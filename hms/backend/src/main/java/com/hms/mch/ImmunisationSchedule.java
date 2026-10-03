package com.hms.mch;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The routine childhood immunisation schedule the module checks against.
 *
 * <p>PROVISIONAL. This is the Kenya Expanded Programme on Immunisation routine schedule as it was
 * understood when this was written (BCG and OPV at birth; OPV, pentavalent, pneumococcal and rotavirus
 * from 6 weeks; IPV at 14 weeks; measles-rubella at 9 and 18 months). Schedules change and counties
 * add vaccines, so it is kept as data in one place and the card says so. Confirm it against the current
 * Ministry of Health guidance before this drives anything clinical.
 */
final class ImmunisationSchedule {
    private ImmunisationSchedule() {}

    /** @param dueAge a PostgreSQL interval literal, so the database can do exact calendar arithmetic */
    record Dose(String code, String label, String antigen, String dueAge, String ageLabel, String previous) {
        /** The date this dose falls due for a child born on {@code birth}, matching what PostgreSQL computes for the interval. */
        LocalDate dueOn(LocalDate birth) {
            String[] part = dueAge.split(" ");
            int n = Integer.parseInt(part[0]);
            return switch (part[1]) {
                case "days" -> birth.plusDays(n);
                case "weeks" -> birth.plusWeeks(n);
                case "months" -> birth.plusMonths(n);
                default -> throw new IllegalStateException("Unsupported interval " + dueAge);
            };
        }
    }

    /** A dose this many days past its due date counts as overdue rather than merely due. */
    static final int GRACE_DAYS = 28;

    /** Doses of one series must be at least this far apart; the usual minimum interval is four weeks. */
    static final int MIN_INTERVAL_DAYS = 28;

    static final String NOTE = "Routine schedule as understood at build time. Confirm against current Ministry of Health guidance.";

    static final List<Dose> DOSES = List.of(
            new Dose("BCG", "BCG", "Tuberculosis", "0 days", "Birth", null),
            new Dose("OPV_0", "OPV 0", "Polio (oral)", "0 days", "Birth", null),
            new Dose("OPV_1", "OPV 1", "Polio (oral)", "6 weeks", "6 weeks", null),
            new Dose("PENTA_1", "Pentavalent 1", "DPT, hepatitis B, Hib", "6 weeks", "6 weeks", null),
            new Dose("PCV_1", "Pneumococcal 1", "Pneumococcus", "6 weeks", "6 weeks", null),
            new Dose("ROTA_1", "Rotavirus 1", "Rotavirus", "6 weeks", "6 weeks", null),
            new Dose("OPV_2", "OPV 2", "Polio (oral)", "10 weeks", "10 weeks", "OPV_1"),
            new Dose("PENTA_2", "Pentavalent 2", "DPT, hepatitis B, Hib", "10 weeks", "10 weeks", "PENTA_1"),
            new Dose("PCV_2", "Pneumococcal 2", "Pneumococcus", "10 weeks", "10 weeks", "PCV_1"),
            new Dose("ROTA_2", "Rotavirus 2", "Rotavirus", "10 weeks", "10 weeks", "ROTA_1"),
            new Dose("OPV_3", "OPV 3", "Polio (oral)", "14 weeks", "14 weeks", "OPV_2"),
            new Dose("PENTA_3", "Pentavalent 3", "DPT, hepatitis B, Hib", "14 weeks", "14 weeks", "PENTA_2"),
            new Dose("PCV_3", "Pneumococcal 3", "Pneumococcus", "14 weeks", "14 weeks", "PCV_2"),
            new Dose("IPV", "IPV", "Polio (injected)", "14 weeks", "14 weeks", null),
            new Dose("MR_1", "Measles-rubella 1", "Measles, rubella", "9 months", "9 months", null),
            new Dose("MR_2", "Measles-rubella 2", "Measles, rubella", "18 months", "18 months", "MR_1"));

    private static final Map<String, Dose> BY_CODE = DOSES.stream().collect(java.util.stream.Collectors.toMap(Dose::code, d -> d));

    static Optional<Dose> find(String code) {
        return Optional.ofNullable(code == null ? null : BY_CODE.get(code.trim().toUpperCase()));
    }
}
