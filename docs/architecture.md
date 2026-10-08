# Architecture

## Context

```
 Claimant ─┐                         Manager ─┐                      Claimant ─┐
 Officer ──┤ REST                             │ REST                            │ REST
           v                                  v                                 v
   ┌────────────────┐   claims.events   ┌───────────────────┐   ┌──────────────────────┐
   │ claims-service │ ────────────────> │ reporting-service │   │ notification-service │
   │ (system of     │   Kafka, keyed by │ (read model,      │   │ (claimant e-mails,   │
   │  record)       │   claimId, via    │  aggregations,    │   │  idempotent per      │
   │                │   outbox ───────────────────────────────> │  event)              │
   └───────┬────────┘                   └─────────┬─────────┘   └──────────┬───────────┘
           │                                      │                        │
   claims DB + document store               reporting DB            notifications DB
 (claims, info_requests, claim_history,      (claim_view)            (notifications)
  claim_documents, outbox_events)

 Shared libraries: claims-events (event contract) · claims-security (auth, roles, 401/403 bodies)
```

## Why three services

The split follows the **read/write profile and the failure domain**, not the nouns of the domain:

- **claims-service**: claim handling is transactional with strict rules and modest volume. Consistency matters most.
  Claims, assessment, documents and the claimant/officer workflows share one aggregate and one consistency boundary,
  so they stay together (splitting them would need distributed transactions for no benefit).
- **reporting-service**: manager views are aggregations across all claims, read-heavy and fine a few seconds stale.
  They must never slow down or lock the transactional store.
- **notification-service**: outbound communication is slow and failure-prone (mail servers, SMS gateways). It must
  never block or fail a claim decision; it retries on its own and can be scaled or replaced independently.

Both consumers subscribe to the same topic with their own consumer group: adding the notification service needed **no
change to claims-service**, which is the point of publishing events instead of calling services directly.

## REST vs Kafka

| Concern | Transport | Reason |
|---|---|---|
| Submit, assign, reassign, assess, approve, reject, settle, respond, upload | REST | The caller needs an immediate answer and immediate validation; strongly consistent |
| Tracking, queue, workload, documents | REST | Reads of the system of record |
| Claim changes → reporting and notifications | Kafka | Fan-out to independent consumers; decouples their availability and speed from claim handling |
| Manager reports, notification history | REST | Query APIs over each service's own data |

## Security

- HTTP Basic, stateless, roles `CLAIMANT` / `OFFICER` / `MANAGER` (shared `claims-security` module).
- **URL rules** per service (deny by default) decide who may call an endpoint; **record rules** in the service layer
  decide which data: a claimant only sees their own claims (other claims answer 404, so IDs cannot be probed), an
  officer only acts on claims assigned to them, and only managers or the current officer can reassign.
- The claimant's identity comes from the authenticated user, never from the request body.
- No credential is stored in the repository: Docker reads `.env` (git-ignored, generated with random values by
  `scripts/init-env`), and without a configured password each service generates a random one at startup.
- 401/403 are RFC 7807 problem responses like every other error.

## Reliability of the event flow

1. **Transactional outbox.** A claim change, its audit row and its event row commit in one DB transaction, so an
   event is never lost or sent for a change that rolled back (no dual-write problem).
2. **Relay.** `OutboxPublisher` polls unpublished rows in id order and sends them keyed by `claimId`
   (`acks=all`, idempotent producer). It stops at the first failure to preserve order. Delivery is at-least-once.
3. **Idempotent consumers.** Events carry the full claim state, the claim's optimistic-lock `version`, the claimant's
   contact details and a note (reason/question). Reporting applies an event only if its version is newer; notification
   stores the event id under a unique constraint, so a redelivered event never e-mails the claimant twice.
4. **Poison messages.** Three retries, then the record goes to `claims.events.DLT`.

## Lifecycle

```
SUBMITTED --assign--> UNDER_REVIEW --approve (needs assessment)--> APPROVED --settle--> SETTLED
                         |   ^    \
          request info   |   |     +--reject--> REJECTED
                         v   |  claimant answers all open requests
                     INFO_REQUESTED

reassign: UNDER_REVIEW / INFO_REQUESTED, status unchanged, new officer
```

Rules live in the `Claim` entity (the only code that mutates status). State checks run before business rules, so
acting on a closed or unassigned claim is always a 409.

## Liability exposure

Open claims = not `REJECTED` / `SETTLED`. Exposure per claim = officer's assessed amount if present, otherwise the
claimant's estimate. `APPROVED` claims remain exposure until settled. Reported per market, claim type and currency;
`/api/reports/exposure/total` converts everything into one currency using configured indicative rates.

## Data model

- claims: `claims` (aggregate root; indexes for officer workload, intake queue, claimant lookup), `info_requests`,
  `claim_history` (append-only timeline), `claim_documents` (metadata; bytes in the document store), `outbox_events`
- reporting: `claim_view` (one denormalised row per claim, with pre-computed resolution time for SQL aggregation)
- notification: `notifications` (one row per sent message, unique per event)
