package com.smartseason.payment.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MsisdnTest {

    @ParameterizedTest
    @CsvSource({
            "0712345678,254712345678",
            "0112345678,254112345678",
            "+254712345678,254712345678",
            "254712345678,254712345678",
            "712345678,254712345678",
            "0712 345 678,254712345678",
            "+254 712-345-678,254712345678"
    })
    @DisplayName("Kenyan numbers normalise to the 2547XXXXXXXX form M-Pesa requires")
    void normalisesKenyanNumbers(String input, String expected) {
        assertThat(Msisdn.normalise(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0812345678", "254812345678", "12345", "+447700900000", "abc"})
    @DisplayName("anything that is not a Kenyan mobile number is rejected")
    void rejectsNonKenyanNumbers(String input) {
        assertThatThrownBy(() -> Msisdn.normalise(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a null number is rejected rather than producing a null msisdn")
    void rejectsNull() {
        assertThatThrownBy(() -> Msisdn.normalise(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a fractional amount is refused because M-Pesa moves whole shillings")
    void rejectsFractionalAmount() {
        assertThatThrownBy(() -> new StkPushRequest(
                "254712345678", new BigDecimal("100.50"), "ref", "desc", "https://x/cb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole shillings");
    }

    @Test
    @DisplayName("a zero or negative amount is refused")
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new StkPushRequest(
                "254712345678", BigDecimal.ZERO, "ref", "desc", "https://x/cb"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a whole-shilling amount with a trailing zero decimal is accepted")
    void acceptsWholeShillingsWithScale() {
        assertThat(new StkPushRequest("254712345678", new BigDecimal("100.00"),
                "ref", "desc", "https://x/cb").amount()).isEqualByComparingTo("100");
    }
}
