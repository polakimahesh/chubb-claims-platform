-- One row per delivered notification. The unique event_id makes processing idempotent: a redelivered Kafka
-- event cannot notify the claimant twice.
CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,
    claim_id        UUID NOT NULL,
    claim_number    VARCHAR(40) NOT NULL,
    recipient_email VARCHAR(200) NOT NULL,
    event_type      VARCHAR(30) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    subject         VARCHAR(300) NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    created_at      TIMESTAMP NOT NULL
);

-- "my notifications", newest first
CREATE INDEX idx_notifications_recipient ON notifications (recipient_email, created_at);
