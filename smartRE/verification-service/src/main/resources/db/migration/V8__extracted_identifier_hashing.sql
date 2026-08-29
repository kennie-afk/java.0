ALTER TABLE seller_identity_verifications
    ADD COLUMN extracted_id_number_hash VARCHAR(64);

CREATE UNIQUE INDEX uk_siv_extracted_id_hash ON seller_identity_verifications (extracted_id_number_hash)
    WHERE extracted_id_number_hash IS NOT NULL AND status <> 'REJECTED';

ALTER TABLE property_ownership_documents
    ADD COLUMN ai_signature_detected BOOLEAN,
    ADD COLUMN ai_seal_detected BOOLEAN;
