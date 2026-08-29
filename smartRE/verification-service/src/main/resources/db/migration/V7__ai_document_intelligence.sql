ALTER TABLE seller_identity_documents
    ADD COLUMN ai_detected_category VARCHAR(60),
    ADD COLUMN ai_category_confidence INTEGER,
    ADD COLUMN ai_category_mismatch BOOLEAN DEFAULT false,
    ADD COLUMN ai_side_detected VARCHAR(10);

ALTER TABLE property_ownership_documents
    ADD COLUMN ai_detected_category VARCHAR(60),
    ADD COLUMN ai_category_confidence INTEGER,
    ADD COLUMN ai_category_mismatch BOOLEAN DEFAULT false,
    ADD COLUMN ai_side_detected VARCHAR(10),
    ADD COLUMN ai_extracted_fields TEXT;

ALTER TABLE seller_identity_verifications
    ADD COLUMN face_match_score INTEGER,
    ADD COLUMN face_match_source VARCHAR(30),
    ADD COLUMN face_match_passed BOOLEAN;
