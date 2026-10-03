package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.LandlordTaxSettings;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.repository.LandlordTaxSettingsRepository;
import com.kenyarealestate.pms.repository.RentPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly rental income tax (MRI) readiness for a landlord: what rent was actually received in a
 * month, what the configured rate makes of it, and when it is due.
 *
 * <p>This is a preparation aid, not a filing and not tax advice. It does not talk to KRA (no eRITS
 * interface is assumed anywhere), and the rate is configuration because published sources disagree.
 * Rent counted is CONFIRMED receipts by their paid-at date, per landlord; deposits are not invoiced
 * here and so are not included.
 */
@Service
public class RentalIncomeTaxService {

    private final RentPaymentRepository payments;
    private final LandlordTaxSettingsRepository settings;
    private final Clock clock;
    private final BigDecimal defaultRatePercent;
    private final BigDecimal bandLow;
    private final BigDecimal bandHigh;

    public RentalIncomeTaxService(
            RentPaymentRepository payments,
            LandlordTaxSettingsRepository settings,
            Clock clock,
            @Value("${pms.mri.rate-percent:7.5}") BigDecimal defaultRatePercent,
            @Value("${pms.mri.band-low-annual:288000}") BigDecimal bandLow,
            @Value("${pms.mri.band-high-annual:15000000}") BigDecimal bandHigh) {
        this.payments = payments;
        this.settings = settings;
        this.clock = clock;
        this.defaultRatePercent = defaultRatePercent;
        this.bandLow = bandLow;
        this.bandHigh = bandHigh;
    }

    public record PropertyTotal(UUID propertyId, BigDecimal gross, int receipts) {
    }

    public record Summary(
            String month,
            BigDecimal grossRentReceived,
            BigDecimal ratePercent,
            boolean rateIsLandlordOverride,
            BigDecimal taxEstimate,
            LocalDate dueDate,
            long daysUntilDue,
            boolean annualisedWithinConfiguredBand,
            int receipts,
            List<PropertyTotal> byProperty,
            String notice) {
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID landlordId, YearMonth month) {
        List<MriLine> lines = lines(landlordId, month);
        BigDecimal gross = lines.stream().map(MriLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var override = settings.findById(landlordId).map(LandlordTaxSettings::getMriRatePercent);
        BigDecimal rate = override.orElse(defaultRatePercent);

        Map<UUID, List<MriLine>> grouped = lines.stream()
                .collect(Collectors.groupingBy(MriLine::propertyId, LinkedHashMap::new, Collectors.toList()));
        List<PropertyTotal> byProperty = grouped.entrySet().stream()
                .map(e -> new PropertyTotal(e.getKey(),
                        e.getValue().stream().map(MriLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()))
                .sorted(Comparator.comparing(PropertyTotal::gross).reversed())
                .toList();

        LocalDate today = LocalDate.now(clock);
        return new Summary(
                month.toString(),
                gross,
                rate,
                override.isPresent(),
                MriCalculator.tax(gross, rate),
                MriCalculator.dueDate(month),
                MriCalculator.daysUntilDue(month, today),
                MriCalculator.withinBand(gross, bandLow, bandHigh),
                lines.size(),
                byProperty,
                "Preparation aid, not a filing or tax advice. Confirm the rate and your eligibility with KRA or your "
                        + "accountant: the rate shown is "
                        + (override.isPresent() ? "the one you set" : "the platform default and has not been verified for you")
                        + ".");
    }

    @Transactional(readOnly = true)
    public List<MriLine> lines(UUID landlordId, YearMonth month) {
        return payments.confirmedRentBetween(
                landlordId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    @Transactional
    public BigDecimal setRate(UUID landlordId, BigDecimal ratePercent) {
        if (ratePercent != null && (ratePercent.signum() < 0 || ratePercent.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ConflictException("The rate must be between 0 and 100 percent.");
        }
        LandlordTaxSettings row = settings.findById(landlordId)
                .orElseGet(() -> new LandlordTaxSettings(landlordId, null));
        row.setMriRatePercent(ratePercent);
        settings.save(row);
        return ratePercent == null ? defaultRatePercent : ratePercent;
    }

    /** CSV with one row per confirmed receipt plus a totals row, safe to open in a spreadsheet. */
    @Transactional(readOnly = true)
    public String csv(UUID landlordId, YearMonth month) {
        Summary summary = summary(landlordId, month);
        StringBuilder out = new StringBuilder();
        out.append("month,invoice,unit,property_id,tenant,method,amount_kes,paid_at,mpesa_receipt\n");
        for (MriLine l : lines(landlordId, month)) {
            out.append(cell(month.toString())).append(',')
                    .append(cell(l.invoiceNumber())).append(',')
                    .append(cell(l.unitLabel())).append(',')
                    .append(cell(String.valueOf(l.propertyId()))).append(',')
                    .append(cell(l.tenantName())).append(',')
                    .append(cell(String.valueOf(l.method()))).append(',')
                    .append(l.amount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()).append(',')
                    .append(cell(String.valueOf(l.paidAt()))).append(',')
                    .append(cell(l.mpesaReceipt())).append('\n');
        }
        out.append("TOTAL,,,,,,").append(summary.grossRentReceived().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                .append(",,\n");
        out.append("RATE_PERCENT,,,,,,").append(summary.ratePercent().toPlainString()).append(",,\n");
        out.append("TAX_ESTIMATE,,,,,,").append(summary.taxEstimate().toPlainString()).append(",,\n");
        out.append("DUE_DATE,,,,,,").append(summary.dueDate()).append(",,\n");
        return out.toString();
    }

    /** Quotes a field, and defuses spreadsheet formulas that a tenant name could otherwise smuggle in. */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
