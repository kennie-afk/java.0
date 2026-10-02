-- Scheduling: clinics, practitioner sessions, appointments, and the day's queue.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE clinics (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL REFERENCES organisations (id),
  facility_id uuid NOT NULL,
  name text NOT NULL CHECK (length(name) BETWEEN 2 AND 120),
  specialty text,
  slot_minutes int NOT NULL DEFAULT 15 CHECK (slot_minutes BETWEEN 5 AND 120),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, id),
  UNIQUE (org_id, facility_id, name),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);

-- A practitioner's recurring weekly hours at a clinic (ISO weekday: 1 = Monday ... 7 = Sunday).
CREATE TABLE clinic_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  clinic_id uuid NOT NULL,
  practitioner_id uuid NOT NULL,
  weekday smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  start_time time NOT NULL,
  end_time time NOT NULL,
  CHECK (end_time > start_time),
  FOREIGN KEY (org_id, clinic_id) REFERENCES clinics (org_id, id) ON DELETE CASCADE,
  FOREIGN KEY (org_id, practitioner_id) REFERENCES practitioners (org_id, id)
);
CREATE INDEX clinic_sessions_clinic ON clinic_sessions (clinic_id, weekday);

CREATE TABLE appointments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  clinic_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  practitioner_id uuid,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
  priority text NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('EMERGENCY', 'PRIORITY', 'ROUTINE')),
  walk_in boolean NOT NULL DEFAULT false,
  reason text,
  queue_number int,
  checked_in_at timestamptz,
  cancel_reason text,
  created_by uuid,
  version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at),
  UNIQUE (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id),
  FOREIGN KEY (org_id, clinic_id) REFERENCES clinics (org_id, id),
  FOREIGN KEY (org_id, patient_id) REFERENCES patients (org_id, id),
  FOREIGN KEY (org_id, practitioner_id) REFERENCES practitioners (org_id, id),
  -- Two live bookings for one practitioner can never overlap, however many requests race for the slot.
  CONSTRAINT appointments_no_double_booking EXCLUDE USING gist
    (practitioner_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (practitioner_id IS NOT NULL AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')),
  CONSTRAINT appointments_patient_no_overlap EXCLUDE USING gist
    (patient_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (walk_in = false AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS'))
);
CREATE INDEX appointments_facility_time ON appointments (org_id, facility_id, starts_at, id);
CREATE INDEX appointments_patient ON appointments (org_id, patient_id, starts_at DESC);
CREATE INDEX appointments_queue ON appointments (org_id, facility_id, status, checked_in_at) WHERE status IN ('CHECKED_IN', 'IN_PROGRESS');

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['clinics', 'clinic_sessions', 'appointments']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (org_id = current_org()) WITH CHECK (org_id = current_org())', t);
  END LOOP;
END $$;
