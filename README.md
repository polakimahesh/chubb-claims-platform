# Claims Platform (Chubb APAC take-home — Backend)

Backend for a motor and property claims platform. Claimants report incidents, track claims, upload documents and
answer information requests; claims staff work an intake queue and move claims to settlement or rejection; managers
see workload, performance, SLA breaches and outstanding liability exposure; claimants are notified of decisions.

## Overview

Three Spring Boot services connected by Kafka, plus two small shared libraries:

| Module | Port | Role | Profile |
|---|---|---|---|
| `claims-service` | 8081 | **Write side / system of record.** Claim lifecycle, business rules, audit trail, documents, REST API for claimants and staff. Publishes events to Kafka via a transactional outbox. | Low volume, strongly consistent, transactional |
| `reporting-service` | 8082 | **Read side.** Consumes events, maintains a denormalised read model, serves manager reports (exposure, workload, performance, SLA). | Read-heavy, aggregations, eventually consistent |
| `notification-service` | 8083 | Consumes events and e-mails claimants about decisions (simulated channel), stores what was sent. | Async, fan-out, idempotent |
| `claims-events` | – | Library: the Kafka event contract (`ClaimEvent`) and enums. No framework dependencies. | – |
| `claims-security` | – | Library: shared authentication (HTTP Basic, roles) and RFC 7807 401/403 responses. | – |

See [docs/architecture.md](docs/architecture.md) for the diagram and [docs/decisions-and-assumptions.md](docs/decisions-and-assumptions.md) for why.

## Features

- **Claimant:** submit a claim, track status and timeline, list own claims, answer information requests, upload PDF/PNG/JPEG documents, receive e-mail notifications.
- **Staff:** intake queue (oldest first, filter by market/type), pick up claims, request information, assess, approve, reject, settle, reassign, per-officer workload.
- **Managers:** outstanding exposure by market/type/currency (and converted into one currency), workload, performance, SLA breaches, reassignment of any claim.
- **Lifecycle:** `SUBMITTED → UNDER_REVIEW ⇄ INFO_REQUESTED`, `UNDER_REVIEW → APPROVED → SETTLED`, `UNDER_REVIEW → REJECTED`; invalid transitions return `409`.
- **Security:** roles `CLAIMANT` / `OFFICER` / `MANAGER`; claimants only see their own claims; officers only act on claims assigned to them; deny-by-default URL rules.
- **Reliability:** optimistic locking, transactional outbox, idempotent consumers, retry then dead-letter topic, RFC 7807 errors everywhere.

## Technology Stack

Java 17, Spring Boot 3.3, Gradle (wrapper), Spring Security, Spring Data JPA, Flyway, PostgreSQL (Docker) / H2 (local default),
Apache Kafka (KRaft), springdoc OpenAPI, JUnit 5 / AssertJ / MockMvc / embedded Kafka.

## Architecture

```
 Claimant / Officer                      Manager                    Claimant
        |  REST                             |  REST                     |  REST
        v                                   v                           v
+------------------+   claims.events   +--------------------+   +----------------------+
|  claims-service  | ----------------> |  reporting-service |   | notification-service |
|  controller      |   (Kafka, via     |  listener          |   |  listener            |
|  service         |    outbox)        |  projection        |   |  templates + sender  |
|  entity (rules)  | ----------------> |  reports, FX, SLA  |   |  notification log    |
|  documents       |                   +---------+----------+   +-----------+----------+
|  outbox relay    |                             |                          |
+--------+---------+                        DB: reporting             DB: notifications
         |
   DB: claims  + document store
```

## Project Structure

```
claims-events/          shared event contract
claims-security/        shared authentication / authorisation plumbing
claims-service/         controller / service / repository / entity / dto / exception / config / messaging / storage
  src/main/resources/db/migration   Flyway SQL
  src/main/resources/openapi        OpenAPI contract (exported from the running service)
reporting-service/      same layering (+ fx/)
notification-service/   same layering
docs/                   architecture, decisions & assumptions, testing guide (with payloads), walkthrough notes, postman/
prompts/                the prompts used with Claude
ai-journal/             AI working journal
assessment-brief/       the original brief and guidelines
scripts/                init-env.sh / init-env.ps1 (generate local secrets)
docker-compose.yml      Postgres + Kafka + the three services
```

