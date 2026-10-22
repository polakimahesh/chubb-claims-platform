# Architecture

How the Claims Platform is put together: the system as a whole, the Angular frontend, the Spring Boot backend, and
how they interact.

- [1. System overview](#1-system-overview)
- [2. Frontend architecture](#2-frontend-architecture)
- [3. Backend architecture](#3-backend-architecture)
- [4. How the frontend and backend interact](#4-how-the-frontend-and-backend-interact)
- [5. REST vs Kafka](#5-rest-vs-kafka)
- [6. Security](#6-security)
- [7. Reliability of the event flow](#7-reliability-of-the-event-flow)
- [8. Domain: claim lifecycle and liability exposure](#8-domain-claim-lifecycle-and-liability-exposure)
- [9. Data model](#9-data-model)
- [10. Deployment view](#10-deployment-view)

---

## 1. System overview

```
                         ┌─────────────────────────────────────────────────┐
  Claimant / Officer /   │                claims-web (Angular 22)          │
  Manager (browser) ───► │  claimant area · staff area · manager area      │
                         │  served by nginx :8080   (dev: ng serve :4200)  │
                         └──────────────────────┬──────────────────────────┘
                                                │ HTTPS/REST, one origin, HTTP Basic
              ┌─────────────────────────────────┼────────────────────────────────┐
              │ /api/claims, /api/staff, /api/me│ /api/reports                   │ /api/notifications
              ▼                                 ▼                                ▼
   ┌─────────────────────┐          ┌──────────────────────┐        ┌────────────────────────┐
   │   claims-service    │          │  reporting-service   │        │  notification-service  │
   │   :8081             │          │  :8082               │        │  :8083                 │
   │   system of record  │          │  read model, reports │        │  claimant e-mails      │
   └──────────┬──────────┘          └──────────▲───────────┘        └────────────▲───────────┘
              │ outbox relay                    │ consumer group                  │ consumer group
              └──────────────► Kafka: claims.events (3 partitions, key = claimId) ─┘
              │                                 │                                 │
      PostgreSQL `claims`             PostgreSQL `reporting`          PostgreSQL `notifications`
      + document store                                                   (+ simulated e-mail sender)

  Shared libraries: claims-events (event contract) · claims-security (auth, roles, error responses)
```

| Component | Role | Profile |
|---|---|---|
| `claims-web` | One Angular app for claimants, officers and managers | Single-page app, role-based areas |
| `claims-service` | **Write side and system of record**: lifecycle rules, audit trail, documents, REST for claimants and staff | Transactional, strongly consistent |
| `reporting-service` | **Read side**: builds a read model from events, serves manager reports | Read-heavy, eventually consistent |
| `notification-service` | E-mails claimants about their claims, keeps a log | Asynchronous, idempotent, failure-isolated |
| Kafka `claims.events` | Carries every claim change from the write side to the consumers | At-least-once, ordered per claim |

---

## 2. Frontend architecture

Angular 22, standalone components, **signals**, **zoneless** change detection and Angular Material 3.

```
src/app/
├── app.routes.ts        lazy routes, guarded by role
├── app.config.ts        router, HttpClient + interceptor, date adapter
├── core/                no UI: shared by every feature
│   ├── models.ts        typed models mirroring the backend DTOs
│   ├── api/             ClaimsApi · ReportsApi · NotificationsApi (one method per endpoint)
│   ├── auth.service     session (HTTP Basic credentials in sessionStorage, roles from /api/me)
│   ├── auth.interceptor adds credentials to /api calls, maps errors to ApiError, signs out on 401
│   ├── guards.ts        authGuard · roleGuard · guestGuard · homeRedirect
│   ├── reference.ts     markets, statuses, and staffActions(), the mirror of the lifecycle rules
│   ├── theme.service    light / dark / system
│   └── latest.ts        cancels stale requests so an old response never overwrites a newer one
├── layout/shell         side navigation by role, toolbar, theme and account menus
├── shared/              claim table, status chip, file picker, bar list, page header, empty state, pipes
└── features/
    ├── login/           sign-in
    ├── claimant/        My claims · Report an incident (3-step wizard)
    ├── claim/           the claim page for every role + the action dialog
    ├── staff/           Intake queue · Workload
    ├── manager/         Dashboard · Team performance · SLA breaches
    └── notifications/
```

**Principles**

- **One codebase, two user types.** Sign-in learns the roles. Routes and navigation are built from them, and each
  area is lazy-loaded. The claim page is shared: claimants get "answer" and "upload", staff get an action bar.
- **The backend is authoritative.** The UI mirrors business rules only to decide which actions to show. Every rule
  is still enforced by the server, and the UI shows its message if it refuses.
- **Server state is the truth.** After every command the claim is reloaded. A `409` (state conflict or concurrent
  update) reloads it too, so a stale screen corrects itself.
- **Eventual consistency is visible.** Report pages show when they were loaded and offer Refresh.
- **Consistent design.** A token-based design system (`src/styles.scss`) with light and dark themes. Details in
  [frontend.md](frontend.md).

---

## 3. Backend architecture

### Why three services

The split follows the **read/write profile and the failure domain**, not the nouns of the domain.

- **claims-service:** claim handling is transactional, with strict rules and modest volume, so consistency matters
  most. Claims, assessment, documents and the claimant and officer workflows share one aggregate and one
  consistency boundary.
- **reporting-service:** manager views aggregate across all claims. They are read-heavy, can be a few seconds stale,
  and must never slow down or lock the transactional store.
- **notification-service:** outbound messages are slow and failure-prone. They must never block or fail a claim
  decision; this service retries on its own and can be replaced independently.

Both consumers subscribe to the same topic with their own consumer groups. Adding notifications needed **no change
to claims-service**.

### Layers in each service

```
controller   REST endpoints, request validation, OpenAPI annotations
service      use cases, transactions, record-level authorisation
entity       JPA entities; the Claim aggregate holds the lifecycle rules
repository   Spring Data JPA queries
dto          request and response records (the API contract)
exception    one @RestControllerAdvice → RFC 7807 problem details
config       security rules, Kafka topics, OpenAPI
messaging    outbox relay (claims) / Kafka listeners (reporting, notification)
storage      document storage abstraction (claims)
```

---

## 4. How the frontend and backend interact

### One origin, routed by path

The browser only ever calls its own origin. A reverse proxy routes each API prefix to the service that owns it, so
there is no CORS and the app hard-codes no ports.

| Path | Service | Development | Production (Docker) |
|---|---|---|---|
| `/api/claims/**`, `/api/staff/**`, `/api/me` | claims-service :8081 | `proxy.conf.mjs` (Angular dev server) | `nginx.conf` |
| `/api/reports/**` | reporting-service :8082 | ″ | ″ |
| `/api/notifications/**` | notification-service :8083 | ″ | ″ |
| everything else | the Angular app (SPA fallback to `index.html`) | ″ | ″ |

Both proxies also remove the `WWW-Authenticate: Basic` header, so browsers never open their own login pop-up over
the app.

### A request, end to end

```
 Officer clicks "Approve"
   → ActionDialog confirms → ClaimsApi.approve(id)            POST /api/staff/claims/{id}/approve
   → authInterceptor adds  Authorization: Basic …
   → nginx → claims-service: Security (OFFICER?) → ClaimService (assigned officer?) → Claim.approve()
        one DB transaction: claim + claim_history + outbox_events
   ← 200 + updated claim   (or RFC 7807: 409 conflict / 422 rule, shown to the user)
   → the claim page reloads the claim, timeline and requests
   … ~1 s later: outbox relay → Kafka → reporting updates exposure; notification e-mails the claimant
```

### The contract

- **Owned by the backend:** the OpenAPI 3 documents are exported from the running services
  (`*/src/main/resources/openapi/*.yaml`, live at `/v3/api-docs.yaml` and `/swagger-ui.html`).
- **Mirrored by the frontend:** `core/models.ts` types and the API clients. Unit tests pin every endpoint, method and
  body, and the end-to-end suite catches drift against the real services.
- **Errors:** RFC 7807 `application/problem+json` everywhere (validation errors include an `errors` list), turned
  into readable messages by `ApiError`.

---

## 5. REST vs Kafka

| Interaction | Transport | Why |
|---|---|---|
| Browser → any service | REST | The user needs an immediate answer and immediate validation |
| Submit, assign, reassign, assess, approve, reject, settle, answer, upload | REST → claims-service | Commands on the system of record; strongly consistent |
| Tracking, queue, workload, documents | REST → claims-service | Reads of the system of record |
| Claim changes → reporting and notifications | **Kafka** | Fan-out to independent consumers; their speed and availability don't affect claim handling |
| Manager reports, notification history | REST → reporting / notification | Queries over each service's own data |

---

## 6. Security

- **Authentication:** stateless HTTP Basic with roles `CLAIMANT`, `OFFICER` and `MANAGER` (shared `claims-security`
  module). The frontend verifies credentials with `GET /api/me` and keeps them in `sessionStorage` for the tab's
  lifetime.
- **Authorisation:**
  - **URL rules** per service (deny by default) decide who may call an endpoint.
  - **Record rules** in the service layer decide which data a caller may touch:
    - claimants see only their own claims; another claimant's claim returns `404`, so IDs can't be probed
    - officers act only on claims assigned to them
    - only managers or the current officer can reassign
- **Identity** always comes from the authenticated user, never from the request body.
- **Secrets:** no real secrets in the repository. The only credentials are the documented demo users in each service's
  `application.yml` (`app.security.demo-password`, `Test@123`) and the local-only database login in
  `docker-compose.yml`, both for local development and assessment use only.
- **Frontend:** route guards hide screens a role can't use. This is convenience only; the server enforces every rule.
- **Containers:** non-root users; nginx sends `X-Content-Type-Options`, `X-Frame-Options` and `Referrer-Policy`.
  Uploaded documents are sniffed against their declared type.

---

## 7. Reliability of the event flow

1. **Transactional outbox:** a claim change, its audit row and its event row commit in one transaction, so no event
   is lost or sent for a rolled-back change.
2. **Relay:** `OutboxPublisher` sends unpublished rows in order, keyed by claim id (`acks=all`, idempotent
   producer), and stops at the first failure to keep the order. Delivery is at-least-once.
3. **Idempotent consumers:** events carry the full claim state and its version.
   - reporting applies an event only if its version is newer
   - notification stores the event id under a unique constraint, so the same e-mail is never sent twice
4. **Poison messages:** three retries, then the record goes to `claims.events.DLT`.
5. **Concurrency:** JPA optimistic locking; a concurrent update returns `409`, and the UI reloads.

---

## 8. Domain: claim lifecycle and liability exposure

```
SUBMITTED ──pick up──► UNDER_REVIEW ──approve (needs assessment)──► APPROVED ──settle──► SETTLED
                          │   ▲     \
           request info   │   │      └──reject (with reason)──► REJECTED
                          ▼   │ claimant answers all open requests
                      INFO_REQUESTED

reassign: while UNDER_REVIEW or INFO_REQUESTED; status unchanged
```

- The rules live in the `Claim` entity, the only code that changes status. Invalid transitions return `409`; a
  business-rule violation (such as approving without an assessment) returns `422`.
- **Liability exposure:** open = not `SETTLED` or `REJECTED`. Exposure per claim = the assessed amount, or the
  claimant's estimate before assessment. It is reported per market, type and currency, and as one total converted at
  indicative FX rates.
- **SLA:** a claim breaches when it stays unassigned longer than 24 h, or open longer than 7 days (configurable).
  Time waiting on the claimant doesn't count against the officer.

---

## 9. Data model

| Service | Tables |
|---|---|
| claims | `claims` (aggregate root, optimistic version), `info_requests`, `claim_history` (append-only timeline), `claim_documents` (metadata; bytes in the document store), `outbox_events` |
| reporting | `claim_view`: one denormalised row per claim, with pre-computed resolution time |
| notification | `notifications`: one row per message sent, unique per event |

Each service owns its database; schemas are versioned with Flyway.

---

## 10. Deployment view

```
docker compose up --build
├── postgres:16-alpine           three databases (claims, reporting, notifications); not published to the host
├── apache/kafka:3.8.0           KRaft, topics declared by the services; :29092 for host tools
├── claims-service               :8081   (+ volume for documents)
├── reporting-service            :8082
├── notification-service         :8083
└── web (nginx + Angular build)  :8080   ← the entry point for users
```

The reasoning behind each choice, and the alternatives considered:
[decisions-and-assumptions.md](decisions-and-assumptions.md).
