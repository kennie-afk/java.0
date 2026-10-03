package com.hms.platform.rbac;

import static com.hms.platform.rbac.Permissions.*;

import java.util.List;

/** Starting roles copied into every new organisation. After that, the organisation's own rows rule. */
public final class DefaultRoles {
    private DefaultRoles() {}

    public record Template(String key, String label, String description, List<String> permissions) {}

    public static final String ADMIN = "ORG_ADMIN";

    public static final List<Template> ALL_TEMPLATES = List.of(
            new Template(ADMIN, "Organisation administrator", "Everything, always", Permissions.ALL),
            new Template("DOCTOR", "Doctor", "Sees patients, orders and prescribes",
                    List.of(PATIENTS_READ, PATIENTS_WRITE, CLINICAL_READ, CLINICAL_WRITE, ORDERS_WRITE, PHARMACY_READ,
                            LAB_READ, BILLING_READ, CLAIMS_READ, SCHEDULING_READ, SCHEDULING_WRITE, INPATIENT_READ, INPATIENT_WRITE, MCH_READ, MCH_WRITE, IMAGING_READ, PROGRAMMES_READ, PROGRAMMES_WRITE, PROGRAMMES_HIV, PORTAL_RELEASE)),
            new Template("CLINICAL_OFFICER", "Clinical officer", "Outpatient care under protocol",
                    List.of(PATIENTS_READ, PATIENTS_WRITE, CLINICAL_READ, CLINICAL_WRITE, ORDERS_WRITE, PHARMACY_READ,
                            LAB_READ, SCHEDULING_READ, SCHEDULING_WRITE, INPATIENT_READ, MCH_READ, MCH_WRITE, IMAGING_READ, PROGRAMMES_READ, PROGRAMMES_WRITE, PROGRAMMES_HIV, PORTAL_RELEASE)),
            new Template("NURSE", "Nurse", "Triage, nursing care and observations",
                    List.of(PATIENTS_READ, PATIENTS_WRITE, CLINICAL_READ, CLINICAL_WRITE, PHARMACY_READ, LAB_READ,
                            SCHEDULING_READ, SCHEDULING_WRITE, INPATIENT_READ, INPATIENT_WRITE, MCH_READ, MCH_WRITE, IMAGING_READ, PROGRAMMES_READ, PROGRAMMES_WRITE, PROGRAMMES_HIV)),
            new Template("PHARMACIST", "Pharmacist", "Dispenses and controls stock",
                    List.of(PATIENTS_READ, CLINICAL_READ, PHARMACY_READ, PHARMACY_DISPENSE, PHARMACY_STOCK)),
            new Template("LAB_TECHNOLOGIST", "Laboratory technologist", "Enters and validates results",
                    List.of(PATIENTS_READ, LAB_READ, LAB_ENTER, LAB_VALIDATE, LAB_MANAGE)),
            new Template("RECORDS_OFFICER", "Records officer", "Registers patients and keeps the index clean",
                    List.of(PATIENTS_READ, PATIENTS_WRITE, PATIENTS_MERGE, SCHEDULING_READ, SCHEDULING_WRITE, PORTAL_MANAGE)),
            new Template("CASHIER", "Cashier", "Takes payment and issues receipts",
                    List.of(PATIENTS_READ, BILLING_READ, BILLING_POST)),
            new Template("CLAIMS_OFFICER", "Claims officer", "Prepares and submits insurance and SHA claims",
                    List.of(PATIENTS_READ, CLINICAL_READ, BILLING_READ, CLAIMS_READ, CLAIMS_SUBMIT)),
            new Template("AUDITOR", "Auditor", "Read-only access to records and the audit trail",
                    List.of(PATIENTS_READ, CLINICAL_READ, BILLING_READ, CLAIMS_READ, REPORTS_READ, AUDIT_READ, MCH_READ, IMAGING_READ, PROGRAMMES_READ)),
            new Template("RADIOGRAPHER", "Radiographer", "Performs studies and drafts reports",
                    List.of(PATIENTS_READ, CLINICAL_READ, IMAGING_READ, IMAGING_PERFORM)),
            new Template("RADIOLOGIST", "Radiologist", "Reports and signs imaging studies",
                    List.of(PATIENTS_READ, CLINICAL_READ, IMAGING_READ, IMAGING_PERFORM, IMAGING_SIGN, IMAGING_MANAGE)),
            new Template("FHIR_CLIENT", "Integration (FHIR read)", "Reads patient records through the FHIR interface; cannot change anything",
                    List.of(FHIR_READ, PATIENTS_READ, CLINICAL_READ, LAB_READ)));
}
