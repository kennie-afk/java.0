-- Phone + OTP is the storefront's own identity mechanism, separate from the
-- email+password accounts staff and owners use. A 6-digit code has far less
-- entropy than a real credential (1,000,000 possibilities, not 2^128), so
-- what makes it safe is not the code itself but its short lifetime, a hard
-- cap on verification attempts, and the fact that only one code is ever live
-- per phone number at a time -- all enforced in OtpService, not here.

CREATE TABLE otp_codes (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    phone        varchar(30) NOT NULL,
    code_hash    varchar(200) NOT NULL,
    expires_at   timestamptz NOT NULL,
    attempts     integer NOT NULL DEFAULT 0,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_codes_lookup ON otp_codes (tenant_id, phone);
