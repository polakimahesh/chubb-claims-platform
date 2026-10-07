# Architecture

## Context

```
 Claimant ─┐                                   ┌─ Manager
 Staff ────┤ REST                         REST │
           v                                   v
   ┌────────────────┐   claims.events    ┌─────────────────┐
   │ claims-service │ ─────────────────> │ reporting-service│
   │ (system of     │   Kafka, keyed by  │ (read model,     │
   │  record)       │   claimId          │  aggregations)   │
   └───────┬────────┘                    └────────┬─────────┘
           │                                      │
      claims DB                             reporting DB
 (claims, info_requests,                      (claim_view)
  claim_history, outbox_events)
```

## Why two services

The split follows the **read/write profile**, not the nouns of the domain:

- Claim handling is transactional with strict rules and modest volume. Consistency matters most.
- Manager views (exposure, workload, performance) are aggregations across all claims. They are read-heavy and
  can be a few seconds stale, and they should never slow down or lock the transactional store.

Claims, assessment and the claimant/officer workflows stay in one service because they share one aggregate and
one consistency boundary. Splitting them would introduce distributed transactions for no benefit.

## REST vs Kafka

| Concern | Transport | Reason |
|---|---|---|
| Submit, assign, assess, approve, reject, settle, respond | REST | The caller needs an immediate answer and immediate validation; strongly consistent |
| Tracking and queue/workload lists | REST | Reads of the system of record |
| Claim changes → reporting | Kafka | Fire-and-forget fan-out; decouples availability and speed of reporting from claim handling; further consumers (notifications, fraud) can subscribe without changing claims-service |
| Manager reports | REST | Query API over the read model |

## Reliability of the event flow

1. **Transactional outbox.** A claim change, its audit row and its event row commit in one DB transaction, so
   an event is never lost or sent for a change that rolled back (no dual-write problem).
2. **Relay.** `OutboxPublisher` polls unpublished rows in id order and sends them keyed by `claimId`
   (`acks=all`, idempotent producer). It stops at the first failure to preserve order. Delivery is at-least-once.
3. **Idempotent consumer.** Events carry the full claim state and the claim's optimistic-lock `version`.
   `ClaimProjectionService` applies an event only if its version is newer than the stored one, so duplicates and
   redeliveries are harmless.
4. **Poison messages.** Three retries, then the record goes to `claims.events.DLT`.

## Lifecycle

```
SUBMITTED --assign--> UNDER_REVIEW --approve (needs assessment)--> APPROVED --settle--> SETTLED
                         |   ^
          request info   |   |  claimant answers all open requests
                         v   |
                     INFO_REQUESTED          UNDER_REVIEW --reject--> REJECTED
```

Rules live in the `Claim` entity (the only code that mutates status). Only the assigned officer can act on a claim.

## Liability exposure

Open claims = not `REJECTED` / `SETTLED`. Exposure per claim = officer's assessed amount if present, otherwise the
claimant's estimate. `APPROVED` claims remain exposure until settled. Grouped by market, claim type and currency.

## Data model

- `claims` — aggregate root (indexes for officer workload, intake queue, claimant lookup)
- `info_requests` — officer question / claimant answer
- `claim_history` — append-only status timeline
- `outbox_events` — pending Kafka events
- reporting: `claim_view` — one denormalised row per claim
