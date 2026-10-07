package com.smartseason.payment.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.payment.domain.PaymentIntent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CollectionControllerTest {

    private final MpesaCollectionService service = mock(MpesaCollectionService.class);
    private final CollectionController controller = new CollectionController(service);

    private static CollectionRequest request() {
        return new CollectionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "0712345678",
                new BigDecimal("1500.00"), "KES", PaymentIntent.Purpose.ORDER, false, "key-1", null);
    }

    private static PaymentIntent intent(PaymentIntent.Status status) {
        PaymentIntent intent = new PaymentIntent();
        intent.setReference("PI-ABC123");
        intent.setStatus(status);
        return intent;
    }

    @Test
    void aPushThatWasSentIsAcceptedNotSettled_theOutcomeComesByCallback() {
        PaymentIntent pending = intent(PaymentIntent.Status.PENDING);
        pending.setProviderRef("ws_CO_123");
        CollectionRequest request = request();
        when(service.collect(request)).thenReturn(pending);

        var response = controller.collect(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("status", PaymentIntent.Status.PENDING)
                .containsEntry("checkoutRequestId", "ws_CO_123").containsEntry("reference", "PI-ABC123");
        verify(service).collect(request);
    }

    @Test
    void aPushSafaricomRefusedIsA422_andTheReasonIsReturned() {
        PaymentIntent failed = intent(PaymentIntent.Status.FAILED);
        failed.setFailureReason("Invalid Access Token");
        when(service.collect(org.mockito.ArgumentMatchers.any())).thenReturn(failed);

        var response = controller.collect(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("failureReason", "Invalid Access Token");
    }

    @Test
    void aRequestAlreadySeenReturnsTheStoredIntentWithoutAnotherPrompt() {
        when(service.collect(org.mockito.ArgumentMatchers.any())).thenReturn(intent(PaymentIntent.Status.SUCCEEDED));

        var response = controller.collect(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
