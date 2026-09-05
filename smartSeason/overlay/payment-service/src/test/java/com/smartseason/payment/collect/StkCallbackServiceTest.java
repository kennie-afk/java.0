package com.smartseason.payment.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseason.payment.domain.MpesaTransaction;
import com.smartseason.payment.domain.PaymentIntent;
import com.smartseason.payment.domain.ProviderCallback;
import com.smartseason.payment.mpesa.MockMpesaGateway;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.MpesaTransactionRepository;
import com.smartseason.payment.repo.PaymentIntentRepository;
import com.smartseason.payment.repo.ProviderCallbackRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StkCallbackServiceTest {

    private final PaymentIntentRepository intents = mock(PaymentIntentRepository.class);
    private final MpesaTransactionRepository transactions = mock(MpesaTransactionRepository.class);
    private final ProviderCallbackRepository callbacks = mock(ProviderCallbackRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final StkCallbackService service = new StkCallbackService(
            intents, transactions, callbacks, new MockMpesaGateway(), events, new ObjectMapper());

    private final UUID tenant = UUID.randomUUID();
    private final String checkoutId = "ws_CO_191220191020363925";

    private MpesaTransaction transaction;
    private PaymentIntent intent;

    private static final String SUCCESS = """
            {"Body":{"stkCallback":{
              "MerchantRequestID":"29115-34620561-1",
              "CheckoutRequestID":"ws_CO_191220191020363925",
              "ResultCode":0,
              "ResultDesc":"The service request is processed successfully.",
              "CallbackMetadata":{"Item":[
                {"Name":"Amount","Value":1000},
                {"Name":"MpesaReceiptNumber","Value":"NLJ7RT61SV"},
                {"Name":"PhoneNumber","Value":254712345678}
              ]}}}}
            """;

    private static final String CANCELLED = """
            {"Body":{"stkCallback":{
              "CheckoutRequestID":"ws_CO_191220191020363925",
              "ResultCode":1032,
              "ResultDesc":"Request cancelled by user"}}}
            """;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);

        intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setTenantId(tenant);
        intent.setReference("PI-TEST");
        intent.setAmount(new BigDecimal("1000"));
        intent.setCurrency("KES");
        intent.setStatus(PaymentIntent.Status.PENDING);

        transaction = new MpesaTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setTenantId(tenant);
        transaction.setPaymentIntentId(intent.getId());
        transaction.setCheckoutRequestId(checkoutId);
        transaction.setStatus(MpesaTransaction.Status.PENDING);

        when(transactions.findByCheckoutRequestId(checkoutId)).thenReturn(Optional.of(transaction));
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(transactions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(intents.save(any())).thenAnswer(i -> i.getArgument(0));
        when(callbacks.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a successful callback settles the intent and stores the receipt")
    void successSettlesIntent() {
        assertThat(service.handle(SUCCESS, null)).isEqualTo(StkCallbackService.Outcome.PROCESSED);

        assertThat(transaction.getStatus()).isEqualTo(MpesaTransaction.Status.SUCCESS);
        assertThat(transaction.getMpesaReceiptNumber()).isEqualTo("NLJ7RT61SV");
        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.Status.SUCCEEDED);
        assertThat(intent.getProviderRef()).isEqualTo("NLJ7RT61SV");
        assertThat(intent.getCompletedAt()).isNotNull();

        verify(events).publish(eq("money"), eq("PaymentSucceeded"), any(), any());
    }

    @Test
    @DisplayName("a user cancellation fails the intent and records the reason")
    void cancellationFailsIntent() {
        assertThat(service.handle(CANCELLED, null)).isEqualTo(StkCallbackService.Outcome.PROCESSED);

        assertThat(transaction.getStatus()).isEqualTo(MpesaTransaction.Status.FAILED);
        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.Status.FAILED);
        assertThat(intent.getFailureReason()).isEqualTo("Request cancelled by user");

        verify(events).publish(eq("money"), eq("PaymentFailed"), any(), any());
    }

    @Test
    @DisplayName("a replayed callback is ignored, so a payment is never counted twice")
    void duplicateCallbackIsIgnored() {
        service.handle(SUCCESS, null);
        intent.setStatus(PaymentIntent.Status.SUCCEEDED);

        assertThat(service.handle(SUCCESS, null)).isEqualTo(StkCallbackService.Outcome.DUPLICATE);

        verify(events, org.mockito.Mockito.times(1))
                .publish(eq("money"), eq("PaymentSucceeded"), any(), any());
    }

    @Test
    @DisplayName("a callback for an unknown checkout id is recorded but changes nothing")
    void unknownTransactionIsRecorded() {
        when(transactions.findByCheckoutRequestId(checkoutId)).thenReturn(Optional.empty());

        assertThat(service.handle(SUCCESS, null))
                .isEqualTo(StkCallbackService.Outcome.UNKNOWN_TRANSACTION);

        verify(intents, never()).save(any());
        verify(events, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unparseable body is recorded as invalid rather than throwing")
    void malformedBodyIsRecorded() {
        assertThat(service.handle("not json at all", null))
                .isEqualTo(StkCallbackService.Outcome.MALFORMED);

        ArgumentCaptor<ProviderCallback> captor = ArgumentCaptor.forClass(ProviderCallback.class);
        verify(callbacks).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProviderCallback.Status.INVALID);
    }

    @Test
    @DisplayName("every callback is persisted for reconciliation, whatever its outcome")
    void everyCallbackIsPersisted() {
        service.handle(SUCCESS, null);
        verify(callbacks).save(any(ProviderCallback.class));
    }
}
