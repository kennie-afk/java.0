-- Foundation: tenancy, staff and roles, audit chain, patient registry.
-- Runs as the schema OWNER. The API connects as ${appUser}, which owns nothing and cannot bypass
-- row-level security, so every tenant table below is filtered by app.org_id in the database itself.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${appUser}') THEN
    CREATE ROLE ${appUser} LOGIN PASSWORD '${appPassword}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO ${appUser};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${appUser};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${appUser};

-- The org of the current request: set by the API per transaction (SET LOCAL semantics).
CREATE FUNCTION current_org() RETURNS uuid LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.org_id', true), '')::uuid $$;

-- ---------------------------------------------------------------------------------------------
-- Organisations and facilities
-- ---------------------------------------------------------------------------------------------
CREATE TABLE organisations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  slug citext NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE facilities (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  -- Master Health Facility List code (the MFL code), where the facility has one.
  mfl_code text,
  -- KEPH level 1 (community) to 6 (national referral).
  keph_level smallint CHECK (keph_level BETWEEN 1 AND 6),
  ownership text CHECK (ownership IN ('PUBLIC', 'PRIVATE', 'FAITH_BASED', 'NGO')),
  county text,
  sub_county text,
  timezone text NOT NULL DEFAULT 'Africa/Nairobi',
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, mfl_code)
);
CREATE INDEX facilities_org ON facilities (org_id);

-- ---------------------------------------------------------------------------------------------
-- Staff accounts, roles (data per organisation) and facility assignments
-- ---------------------------------------------------------------------------------------------
CREATE TABLE practitioners (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  email citext NOT NULL UNIQUE,
  password_hash text NOT NULL,
  full_name text NOT NULL CHECK (length(full_name) BETWEEN 2 AND 200),
  -- What the person is professionally; separate from what the system lets them do (roles).
  cadre text NOT NULL DEFAULT 'ADMINISTRATIVE' CHECK (cadre IN
    ('DOCTOR', 'CLINICAL_OFFICER', 'NURSE', 'MIDWIFE', 'PHARMACIST', 'PHARMACEUTICAL_TECHNOLOGIST',
     'LAB_TECHNOLOGIST', 'RADIOGRAPHER', 'NUTRITIONIST', 'PHYSIOTHERAPIST', 'DENTIST', 'COMMUNITY_HEALTH',
     'RECORDS_OFFICER', 'ACCOUNTANT', 'ADMINISTRATIVE')),
  licence_body text CHECK (licence_body IN ('KMPDC', 'NCK', 'PPB', 'KMLTTB', 'COC', 'OTHER')),
  licence_no text,
  phone text,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  failed_logins int NOT NULL DEFAULT 0,
  locked_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id)
);
CREATE INDEX practitioners_org ON practitioners (org_id);

CREATE TABLE roles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  role_key text NOT NULL CHECK (role_key ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  label text NOT NULL CHECK (length(label) BETWEEN 2 AND 80),
  description text,
  is_system boolean NOT NULL DEFAULT false,
  permissions jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, role_key)
);

CREATE TABLE practitioner_roles (
  org_id uuid NOT NULL,
  practitioner_id uuid NOT NULL,
  role_key text NOT NULL,
  PRIMARY KEY (practitioner_id, role_key),
  FOREIGN KEY (org_id, practitioner_id) REFERENCES practitioners (org_id, id) ON DELETE CASCADE,
  FOREIGN KEY (org_id, role_key) REFERENCES roles (org_id, role_key)
);

CREATE TABLE practitioner_facilities (
  org_id uuid NOT NULL,
  practitioner_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  PRIMARY KEY (practitioner_id, facility_id),
  FOREIGN KEY (org_id, practitioner_id) REFERENCES practitioners (org_id, id) ON DELETE CASCADE,
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------------------------
-- Audit: append-only, hash-chained per (organisation, facility). Verifiable after the fact.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE audit_chain (
  org_id uuid NOT NULL REFERENCES organisations (id),
  chain_key text NOT NULL,
  last_seq bigint NOT NULL DEFAULT 0,
  last_hash bytea NOT NULL DEFAULT '\x00',
  PRIMARY KEY (org_id, chain_key)
);

CREATE TABLE audit_event (
  id bigserial PRIMARY KEY,
  org_id uuid NOT NULL REFERENCES organisations (id),
  chain_key text NOT NULL,
  seq bigint NOT NULL,
  at timestamptz NOT NULL DEFAULT now(),
  actor_id uuid,
  facility_id uuid,
  action text NOT NULL,
  entity_type text NOT NULL,
  entity_id text,
  -- A privileged "break-glass" read of a restricted record carries its reason here.
  reason text,
  detail jsonb NOT NULL DEFAULT '{}'::jsonb,
  prev_hash bytea NOT NULL,
  hash bytea NOT NULL,
  UNIQUE (org_id, chain_key, seq)
);
CREATE INDEX audit_event_entity ON audit_event (org_id, entity_type, entity_id, at DESC);
CREATE INDEX audit_event_actor ON audit_event (org_id, actor_id, at DESC);

CREATE FUNCTION forbid_change() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN RAISE EXCEPTION '% is append-only', TG_TABLE_NAME; END $$;
CREATE TRIGGER audit_event_append_only BEFORE UPDATE OR DELETE ON audit_event
  FOR EACH ROW EXECUTE FUNCTION forbid_change();

-- ---------------------------------------------------------------------------------------------
-- Master patient index
-- ---------------------------------------------------------------------------------------------
CREATE TABLE patients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  registered_facility_id uuid NOT NULL,
  given_name text NOT NULL CHECK (length(given_name) BETWEEN 1 AND 100),
  other_names text,
  family_name text NOT NULL CHECK (length(family_name) BETWEEN 1 AND 100),
  sex text NOT NULL CHECK (sex IN ('MALE', 'FEMALE', 'INTERSEX', 'UNKNOWN')),
  birth_date date NOT NULL CHECK (birth_date <= current_date),
  birth_date_estimated boolean NOT NULL DEFAULT false,
  phone text,
  email text,
  county text,
  sub_county text,
  address_line text,
  marital_status text CHECK (marital_status IN ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'SEPARATED', 'UNKNOWN')),
  occupation text,
  nationality text NOT NULL DEFAULT 'KE',
  deceased_at timestamptz,
  active boolean NOT NULL DEFAULT true,
  -- A restricted record (staff, VIPs, sensitive programmes) needs a reason to open.
  restricted boolean NOT NULL DEFAULT false,
  merged_into uuid REFERENCES patients (id),
  version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  search_text text GENERATED ALWAYS AS (
    lower(given_name || ' ' || coalesce(other_names, '') || ' ' || family_name)
  ) STORED,
  UNIQUE (org_id, id),
  FOREIGN KEY (org_id, registered_facility_id) REFERENCES facilities (org_id, id)
);
CREATE INDEX patients_search_trgm ON patients USING gin (search_text gin_trgm_ops);
CREATE INDEX patients_org_name ON patients (org_id, search_text, id);
CREATE INDEX patients_org_birth ON patients (org_id, birth_date);
CREATE INDEX patients_org_phone ON patients (org_id, phone) WHERE phone IS NOT NULL;

