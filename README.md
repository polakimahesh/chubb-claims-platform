# Claims Platform (Chubb APAC take-home — Backend)

Backend for a motor and property claims platform. Claimants report incidents, track claims and answer information
requests; claims staff work an intake queue and move claims to settlement or rejection; managers see workload,
performance and outstanding liability exposure.

## Overview

Two Spring Boot services connected by Kafka:

| Service | Port | Role | Profile |
|---|---|---|---|
| `claims-service` | 8081 | **Write side.** Claim lifecycle, business rules, audit trail. REST API for claimants and staff. Publishes events to Kafka via a transactional outbox. | Low volume, strongly consistent, transactional |
| `reporting-service` | 8082 | **Read side.** Consumes events from Kafka, maintains a denormalised read model, serves manager reports. | Read-heavy, aggregations, eventually consistent |
| `claims-events` | – | Shared library: the Kafka event contract (`ClaimEvent`) and enums. | – |

See [docs/architecture.md](docs/architecture.md) for the diagram and [docs/decisions-and-assumptions.md](docs/decisions-and-assumptions.md) for why.

## Features

- Submit a claim, track status, view the status timeline, answer information requests
- Intake queue (oldest first, filter by market and type) and per-officer workload views
- Lifecycle: `SUBMITTED → UNDER_REVIEW ⇄ INFO_REQUESTED`, `UNDER_REVIEW → APPROVED → SETTLED`, `UNDER_REVIEW → REJECTED`; invalid transitions return `409`
- Optimistic locking, so two officers cannot both pick up the same claim
- Outstanding liability exposure by market, claim type and currency; workload and performance per officer
- Kafka producer (outbox relay) and consumer (idempotent projection, retry then dead-letter topic)

## Technology Stack

Java 17, Spring Boot 3.3, Gradle (wrapper), Spring Data JPA, Flyway, PostgreSQL (Docker) / H2 (local default),
Apache Kafka (KRaft), springdoc OpenAPI, JUnit 5 / AssertJ / MockMvc.

## Architecture

```
 Claimant / Staff                         Manager
        |  REST                              |  REST
        v                                    v
+------------------+   Kafka topic     +--------------------+
|  claims-service  |  claims.events    |  reporting-service |
|  controller      | ----------------> |  listener          |
|  service         |  (via outbox)     |  projection        |
|  entity (rules)  |                   |  report service    |
|  outbox relay    |                   |  controller        |
+--------+---------+                   +---------+----------+
         |                                       |
   DB: claims                              DB: reporting
```

## Project Structure

```
claims-events/        shared event contract
claims-service/       controller / service / repository / entity / dto / exception / config / messaging
  src/main/resources/db/migration   Flyway SQL
  src/main/resources/openapi        OpenAPI contract
reporting-service/    same layering
docs/                 architecture, decisions & assumptions, walkthrough notes, postman/
prompts/              the prompts used with Claude
ai-journal/           AI working journal
assessment-brief/     the original brief and guidelines
docker-compose.yml    Postgres + Kafka + both services
```

## Prerequisites

- JDK 17+ (the Gradle wrapper downloads Gradle itself)
- Docker (only for the one-command setup, or to get Kafka for local runs)

## Running with Docker (one command)

```bash
docker compose up --build
```

Starts Postgres, Kafka, and both services. claims-service: http://localhost:8081/swagger-ui.html,
reporting-service: http://localhost:8082/swagger-ui.html.

## Running Locally (without Docker for the apps)

Each service defaults to in-memory H2 and Kafka at `localhost:29092`.

```bash
docker compose up -d kafka          # optional: only needed for events to flow
./gradlew :claims-service:bootRun    # terminal 1
./gradlew :reporting-service:bootRun # terminal 2
```

Without Kafka the services still start; the outbox keeps events and relays them once Kafka is reachable.

## Trying It

```bash
# 1. claimant submits
curl -s -XPOST localhost:8081/api/claims -H 'Content-Type: application/json' -d '{
  "claimantName":"Tan Wei","claimantEmail":"tan@example.com","market":"SG","claimType":"MOTOR",
  "description":"Rear-ended at lights","incidentDate":"2026-01-10","currency":"SGD","estimatedAmount":5000}'
# 2. officer picks it up, assesses, approves, settles  (ID from step 1)
curl -XPOST localhost:8081/api/staff/claims/$ID/assign  -H 'X-Officer-Id: officer-1'
curl -XPOST localhost:8081/api/staff/claims/$ID/assess  -H 'X-Officer-Id: officer-1' -H 'Content-Type: application/json' -d '{"assessedAmount":4200}'
curl -XPOST localhost:8081/api/staff/claims/$ID/approve -H 'X-Officer-Id: officer-1'
# 3. manager views (a second or two later)
curl -s localhost:8082/api/reports/exposure
curl -s localhost:8082/api/reports/summary
```

## Running Tests

```bash
./gradlew test       # all modules
./gradlew build      # compile + tests + jars
```

Tests use H2 with the real Flyway migrations and do not need Docker or Kafka. Coverage: lifecycle rules (unit),
full claimant/staff API flow including outbox events, validation and error cases (integration), and projection
idempotency, exposure, workload and performance reports.

## API Documentation

Postman collection: [docs/postman/claims-platform.postman_collection.json](docs/postman/claims-platform.postman_collection.json) (import it, then run folders 1-3 in order; it chains claim and info-request ids automatically).

OpenAPI contracts: [claims-api.yaml](claims-service/src/main/resources/openapi/claims-api.yaml),
[reporting-api.yaml](reporting-service/src/main/resources/openapi/reporting-api.yaml). Live Swagger UI at `/swagger-ui.html`.

| Who | Endpoint |
|---|---|
| Claimant | `POST /api/claims` · `GET /api/claims/{id}` · `GET /api/claims?claimantEmail=` · `GET /api/claims/{id}/history` · `GET /api/claims/{id}/info-requests` · `POST /api/claims/{id}/info-requests/{requestId}/response` |
| Staff (`X-Officer-Id` header) | `GET /api/staff/claims/queue` · `GET /api/staff/claims?officerId=` · `POST /api/staff/claims/{id}/assign \| info-requests \| assess \| approve \| reject \| settle` |
| Manager | `GET /api/reports/summary \| exposure \| workload \| performance` |

Errors use RFC 7807 problem details: `400` validation, `404` unknown claim, `409` invalid transition or concurrent
update, `422` business-rule violation.

## Database

Each service owns its database (`claims`, `reporting`); schema is managed by Flyway
(`src/main/resources/db/migration`). Default local DB is in-memory H2 (PostgreSQL mode); Docker uses PostgreSQL.

## Configuration

| Variable | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | in-memory H2 / `sa` / empty (Docker: Postgres, overridable via env) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` |

The Docker credentials are local-development defaults only.

## Assumptions, Design Decisions, Known Limitations

See [docs/decisions-and-assumptions.md](docs/decisions-and-assumptions.md). Headlines: no authentication (officer
identified by header); no FX conversion (exposure is per currency); at-least-once Kafka delivery with idempotent
consumer; performance report aggregated in memory.

## AI-Assisted Development

Built with Claude Code. The prompts are in [prompts/](prompts/) and the journal, including what was accepted,
challenged and corrected, is in [ai-journal/claude-journal.md](ai-journal/claude-journal.md).

## Future Improvements

See [docs/walkthrough.md](docs/walkthrough.md) ("What I would do with more time").
