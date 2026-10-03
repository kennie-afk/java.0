-- Imaging studies become billable the way laboratory tests and dispensings are: a performed study is charged once.
DO $$
DECLARE c text;
BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE conrelid = 'invoice_lines'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%source_type%';
  IF c IS NOT NULL THEN
    EXECUTE format('ALTER TABLE invoice_lines DROP CONSTRAINT %I', c);
  END IF;
END $$;
ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_source_type_check CHECK (source_type IN ('MANUAL', 'CHARGE', 'LAB', 'DISPENSING', 'BED', 'IMAGING'));
