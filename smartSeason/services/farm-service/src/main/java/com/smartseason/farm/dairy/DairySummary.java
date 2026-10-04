package com.smartseason.farm.dairy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the herd produced, what left the farm, and what needs a person's attention. */
public record DairySummary(
        UUID farmId,
        LocalDate from,
        LocalDate to,
        BigDecimal litresRecorded,
        BigDecimal litresDelivered,
        BigDecimal litresRejected,
        BigDecimal rejectionRatePct,
        BigDecimal weightedFatPct,
        BigDecimal deliveredValue,
        int deliveriesWithoutPrice,
        int milkingCows,
        List<DayTotal> days,
        List<CowTotal> cows,
        List<Withdrawal> underWithdrawal,
        List<WithdrawalConflict> milkedDuringWithdrawal,
        List<ExpectedCalving> expectedCalvings) {

    public record DayTotal(LocalDate day, BigDecimal litres) { }

    public record CowTotal(UUID cowId, String tagNo, String name, BigDecimal litres, int daysRecorded,
                           BigDecimal litresPerRecordedDay) { }

    public record Withdrawal(UUID cowId, String tagNo, String medicine, LocalDate endsOn) { }

    public record WithdrawalConflict(UUID cowId, String tagNo, LocalDate day, BigDecimal litres,
                                     String medicine, LocalDate withdrawalEndsOn) { }

    public record ExpectedCalving(UUID cowId, String tagNo, LocalDate expectedOn) { }
}
