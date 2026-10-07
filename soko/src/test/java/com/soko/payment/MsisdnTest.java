package com.soko.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soko.platform.Errors;
import org.junit.jupiter.api.Test;

class MsisdnTest {

    @Test
    void everyCommonWayOfWritingASafaricomNumberBecomesTheDarajaForm() {
        assertThat(Msisdn.normalise("0712 345 678")).isEqualTo("254712345678");
        assertThat(Msisdn.normalise("+254712345678")).isEqualTo("254712345678");
        assertThat(Msisdn.normalise("254112345678")).isEqualTo("254112345678");
        assertThat(Msisdn.normalise("0712-345-678")).isEqualTo("254712345678");
    }

    @Test
    void anythingElseIsRefusedBeforeItReachesM_Pesa() {
        for (String bad : new String[] {"", null, "12345", "0212345678", "07123456789", "abc"}) {
            assertThatThrownBy(() -> Msisdn.normalise(bad)).isInstanceOf(Errors.BadRequest.class);
        }
    }
}
