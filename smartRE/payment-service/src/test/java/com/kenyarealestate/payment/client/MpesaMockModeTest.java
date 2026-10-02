package com.kenyarealestate.payment.client;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Mock mode must settle locally, never call Safaricom, and be unmistakable in every identifier. */
class MpesaMockModeTest {

    private MpesaClient mockClient() {
        MpesaClient client = new MpesaClient();
        ReflectionTestUtils.setField(client, "mode", "mock");
        // An unreachable auth URL proves nothing is called: a real call would fail.
        ReflectionTestUtils.setField(client, "authUrl", "http://127.0.0.1:1/never");
        return client;
    }

    @Test
    void acceptsAPushAndMarksEveryIdentifierAsMock() {
        var result = mockClient().initiateSTKPush("0712345678", "1500", "SMARTRE-ABCD1234", "rent");
        assertThat(result.success()).isTrue();
        assertThat(result.checkoutRequestId()).contains("MOCK");
        assertThat(result.merchantRequestId()).startsWith("MOCK-");
    }

    @Test
    void settlesAnAcceptedPushOnTheNextStatusQuery() {
        var client = mockClient();
        var push = client.initiateSTKPush("0712345678", "1500", "ref", "rent");
        var query = client.queryStkStatus(push.checkoutRequestId());
        assertThat(query.resolved()).isTrue();
        assertThat(query.succeeded()).isTrue();
        assertThat(query.receipt()).startsWith("MOCK");
        // Asking again gives the same receipt: a payment has one.
        assertThat(client.queryStkStatus(push.checkoutRequestId()).receipt()).isEqualTo(query.receipt());
    }

    @Test
    void aNumberEndingInZeroZeroPlaysACustomerWhoDeclines() {
        var client = mockClient();
        var push = client.initiateSTKPush("0712345600", "1500", "ref", "rent");
        assertThat(push.success()).isTrue();
        var query = client.queryStkStatus(push.checkoutRequestId());
        assertThat(query.resolved()).isTrue();
        assertThat(query.succeeded()).isFalse();
        assertThat(query.resultDesc()).containsIgnoringCase("cancelled");
    }
}
