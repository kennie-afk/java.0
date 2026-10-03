package com.smartseason.payment.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StkCallbackControllerTest {

    private final StkCallbackService service = mock(StkCallbackService.class);
    private final StkCallbackController controller = new StkCallbackController(service);

    @Test
    void aProcessedCallbackIsAcknowledgedInTheShapeDarajaExpects() {
        when(service.handle(any(), any())).thenReturn(StkCallbackService.Outcome.PROCESSED);

        var response = controller.callback("{}", null, "s3cret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("ResultCode", 0);
    }

    @Test
    void aReplayAndAnUnknownCheckoutAreAlsoAcknowledgedSoSafaricomStopsRetrying() {
        when(service.handle(any(), any())).thenReturn(StkCallbackService.Outcome.DUPLICATE)
                .thenReturn(StkCallbackService.Outcome.UNKNOWN_TRANSACTION);

        assertThat(controller.callback("{}", null, "x").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.callback("{}", null, "x").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aWrongSecretIsRejectedWith401() {
        when(service.handle(any(), any())).thenReturn(StkCallbackService.Outcome.INVALID_SIGNATURE);

        assertThat(controller.callback("{}", null, "wrong").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anUnreadableBodyIsA400() {
        when(service.handle(any(), any())).thenReturn(StkCallbackService.Outcome.MALFORMED);

        assertThat(controller.callback("not json", null, "x").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theUrlSecretWinsOverTheHeaderAndEitherIsAccepted() {
        when(service.handle(any(), any())).thenReturn(StkCallbackService.Outcome.PROCESSED);

        controller.callback("{}", "from-header", "from-url");
        verify(service).handle(eq("{}"), eq("from-url"));

        controller.callback("{}", "only-header", null);
        verify(service).handle(eq("{}"), eq("only-header"));
    }
}
