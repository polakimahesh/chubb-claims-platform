-- Read model maintained solely from claims.events. Denormalised for reporting queries; never written by the API.
CREATE TABLE claim_view (
    claim_id            UUID PRIMARY KEY,
    claim_number        VARCHAR(40)   NOT NULL,
    market              VARCHAR(5)    NOT NULL,
    claim_type          VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    assigned_officer_id VARCHAR(100),
    currency            VARCHAR(3)    NOT NULL,
    estimated_amount    NUMERIC(15,2) NOT NULL,
    assessed_amount     NUMERIC(15,2),
    submitted_at        TIMESTAMP     NOT NULL,
    closed_at           TIMESTAMP,
    version             BIGINT        NOT NULL,
    last_event_at       TIMESTAMP     NOT NULL
);

-- Liability exposure: open claims grouped by market/type/currency
CREATE INDEX idx_claim_view_status_market ON claim_view (status, market, claim_type, currency);
-- Workload / performance per officer
CREATE INDEX idx_claim_view_officer_status ON claim_view (assigned_officer_id, status);