## Prerequisites

- JDK 17+ (the Gradle wrapper downloads Gradle itself)
- Docker (for the one-command setup, or to get Kafka for local runs)

## Running with Docker (one command after creating `.env`)

Secrets are never stored in the repository. Generate a git-ignored `.env` with random values, then start everything:

```bash
./scripts/init-env.sh          # PowerShell: .\scripts\init-env.ps1
docker compose up --build
```

Swagger UIs: http://localhost:8081/swagger-ui.html (claims), :8082 (reporting), :8083 (notification).
The demo-user password is `APP_SECURITY_DEMO_PASSWORD` in `.env`.

## Running Locally (apps outside Docker)

Each service defaults to in-memory H2 and Kafka at `localhost:29092`. **Use the same demo password for all three
services** (if it is not set, each service generates a different random one and logs it once):

```bash
export APP_SECURITY_DEMO_PASSWORD='choose-a-password'        # PowerShell: $env:APP_SECURITY_DEMO_PASSWORD='...'
docker compose up -d kafka           # needs .env (init-env first); only needed for events to flow
./gradlew :claims-service:bootRun    # terminal 1
./gradlew :reporting-service:bootRun # terminal 2
./gradlew :notification-service:bootRun # terminal 3
```

**IntelliJ IDEA:** open the repository root (the folder with `settings.gradle`) as a Gradle project with JDK 17 and
annotation processing enabled (for Lombok), set `APP_SECURITY_DEMO_PASSWORD` in each run configuration's environment
variables, then run `ClaimsServiceApplication`, `ReportingServiceApplication` and `NotificationServiceApplication`.

> **Do not run the Docker stack and local `bootRun` copies of the same service at the same time.** They would fight
> over ports and, for the consumers, share a Kafka consumer group so events get split between instances.

Without Kafka the services still start; the outbox keeps events and relays them once Kafka is reachable.

## Demo Users

| Username | Role |
|---|---|
| `tan@example.com`, `lee@example.com` | CLAIMANT (the e-mail is the claimant identity) |
| `officer-1`, `officer-2` | OFFICER |
| `manager-1` | MANAGER |

All share the password from `APP_SECURITY_DEMO_PASSWORD`. Replace with an identity provider in production.

## Trying It

```bash
PW=...   # APP_SECURITY_DEMO_PASSWORD
ID=$(curl -s -u tan@example.com:$PW -XPOST localhost:8081/api/claims -H 'Content-Type: application/json' -d '{
  "claimantName":"Tan Wei","market":"SG","claimType":"MOTOR","description":"Rear-ended at lights",
  "incidentDate":"2026-01-10","currency":"SGD","estimatedAmount":5000}' | sed 's/.*"id":"\([^"]*\)".*/\1/')
curl -u officer-1:$PW -XPOST localhost:8081/api/staff/claims/$ID/assign
curl -u officer-1:$PW -XPOST localhost:8081/api/staff/claims/$ID/assess -H 'Content-Type: application/json' -d '{"assessedAmount":4200}'
curl -u officer-1:$PW -XPOST localhost:8081/api/staff/claims/$ID/approve
# a second or two later
curl -u manager-1:$PW localhost:8082/api/reports/exposure
curl -u tan@example.com:$PW localhost:8083/api/notifications
```

The [Postman collection](docs/postman/claims-platform.postman_collection.json) walks through everything with assertions
(see "API Documentation").

## Running Tests

```bash
./gradlew test       # all modules
./gradlew build      # compile + tests + jars
```

Full details, the test catalogue and request/response payloads: [docs/testing.md](docs/testing.md).

