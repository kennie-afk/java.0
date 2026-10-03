package com.soko.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.soko.domain.MpesaPayment;
import com.soko.mpesa.MockMpesaGateway;
import com.soko.mpesa.MpesaProperties;
import com.soko.mpesa.StkCallback;
import com.soko.persistence.MpesaPaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private static MpesaProperties properties() {
        return new MpesaProperties(null, null, null, null, null, null, null, null);
    }

    /** In-memory stand-in for the one repository PaymentService needs, no database. */
    private static final class FakeRepo {
        private final List<MpesaPayment> all = new ArrayList<>();

        MpesaPayment save(MpesaPayment payment) {
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
                all.add(payment);
            }
            return payment;
        }

        Optional<MpesaPayment> byCheckout(String checkoutRequestId) {
            return all.stream().filter(p -> checkoutRequestId.equals(p.getCheckoutRequestId())).findFirst();
        }

        Optional<MpesaPayment> firstPending(String purpose, UUID referenceId, String status) {
            return all.stream()
                    .filter(p -> p.getPurpose().name().equals(purpose)
                            && p.getReferenceId().equals(referenceId)
                            && p.getStatus().name().equals(status))
                    .findFirst();
        }
    }

    private static MpesaPaymentRepository adapt(FakeRepo fake) {
        return (MpesaPaymentRepository)
                java.lang.reflect.Proxy.newProxyInstance(
                        MpesaPaymentRepository.class.getClassLoader(),
                        new Class<?>[] {MpesaPaymentRepository.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "save":
                                    return fake.save((MpesaPayment) args[0]);
                                case "findByCheckoutRequestId":
                                    return fake.byCheckout((String) args[0]);
                                case "findFirstByPurposeAndReferenceIdAndStatusOrderByInitiatedAtDesc":
                                    return fake.firstPending(
                                            (String) args[0], (UUID) args[1], (String) args[2]);
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        });
    }

    @Test
    void initiatingTwiceBeforeCompletionReturnsTheSamePendingPushInsteadOfDoublePushing() {
        FakeRepo fake = new FakeRepo();
        PaymentService service = new PaymentService(adapt(fake), new MockMpesaGateway(), properties());
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        MpesaPayment first = service.initiate(tenantId, MpesaPayment.Purpose.ORDER, orderId,
                150000, "254712345678", "SO-ABC123", "test order");
        MpesaPayment second = service.initiate(tenantId, MpesaPayment.Purpose.ORDER, orderId,
                150000, "254712345678", "SO-ABC123", "test order");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(fake.all).hasSize(1);
    }

    @Test
    void insufficientFundsIsRecordedAsFailedImmediately() {
        FakeRepo fake = new FakeRepo();
        PaymentService service = new PaymentService(adapt(fake), new MockMpesaGateway(), properties());

        MpesaPayment payment = service.initiate(UUID.randomUUID(), MpesaPayment.Purpose.ORDER,
                UUID.randomUUID(), 100 /* KSh 1 triggers the mock's failure path */,
                "254712345678", "SO-XYZ", "test order");

        assertThat(payment.getStatus()).isEqualTo(MpesaPayment.Status.FAILED);
        assertThat(payment.getCheckoutRequestId()).isNull();
    }

    @Test
    void aReplayedCallbackForAnAlreadySettledPaymentIsIgnored() {
        FakeRepo fake = new FakeRepo();
        MockMpesaGateway gateway = new MockMpesaGateway();
        PaymentService service = new PaymentService(adapt(fake), gateway, properties());

        MpesaPayment payment = service.initiate(UUID.randomUUID(), MpesaPayment.Purpose.ORDER,
                UUID.randomUUID(), 150000, "254712345678", "SO-ABC123", "test order");
        assertThat(payment.getStatus()).isEqualTo(MpesaPayment.Status.PENDING);

        StkCallback success = callback(payment.getMerchantRequestId(), payment.getCheckoutRequestId(), 0);

        PaymentService.CallbackResult first = service.applyCallback(success, "{}", null);
        assertThat(first.outcome()).isEqualTo(PaymentService.CallbackOutcome.SUCCEEDED);
        assertThat(first.payment().getStatus()).isEqualTo(MpesaPayment.Status.SUCCESS);

        // Safaricom retries until it sees a 200; the second delivery of the
        // exact same callback must not process a second time.
        PaymentService.CallbackResult replay = service.applyCallback(success, "{}", null);
        assertThat(replay.outcome()).isEqualTo(PaymentService.CallbackOutcome.DUPLICATE);
    }

    @Test
    void aCallbackForAnUnknownCheckoutIdIsRejectedWithoutTouchingAnything() {
        FakeRepo fake = new FakeRepo();
        PaymentService service = new PaymentService(adapt(fake), new MockMpesaGateway(), properties());

        StkCallback orphan = callback("ws_CO_ghost1", "ws_CO_ghost2", 0);
        PaymentService.CallbackResult result = service.applyCallback(orphan, "{}", null);

        assertThat(result.outcome()).isEqualTo(PaymentService.CallbackOutcome.UNKNOWN);
        assertThat(fake.all).isEmpty();
    }

    @Test
    void anAmountWithCentsIsChargedRoundedUpAndTheRecordKeepsBothFigures() {
        FakeRepo fake = new FakeRepo();
        PaymentService service = new PaymentService(adapt(fake), new MockMpesaGateway(), properties());

        // KSh 1,234.56 is due; M-Pesa moves whole shillings, so the customer is charged KSh 1,235.
        MpesaPayment payment = service.initiate(UUID.randomUUID(), MpesaPayment.Purpose.ORDER,
                UUID.randomUUID(), 123456, "254712345678", "SO-ROUND", "rounding");

        assertThat(payment.getDueCents()).isEqualTo(123456);
        assertThat(payment.getAmountCents()).isEqualTo(123500);
        assertThat(payment.getAmountCents() - payment.getDueCents()).isEqualTo(44);
    }

    @Test
    void anAmountInWholeShillingsHasNoRounding() {
        FakeRepo fake = new FakeRepo();
        PaymentService service = new PaymentService(adapt(fake), new MockMpesaGateway(), properties());

        MpesaPayment payment = service.initiate(UUID.randomUUID(), MpesaPayment.Purpose.ORDER,
                UUID.randomUUID(), 150000, "254712345678", "SO-WHOLE", "whole");

        assertThat(payment.getAmountCents()).isEqualTo(payment.getDueCents()).isEqualTo(150000);
    }

    private static StkCallback callback(String merchantRequestId, String checkoutRequestId, int resultCode) {
        var detail = new StkCallback.StkCallbackDetail(
                merchantRequestId, checkoutRequestId, resultCode,
                resultCode == 0 ? "The service request is processed successfully." : "Cancelled",
                new StkCallback.CallbackMetadata(List.of(
                        new StkCallback.Item("Amount", new BigDecimal("1500")),
                        new StkCallback.Item("MpesaReceiptNumber", "NLJ7RT61SV"),
                        new StkCallback.Item("PhoneNumber", 254712345678L))));
        return new StkCallback(new StkCallback.Body(detail));
    }
}
