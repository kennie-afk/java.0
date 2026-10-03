package com.hms.reporting;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The measures a report definition can use. Each is a fixed query fragment, written here and nowhere else, so a
 * definition (which is data an administrator can edit) can never inject SQL: it only chooses among these.
 */
final class Measures {
    private Measures() {}

    enum Filter { NONE, ICD_PREFIX, PROGRAMME, VACCINE }

    /**
     * @param from       the FROM/JOIN clause; aliases x (the fact) and p (patients) are always present, ip for imaging
     * @param timeExpr   the event time column, compared against the facility-local period
     * @param timeIsDate true for a date column, false for a timestamp
     * @param facilityExpr the facility column
     * @param where      extra fixed conditions, or empty
     * @param countExpr  what is counted
     */
    record Measure(String code, String label, String description, String from, String timeExpr, boolean timeIsDate, String facilityExpr, String where, String countExpr,
                   Filter filter, boolean filterRequired, List<String> disaggregations) {}

    static final List<String> SEX_AGE = List.of("NONE", "SEX", "AGE_BAND");

    private static final String PATIENT_X = " JOIN patients p ON p.org_id = x.org_id AND p.id = x.patient_id";

    static final List<Measure> ALL = List.of(
            new Measure("OPD_VISITS", "Outpatient and emergency visits", "Encounters of type OPD or ED that started in the period.",
                    "encounters x" + PATIENT_X, "x.started_at", false, "x.facility_id", "x.encounter_type IN ('OPD', 'ED')", "count(*)", Filter.NONE, false, SEX_AGE),
            new Measure("DIAGNOSED_CASES", "Cases with a diagnosis", "Encounters in the period with a diagnosis whose ICD-11 code starts with the filter (ruled-out diagnoses excluded). One encounter counts once.",
                    "diagnoses d JOIN encounters x ON x.org_id = d.org_id AND x.id = d.encounter_id" + PATIENT_X, "x.started_at", false, "x.facility_id", "d.certainty <> 'RULED_OUT'",
                    "count(DISTINCT x.id)", Filter.ICD_PREFIX, true, SEX_AGE),
            new Measure("ADMISSIONS", "Admissions", "Inpatient admissions in the period.", "admissions x" + PATIENT_X, "x.admitted_at", false, "x.facility_id", "", "count(*)", Filter.NONE, false, SEX_AGE),
            new Measure("DISCHARGES", "Discharges", "Inpatient discharges (including deaths recorded at discharge) in the period.", "admissions x" + PATIENT_X, "x.discharged_at", false, "x.facility_id",
                    "x.status = 'DISCHARGED'", "count(*)", Filter.NONE, false, SEX_AGE),
            new Measure("LAB_TESTS_VALIDATED", "Laboratory tests validated", "Tests whose result was validated in the period.",
                    "lab_order_items x JOIN lab_orders o ON o.org_id = x.org_id AND o.id = x.order_id JOIN patients p ON p.org_id = o.org_id AND p.id = o.patient_id", "x.validated_at", false,
                    "o.facility_id", "x.status = 'VALIDATED'", "count(*)", Filter.NONE, false, SEX_AGE),
            new Measure("IMAGING_STUDIES_SIGNED", "Imaging studies signed", "Imaging reports signed in the period.",
                    "imaging_orders x JOIN imaging_procedures ip ON ip.org_id = x.org_id AND ip.id = x.procedure_id" + PATIENT_X, "x.signed_at", false, "x.facility_id", "x.status = 'SIGNED'", "count(*)",
                    Filter.NONE, false, List.of("NONE", "SEX", "AGE_BAND", "MODALITY")),
            new Measure("PROGRAMME_ENROLMENTS", "Programme enrolments", "New enrolments in the period into the programme named by the filter.", "programme_enrolments x" + PATIENT_X, "x.enrolled_on", true,
                    "x.facility_id", "x.programme = ?", "count(*)", Filter.PROGRAMME, true, SEX_AGE),
            new Measure("IMMUNISATION_DOSES", "Immunisation doses given", "Doses given in the period, optionally only the vaccine named by the filter.", "immunisations x" + PATIENT_X, "x.given_on", true,
                    "x.facility_id", "", "count(*)", Filter.VACCINE, false, SEX_AGE));

    static final Map<String, Measure> BY_CODE = ALL.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Measure::code, m -> m));

    static Optional<Measure> find(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    static final Map<String, List<String>> OPTIONS = Map.of(
            "NONE", List.of("Total"),
            "SEX", List.of("MALE", "FEMALE", "INTERSEX", "UNKNOWN"),
            "AGE_BAND", List.of("0-4", "5-17", "18-59", "60+"),
            "MODALITY", List.of("XR", "US", "CT", "MR", "MG", "FL", "NM", "OTHER"));

    /** The SQL expression that yields the category for a disaggregation, given the event time expression. */
    static String categoryExpr(String disaggregation, String timeExpr) {
        return switch (disaggregation) {
            case "SEX" -> "p.sex";
            case "AGE_BAND" -> "CASE WHEN age(" + timeExpr + ", p.birth_date) < interval '5 years' THEN '0-4' WHEN age(" + timeExpr + ", p.birth_date) < interval '18 years' THEN '5-17' "
                    + "WHEN age(" + timeExpr + ", p.birth_date) < interval '60 years' THEN '18-59' ELSE '60+' END";
            case "MODALITY" -> "ip.modality";
            default -> "'Total'";
        };
    }
}
