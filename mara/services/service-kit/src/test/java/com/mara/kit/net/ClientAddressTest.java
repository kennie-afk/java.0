package com.mara.kit.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressTest {

    private static MockHttpServletRequest request(String peer, String xff) {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr(peer);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    void byDefaultTheSocketPeerIsUsedWhateverTheHeaderSays() {
        assertThat(ClientAddress.of(request("10.0.0.7", "1.2.3.4"), false)).isEqualTo("10.0.0.7");
    }

    @Test
    void whenTrustedTheLastEntryIsUsedBecauseOnlyTheIngressCanAppendIt() {
        // a client may send any prefix; the ingress appends the real address on the right
        assertThat(ClientAddress.of(request("10.0.0.7", "6.6.6.6, 203.0.113.9"), true)).isEqualTo("203.0.113.9");
        assertThat(ClientAddress.of(request("10.0.0.7", "203.0.113.9"), true)).isEqualTo("203.0.113.9");
        assertThat(ClientAddress.of(request("10.0.0.7", "2001:db8::1"), true)).isEqualTo("2001:db8::1");
    }

    @Test
    void aMissingOrNonAddressEntryFallsBackToThePeerNeverToAnArbitraryKey() {
        assertThat(ClientAddress.of(request("10.0.0.7", null), true)).isEqualTo("10.0.0.7");
        assertThat(ClientAddress.of(request("10.0.0.7", ""), true)).isEqualTo("10.0.0.7");
        assertThat(ClientAddress.of(request("10.0.0.7", "1.2.3.4, <script>"), true)).isEqualTo("10.0.0.7");
        assertThat(ClientAddress.of(request("10.0.0.7", "x".repeat(500)), true)).isEqualTo("10.0.0.7");
        assertThat(ClientAddress.of(request("10.0.0.7", "1.2.3.4, "), true)).as("a blank last entry carries no appended address").isEqualTo("10.0.0.7");
    }
}