CREATE TABLE patient_identifiers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  system text NOT NULL CHECK (system IN
    ('NATIONAL_ID', 'PASSPORT', 'ALIEN_ID', 'BIRTH_CERTIFICATE', 'SHA_NUMBER', 'NHIF_LEGACY', 'UPI', 'MRN')),
  value text NOT NULL CHECK (length(value) BETWEEN 1 AND 60),
  facility_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  -- One person per national identifier within an organisation; a duplicate is a prompt to merge.
  UNIQUE (org_id, system, value),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id) ON DELETE CASCADE
);
CREATE INDEX patient_identifiers_patient ON patient_identifiers (patient_id);

CREATE TABLE patient_contacts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  relationship text NOT NULL CHECK (length(relationship) BETWEEN 2 AND 40),
  full_name text NOT NULL,
  phone text,
  is_next_of_kin boolean NOT NULL DEFAULT true,
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id) ON DELETE CASCADE
);

-- Medical record numbers are issued per facility from an atomic counter.
CREATE TABLE facility_counters (
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  name text NOT NULL,
  next_value bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (facility_id, name),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------------------------
-- Row-level security: a request sees only its own organisation, enforced here and nowhere else.
-- ---------------------------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['facilities', 'practitioners', 'roles', 'practitioner_roles', 'practitioner_facilities',
                           'audit_chain', 'audit_event', 'patients', 'patient_identifiers', 'patient_contacts',
                           'facility_counters']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;

-- Sign-in has to find an account before it knows which organisation it belongs to, so this one
-- lookup runs with the owner's rights and returns only what sign-in needs.
CREATE FUNCTION login_lookup(p_email citext)
RETURNS TABLE (id uuid, org_id uuid, password_hash text, full_name text, status text,
               failed_logins int, locked_until timestamptz, org_status text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ SELECT p.id, p.org_id, p.password_hash, p.full_name, p.status, p.failed_logins, p.locked_until, o.status
     FROM practitioners p JOIN organisations o ON o.id = p.org_id WHERE p.email = p_email $$;
REVOKE ALL ON FUNCTION login_lookup(citext) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION login_lookup(citext) TO ${appUser};

CREATE FUNCTION record_login_result(p_id uuid, p_success boolean, p_lock_after int, p_lock_minutes int)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ UPDATE practitioners SET
     failed_logins = CASE WHEN p_success THEN 0 ELSE failed_logins + 1 END,
     locked_until = CASE WHEN p_success THEN NULL
                         WHEN failed_logins + 1 >= p_lock_after THEN now() + make_interval(mins => p_lock_minutes)
                         ELSE locked_until END
   WHERE id = p_id $$;
REVOKE ALL ON FUNCTION record_login_result(uuid, boolean, int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION record_login_result(uuid, boolean, int, int) TO ${appUser};

-- A tenant sees only its own organisation row. The application role is not the table owner, so
-- ENABLE (without FORCE) is enough to bind it, while the owner-run functions above keep working.
ALTER TABLE organisations ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_organisation ON organisations USING (id = current_org()) WITH CHECK (id = current_org());

-- Onboarding creates the organisation before any tenant context exists, so it goes through one
-- narrow function rather than a general INSERT right.
CREATE FUNCTION create_organisation(p_name text, p_slug citext)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS
$$ DECLARE v_id uuid;
   BEGIN
     INSERT INTO organisations (name, slug) VALUES (p_name, p_slug) RETURNING id INTO v_id;
     RETURN v_id;
   END $$;
REVOKE ALL ON FUNCTION create_organisation(text, citext) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_organisation(text, citext) TO ${appUser};
