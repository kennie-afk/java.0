package com.kenyarealestate.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kenyarealestate.pms.entity.LandlordTaxSettings;
import com.kenyarealestate.pms.entity.PaymentMethod;
import com.kenyarealestate.pms.repository.LandlordTaxSettingsRepository;
import com.kenyarealestate.pms.repository.RentPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RentalIncomeTaxServiceTest {

    private final RentPaymentRepository payments = mock(RentPaymentRepository.class);
    private final LandlordTaxSettingsRepository settings = mock(LandlordTaxSettingsRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC);
    private final UUID landlord = UUID.randomUUID();
    private final UUID propertyA = UUID.randomUUID();
    private final UUID propertyB = UUID.randomUUID();

    private RentalIncomeTaxService service(String defaultRate) {
        return new RentalIncomeTaxService(payments, settings, clock, new BigDecimal(defaultRate),
                new BigDecimal("288000"), new BigDecimal("15000000"));
    }

    private MriLine line(UUID property, String unit, String tenant, String amount, String receipt) {
        return new MriLine(UUID.randomUUID(), "INV-" + unit, unit, property, tenant, PaymentMethod.MPESA_PAYBILL,
                new BigDecimal(amount), LocalDateTime.of(2026, 9, 5, 10, 0), receipt);
    }

    @Test
    void summaryTotalsTheMonthAppliesTheDefaultRateAndSaysItIsNotVerified() {
        when(settings.findById(landlord)).thenReturn(Optional.empty());
        when(payments.confirmedRentBetween(eq(landlord), any(), any())).thenReturn(List.of(
                line(propertyA, "A1", "Jane", "30000", "QWE1"), line(propertyA, "A2", "John", "20000", "QWE2"),
                line(propertyB, "B1", "Mary", "10000", "QWE3")));

        var s = service("7.5").summary(landlord, YearMonth.of(2026, 9));

        assertThat(s.grossRentReceived()).isEqualByComparingTo("60000");
        assertThat(s.ratePercent()).isEqualByComparingTo("7.5");
        assertThat(s.rateIsLandlordOverride()).isFalse();
        assertThat(s.taxEstimate()).isEqualByComparingTo("4500.00");
        assertThat(s.dueDate()).hasToString("2026-10-20");
        assertThat(s.daysUntilDue()).isEqualTo(17);
        assertThat(s.receipts()).isEqualTo(3);
        assertThat(s.byProperty()).hasSize(2);
        assertThat(s.byProperty().get(0).propertyId()).isEqualTo(propertyA);
        assertThat(s.byProperty().get(0).gross()).isEqualByComparingTo("50000");
        assertThat(s.notice()).contains("not a filing").contains("has not been verified");
    }

    @Test
    void aLandlordsOwnRateOverridesTheDefault() {
        when(settings.findById(landlord)).thenReturn(Optional.of(new LandlordTaxSettings(landlord, new BigDecimal("10.00"))));
        when(payments.confirmedRentBetween(eq(landlord), any(), any()))
                .thenReturn(List.of(line(propertyA, "A1", "Jane", "100000", "QWE1")));

        var s = service("7.5").summary(landlord, YearMonth.of(2026, 9));

        assertThat(s.rateIsLandlordOverride()).isTrue();
        assertThat(s.taxEstimate()).isEqualByComparingTo("10000.00");
        assertThat(s.notice()).contains("the one you set");
    }

    @Test
    void theMonthWindowIsTheCalendarMonthHalfOpen() {
        when(settings.findById(landlord)).thenReturn(Optional.empty());
        when(payments.confirmedRentBetween(eq(landlord), any(), any())).thenReturn(List.of());

        service("7.5").summary(landlord, YearMonth.of(2026, 12));

        var from = ArgumentCaptor.forClass(LocalDateTime.class);
        var to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(payments).confirmedRentBetween(eq(landlord), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void csvHasOneRowPerReceiptPlusTotalsAndDefusesFormulaCells() {
        when(settings.findById(landlord)).thenReturn(Optional.empty());
        when(payments.confirmedRentBetween(eq(landlord), any(), any())).thenReturn(List.of(
                line(propertyA, "A1", "=HYPERLINK(\"http://evil\")", "30000", "QWE1"),
                line(propertyA, "A2", "Ann, \"Mwangi\"", "20000", null)));

        String csv = service("7.5").csv(landlord, YearMonth.of(2026, 9));

        assertThat(csv.lines().count()).isEqualTo(1 + 2 + 4);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(csv).contains("\"Ann, \"\"Mwangi\"\"\"");
        assertThat(csv).contains("TOTAL,,,,,,50000.00");
        assertThat(csv).contains("TAX_ESTIMATE,,,,,,3750.00");
        assertThat(csv).contains("DUE_DATE,,,,,,2026-10-20");
    }

    @Test
    void settingARateOutsideZeroToHundredIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service("7.5").setRate(landlord, new BigDecimal("150")))
                .isInstanceOf(com.kenyarealestate.pms.exception.ConflictException.class);
    }
}
