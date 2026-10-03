package com.smartseason.payment.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DarajaMpesaGatewayTest {

    private static MpesaProperties props(String secret) {
        return new MpesaProperties("live", null, "key", "secret", "174379", "passkey", null, null, secret);
    }

    @Test
    void liveModeRefusesToStartWithoutAUsableCallbackSecret() {
        assertThatThrownBy(() -> new DarajaMpesaGateway(props(null), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DarajaMpesaGateway(props(""), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DarajaMpesaGateway(props("short"), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyTheExactSecretVerifies() {
        DarajaMpesaGateway gateway = new DarajaMpesaGateway(props("0123456789abcdef"), new ObjectMapper());

        assertThat(gateway.verifyCallbackSignature("{}", "0123456789abcdef")).isTrue();
        assertThat(gateway.verifyCallbackSignature("{}", "0123456789abcdeX")).isFalse();
        assertThat(gateway.verifyCallbackSignature("{}", "")).isFalse();
        assertThat(gateway.verifyCallbackSignature("{}", null)).isFalse();
    }

    @Test
    void theRegisteredCallbackUrlCarriesTheSecret() {
        DarajaMpesaGateway gateway = new DarajaMpesaGateway(props("0123456789abcdef"), new ObjectMapper());

        assertThat(gateway.withSecret("https://x.test/api/payment/v1/mpesa/stk-callback"))
                .isEqualTo("https://x.test/api/payment/v1/mpesa/stk-callback?secret=0123456789abcdef");
        assertThat(gateway.withSecret("https://x.test/cb?a=1")).endsWith("&secret=0123456789abcdef");
    }
}
