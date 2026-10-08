-- Pre-computed resolution time lets the performance report aggregate in SQL instead of loading closed claims.
ALTER TABLE claim_view ADD COLUMN resolution_seconds BIGINT;

-- SLA breach scans: "status + age" lookups
CREATE INDEX idx_claim_view_status_submitted ON claim_view (status, submitted_at);
