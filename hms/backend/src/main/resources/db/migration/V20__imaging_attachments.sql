-- Images attached to an imaging study. The bytes live in object storage; this table is the record of what was attached, by whom,
-- and the checksum that proves the stored file is the one that was uploaded. PNG and JPEG only: DICOM is not handled.

CREATE TABLE imaging_attachments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id uuid NOT NULL,
  order_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  content_type text NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
  size_bytes int NOT NULL CHECK (size_bytes BETWEEN 1 AND 8388608),
  width int NOT NULL CHECK (width BETWEEN 1 AND 20000),
  height int NOT NULL CHECK (height BETWEEN 1 AND 20000),
  sha256 text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  caption text CHECK (length(caption) <= 200),
  uploaded_by uuid NOT NULL,
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  -- An attachment put on the wrong study is withdrawn, not erased: the row and the file stay for the audit trail.
  removed_at timestamptz,
  removed_by uuid,
  removed_reason text CHECK (length(removed_reason) BETWEEN 5 AND 300),
  UNIQUE (org_id, id),
  -- The same image is not attached twice to one study.
  UNIQUE (org_id, order_id, sha256),
  CHECK ((removed_at IS NULL) = (removed_by IS NULL AND removed_reason IS NULL)),
  FOREIGN KEY (org_id, order_id) REFERENCES imaging_orders (org_id, id),
  FOREIGN KEY (org_id, facility_id) REFERENCES facilities (org_id, id)
);
CREATE INDEX imaging_attachments_order ON imaging_attachments (org_id, order_id, uploaded_at);

ALTER TABLE imaging_attachments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON imaging_attachments USING (org_id = current_org()) WITH CHECK (org_id = current_org());

-- The only change a row ever accepts is being withdrawn, once.
CREATE FUNCTION imaging_attachment_guard() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN
     IF TG_OP = 'DELETE' THEN
       RAISE EXCEPTION 'imaging_attachments is append-only';
     END IF;
     IF OLD.removed_at IS NOT NULL THEN
       RAISE EXCEPTION 'that attachment was already withdrawn';
     END IF;
     IF (NEW.id, NEW.org_id, NEW.order_id, NEW.facility_id, NEW.content_type, NEW.size_bytes, NEW.width, NEW.height, NEW.sha256, NEW.caption, NEW.uploaded_by, NEW.uploaded_at)
        IS DISTINCT FROM (OLD.id, OLD.org_id, OLD.order_id, OLD.facility_id, OLD.content_type, OLD.size_bytes, OLD.width, OLD.height, OLD.sha256, OLD.caption, OLD.uploaded_by, OLD.uploaded_at) THEN
       RAISE EXCEPTION 'only withdrawing an attachment is allowed';
     END IF;
     RETURN NEW;
   END $$;
CREATE TRIGGER imaging_attachments_guard BEFORE UPDATE OR DELETE ON imaging_attachments FOR EACH ROW EXECUTE FUNCTION imaging_attachment_guard();
