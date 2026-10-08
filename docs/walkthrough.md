# Walkthrough Notes

## 15–20 min: presentation outline

1. Problem decomposition: three actors, one write aggregate, two independent read/side-effect concerns (docs/architecture.md).
2. Why three services; what REST vs Kafka each carry; adding notifications needed no change to claims-service.
3. Lifecycle rules in the `Claim` entity (`ClaimLifecycleTest`), security: URL roles + record-level ownership.
4. Reliability: outbox, idempotent consumers (version / event id), retry + DLT, optimistic locking.
5. Demo: `docker compose up --build`, then the Postman collection (or newman) end to end, then `/api/reports/*` and
   `/api/notifications`.

## Likely "why not X?" questions

- *Why not a monolith?* Valid for this scale; the split isolates the read profile and the slow/failure-prone e-mail
  side effect, and makes Kafka load-bearing. The seams are clean: consumers only know the event contract.
- *Why not publish to Kafka directly in the request?* Dual write; a crash between commit and send loses the event.
- *Why not exactly-once?* At-least-once plus idempotent consumers gives the same effect with less machinery.
- *Why full-state events?* Idempotent, order-tolerant, new consumers need no history and no call-backs.
- *Why HTTP Basic?* Runs locally with no identity provider; the authorisation model (roles + ownership) is what
  matters and carries over unchanged to JWT/OIDC.
- *Why 404 for another claimant's claim?* A 403 would confirm the claim exists.
- *Why sniff document bytes?* `Content-Type` is client-controlled; a renamed executable must not be stored as a PDF.

## What I would do with more time

- OIDC/JWT authentication against a real identity provider; per-market data access for staff
- Real e-mail/SMS channel behind `NotificationSender`, with templates per market/language
- Live FX source behind `FxRateProvider`; S3/Blob behind `DocumentStorage`, with virus scanning
- Outbox: `SKIP LOCKED` for multiple instances, purge job, lag metrics and alerts; DLT replay tooling
- Schema registry / Avro or JSON Schema for the event contract and versioning
- Testcontainers (real Postgres + Kafka) in CI alongside the H2/embedded-Kafka suite