Tests use H2 with the real Flyway migrations and an embedded Kafka broker; they need neither Docker nor a running Kafka.
Coverage: lifecycle rules (unit); the full claimant/staff flow, authentication and authorisation, ownership rules,
reassignment, documents, validation and error cases, concurrency and Kafka outage/recovery (claims-service);
projection idempotency, exposure, FX conversion, SLA, workload and performance (reporting-service);
notification rules, idempotency, access scoping and the Kafka transport (notification-service).

## API Documentation

- **OpenAPI contracts** (exported from the running services): [claims](claims-service/src/main/resources/openapi/claims-api.yaml),
  [reporting](reporting-service/src/main/resources/openapi/reporting-api.yaml),
  [notification](notification-service/src/main/resources/openapi/notification-api.yaml). Live Swagger UI at `/swagger-ui.html`
  (use *Authorize* with HTTP Basic).
- **Postman:** import [docs/postman/claims-platform.postman_collection.json](docs/postman/claims-platform.postman_collection.json),
  set the collection variable `demoPassword`, run folders 0 → 4. Headless:
  `newman run docs/postman/claims-platform.postman_collection.json --working-dir docs/postman --env-var demoPassword=$PW`.

| Who | Endpoint |
|---|---|
| Claimant | `POST /api/claims` · `GET /api/claims` (mine) · `GET /api/claims/{id}` · `GET /api/claims/{id}/history` · `GET /api/claims/{id}/info-requests` · `POST /api/claims/{id}/info-requests/{requestId}/response` · `POST/GET /api/claims/{id}/documents` · `GET /api/claims/{id}/documents/{documentId}` · `GET /api/notifications` (notification-service) |
| Staff (OFFICER) | `GET /api/staff/claims/queue` · `GET /api/staff/claims` (my workload) · `POST /api/staff/claims/{id}/assign \| info-requests \| assess \| approve \| reject \| settle` · `POST .../reassign` (also MANAGER) |
| Manager | `GET /api/reports/summary \| exposure \| exposure/total \| workload \| performance \| sla-breaches` · `GET /api/staff/**` (read-only) · `POST /api/staff/claims/{id}/reassign` |

Errors use RFC 7807 problem details: `400` validation, `401` not authenticated, `403` not permitted, `404` unknown claim
(also for other claimants' claims), `405`, `409` invalid transition or concurrent update, `413` document too large,
`415` unsupported document type, `422` business-rule violation, `500` generic.

## Database

Each service owns its database (`claims`, `reporting`, `notifications`); schema is managed by Flyway
(`src/main/resources/db/migration`). Default local DB is in-memory H2 (PostgreSQL mode); Docker uses PostgreSQL.
Documents are stored on a volume (`claims-documents`) behind a `DocumentStorage` interface.

## Configuration

| Variable | Default |
|---|---|
| `APP_SECURITY_DEMO_PASSWORD` | none; a random password is generated and logged if unset (Docker: from `.env`) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | in-memory H2 / `sa` / empty (Docker: Postgres, from `.env`) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` |
| `DOCUMENTS_DIR` | system temp dir (Docker: `/data/documents`) |
| `reporting.sla.*`, `reporting.fx.rates-to-usd` | see `reporting-service/src/main/resources/application.yml` |

## Assumptions, Design Decisions, Known Limitations

See [docs/decisions-and-assumptions.md](docs/decisions-and-assumptions.md). Headlines: HTTP Basic with in-memory demo users
(production: OIDC/JWT); FX rates are static and indicative; the e-mail channel is simulated; at-least-once Kafka delivery
with idempotent consumers; each market settles in one currency and claims must use it.

## AI-Assisted Development

Built with Claude Code. The prompts are in [prompts/](prompts/) and the journal, including what was accepted,
challenged and corrected, is in [ai-journal/claude-journal.md](ai-journal/claude-journal.md).

## Future Improvements

See [docs/walkthrough.md](docs/walkthrough.md) ("What I would do with more time").
