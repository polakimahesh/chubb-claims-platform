-- Metadata for claim attachments; the bytes live in the document store (local filesystem by default).
CREATE TABLE claim_documents (
    id           UUID PRIMARY KEY,
    claim_id     UUID NOT NULL REFERENCES claims (id),
    file_name    VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    storage_key  VARCHAR(100) NOT NULL UNIQUE,
    uploaded_by  VARCHAR(200) NOT NULL,
    uploaded_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_claim_documents_claim ON claim_documents (claim_id, uploaded_at);
