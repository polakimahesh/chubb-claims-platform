# Walkthrough Notes

## 15–20 min: presentation outline

1. Problem decomposition: three actors, one write aggregate, one read concern (docs/architecture.md).
2. Why two services; what REST vs Kafka each carry.
3. Lifecycle rules in the `Claim` entity (`ClaimLifecycleTest`).
4. Reliability: outbox, idempotent consumer, DLT.
5. Demo: README "Trying It", then `/api/reports/*`.

## Likely "why not X?" questions

- *Why not a monolith?* Valid for this scale; chose the split to isolate the read profile and make Kafka load-bearing. The
  seam is clean: reporting only knows the event contract.
- *Why not publish to Kafka directly in the request?* Dual write; a crash between commit and send loses the event.
- *Why not exactly-once?* At-least-once plus an idempotent consumer gives the same effect with less machinery.
- *Why full-state events?* Idempotent, order-tolerant, new consumers need no history.

## What I would do with more time

- Authentication (OIDC), roles (claimant / officer / manager) and per-claimant access control
- Kafka integration test with Testcontainers; verify the Docker path in CI
- Reassignment, SLA timers and escalation; document attachments
- Outbox: `SKIP LOCKED` for multiple instances, purge job, metrics on lag
- Schema registry / Avro or JSON Schema for the event contract and versioning
- Performance report in SQL; FX conversion for cross-currency exposure
- Notification consumer (e-mail/SMS to claimants on decisions)
