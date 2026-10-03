package com.hms.platform.rbac;

import java.util.List;

/**
 * The vocabulary of permissions. Names live in code because controllers are written against them;
 * which role holds which permission is data, per organisation (the roles table), and DefaultRoles
 * only seeds a new organisation.
 */
public final class Permissions {
    private Permissions() {}

    public static final String PATIENTS_READ = "patients:read";
    public static final String PATIENTS_WRITE = "patients:write";
    public static final String PATIENTS_MERGE = "patients:merge";
    /** Open a restricted record (with a reason that is audited). */
    public static final String PATIENTS_RESTRICTED = "patients:restricted";
    public static final String CLINICAL_READ = "clinical:read";
    public static final String CLINICAL_WRITE = "clinical:write";
    public static final String ORDERS_WRITE = "orders:write";
    public static final String PHARMACY_READ = "pharmacy:read";
    public static final String PHARMACY_DISPENSE = "pharmacy:dispense";
    public static final String PHARMACY_STOCK = "pharmacy:stock";
    public static final String LAB_READ = "lab:read";
    public static final String LAB_ENTER = "lab:enter";
    public static final String LAB_VALIDATE = "lab:validate";
    public static final String LAB_MANAGE = "lab:manage";
    public static final String BILLING_READ = "billing:read";
    public static final String BILLING_POST = "billing:post";
    public static final String BILLING_REFUND = "billing:refund";
    public static final String BILLING_MANAGE = "billing:manage";
    public static final String CLAIMS_READ = "claims:read";
    public static final String CLAIMS_SUBMIT = "claims:submit";
    public static final String REPORTS_READ = "reports:read";
    public static final String STAFF_READ = "staff:read";
    public static final String STAFF_MANAGE = "staff:manage";
    public static final String ROLES_MANAGE = "roles:manage";
    public static final String FACILITIES_MANAGE = "facilities:manage";
    public static final String AUDIT_READ = "audit:read";
    public static final String SCHEDULING_READ = "scheduling:read";
    public static final String SCHEDULING_WRITE = "scheduling:write";
    public static final String INPATIENT_READ = "inpatient:read";
    public static final String INPATIENT_WRITE = "inpatient:write";
    public static final String MCH_READ = "mch:read";
    public static final String MCH_WRITE = "mch:write";
    public static final String IMAGING_READ = "imaging:read";
    public static final String IMAGING_PERFORM = "imaging:perform";
    public static final String IMAGING_SIGN = "imaging:sign";
    public static final String IMAGING_MANAGE = "imaging:manage";
    public static final String PROGRAMMES_READ = "programmes:read";
    public static final String PROGRAMMES_WRITE = "programmes:write";
    public static final String PROGRAMMES_HIV = "programmes:hiv";
    public static final String REPORTS_MANAGE = "reports:manage";
    public static final String FHIR_READ = "fhir:read";

    public static final List<String> ALL = List.of(
            PATIENTS_READ, PATIENTS_WRITE, PATIENTS_MERGE, PATIENTS_RESTRICTED, CLINICAL_READ, CLINICAL_WRITE,
            ORDERS_WRITE, PHARMACY_READ, PHARMACY_DISPENSE, PHARMACY_STOCK, LAB_READ, LAB_ENTER, LAB_VALIDATE, LAB_MANAGE,
            BILLING_READ, BILLING_POST, BILLING_REFUND, BILLING_MANAGE, CLAIMS_READ, CLAIMS_SUBMIT, REPORTS_READ, STAFF_READ,
            STAFF_MANAGE, ROLES_MANAGE, FACILITIES_MANAGE, AUDIT_READ, SCHEDULING_READ, SCHEDULING_WRITE, INPATIENT_READ,
            INPATIENT_WRITE, MCH_READ, MCH_WRITE, IMAGING_READ, IMAGING_PERFORM, IMAGING_SIGN, IMAGING_MANAGE, PROGRAMMES_READ, PROGRAMMES_WRITE, PROGRAMMES_HIV, REPORTS_MANAGE, FHIR_READ);
}
