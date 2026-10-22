# Developer Guide

Everything needed to set up, run, test, maintain and deploy the Claims Platform: the Angular frontend and the three
Java 17 / Gradle services. Other documents go deeper on specific topics and are linked from each section.

| Topic | Document |
|---|---|
| Why the system is shaped this way | [architecture.md](architecture.md), [decisions-and-assumptions.md](decisions-and-assumptions.md) |
| Frontend design and feature traceability | [frontend.md](frontend.md) |
| Backend test catalogue and API payloads | [testing.md](testing.md) |
| Measured test results and coverage | [test-report.md](test-report.md) |

---

## 1. Project overview and functional requirements

A motor and property claims platform for six APAC markets (SG, HK, MY, TH, ID, AU). Each market settles in its own
currency.

- **Claimants** report an incident, track the claim and its timeline, answer information requests, attach documents,
  and receive decisions by e-mail.
- **Claims officers** pick up claims from an intake queue, request information, assess, approve, reject and settle
  them, and reassign their own claims.
- **Managers** see the summary, outstanding liability exposure (per currency and converted to one), team workload,
  performance and SLA breaches. They can reassign any claim but do not decide claims.

Claim lifecycle (enforced by the `Claim` entity; any other transition returns `409`):

```
SUBMITTED → UNDER_REVIEW ⇄ INFO_REQUESTED
UNDER_REVIEW → APPROVED (needs an assessment, otherwise 422) → SETTLED
UNDER_REVIEW → REJECTED (needs a reason)
```

The full list of 27 features and the screen, API and test behind each is in [§17](#17-requirements-traceability).

## 2. Technology stack and versions

| Layer | Technology |
|---|---|
| Frontend | Angular 22.2 (standalone components, signals, zoneless), Angular Material 22.2 (Material 3), RxJS 7.8, TypeScript 6.0 |
| Frontend tests | Vitest 5 (through `ng test`, jsdom) with V8 coverage, Playwright 1.64 (Chromium) |
| Backend | Java 17, Spring Boot 3.3.5, Spring Security, Spring Data JPA, Spring Kafka, Flyway, springdoc-openapi 2.6 |
| Build | Gradle 8.10.2 (wrapper), npm 11 / Node.js 22+ (tested on Node 24.20) |
| Data | PostgreSQL 16 (Docker), H2 in PostgreSQL mode (local default and tests) |
| Messaging | Apache Kafka 3.8 (KRaft) |
| Backend tests | JUnit 5, AssertJ, MockMvc, embedded Kafka, JaCoCo |
| Containers | Docker Compose; nginx (unprivileged) serves the frontend |

## 3. Architecture

```
 Browser ──► claims-web (Angular)      one origin: dev proxy (:4200) or nginx (:8080)
               │ /api/claims, /api/staff, /api/me  ─────►  claims-service :8081  (system of record)
               │ /api/reports                      ─────►  reporting-service :8082 (read model, manager reports)
               │ /api/notifications                ─────►  notification-service :8083 (claimant e-mails)
                                                              ▲              ▲
 claims-service ── transactional outbox ── Kafka topic claims.events ────────┘ (both consume)
```

- **Backend.** Commands such as submit, assign, assess and approve go to claims-service, which updates the claim,
  appends the audit trail and writes an event to the outbox in one transaction. A relay publishes the outbox to
  Kafka. Reporting and notification consume the events and are idempotent: reporting checks the claim version,
  notification stores each event id once. Each service owns its own database. Details:
  [architecture.md](architecture.md).
- **Frontend.** A single-page app with lazy-loaded areas for each role. It holds no business rules: it mirrors the
  backend rules only to decide which actions to show, and the server stays authoritative. Details:
  [frontend.md](frontend.md).

## 4. Folder structure

```
claims-web/                      Angular app
  src/app/core/                  models (mirror the DTOs), reference data + rule mirror, auth, interceptor, guards,
    api/                         API clients: claims, reports, notifications
  src/app/layout/                shell (navigation by role, account menu)
  src/app/shared/                claim table, status chip, bar list, file picker, page header, empty state, pipes
  src/app/features/              login, claimant, claim (shared claim page + action dialog), staff, manager, notifications
  src/testing.ts                 unit-test helpers
  e2e/                           Playwright end-to-end tests
  proxy.conf.mjs, nginx.conf, Dockerfile, playwright.config.ts
claims-service/                  system of record: controller, service, repository, entity, dto, exception, config,
                                 messaging (outbox relay), storage (documents); resources: application.yml,
                                 db/migration (Flyway), openapi/claims-api.yaml
reporting-service/               Kafka consumer + read model + reports (also fx/)
notification-service/            Kafka consumer + e-mail templates + notification log
claims-events/                   shared Kafka event contract (plain Java)
claims-security/                 shared HTTP Basic security, demo users, UserDirectory, RFC 7807 401/403
docs/                            this guide, architecture, decisions, frontend, testing, test report, postman/
docker/ docker-compose.yml       Postgres init + full stack
scripts/                         init-env.sh / init-env.ps1 (generate .env secrets)
prompts/ ai-journal/             AI prompts and working journal
```

## 5. Prerequisites and environment setup

| Tool | Version | Needed for |
|---|---|---|
| JDK | 17 (newer works through the Gradle toolchain) | building and running the services |
| Node.js | 22 or newer (`winget install OpenJS.NodeJS.LTS` on Windows) | the frontend outside Docker |
| Docker Desktop | with at least 4 GB of memory for the full stack | Kafka, PostgreSQL, one-command start |
| Git | any | – |

```bash
git clone https://github.com/polakimahesh/chubb-claims-platform.git
cd chubb-claims-platform
./scripts/init-env.sh            # PowerShell: .\scripts\init-env.ps1 — writes a git-ignored .env with random secrets
cd claims-web && npm ci && npx playwright install chromium   # frontend dependencies (+ browser for e2e)
```

Avoid spaces and special characters such as `--` in the checkout path; some Node tooling handles them badly.

## 6. Running the application

### Option A: everything in Docker (recommended for a demo)

```bash
./scripts/init-env.sh            # once
docker compose up --build        # Postgres, Kafka, the three services and the web app
```

Open **http://localhost:8080** and sign in with a demo user ([§9](#9-http-basic-authentication)). Swagger UIs:
http://localhost:8081/swagger-ui.html, :8082 and :8083.

### Option B: services and frontend on the host (development)

```bash
export APP_SECURITY_DEMO_PASSWORD='choose-one'   # PowerShell: $env:APP_SECURITY_DEMO_PASSWORD='choose-one'
docker compose up -d kafka                       # events need Kafka (claims still work without it; the outbox waits)
./gradlew :claims-service:bootRun                # terminal 1 → :8081
./gradlew :reporting-service:bootRun             # terminal 2 → :8082
./gradlew :notification-service:bootRun          # terminal 3 → :8083
cd claims-web && npm start                       # terminal 4 → http://localhost:4200
```

All three services must use the **same** demo password. Do not run Docker copies and local copies of the same
service at the same time: they clash on ports and share Kafka consumer groups.

## 7. Gradle build and execution

```bash
./gradlew build                                   # compile, all tests, coverage, boot jars
./gradlew test                                    # all backend tests (+ JaCoCo reports)
./gradlew :claims-service:test                    # one module
./gradlew :claims-service:test --tests '*ClaimLifecycleTest'                              # one class
./gradlew :claims-service:test --tests '*NegativeScenariosIntegrationTest.malformedJsonIs400'  # one method
./gradlew bootJar                                 # jars in <module>/build/libs
java -jar claims-service/build/libs/claims-service-0.1.0.jar   # run a built jar
```

In PowerShell, use `.\gradlew.bat`.

## 8. Backend API

The contracts are exported from the running services and live in each module's `src/main/resources/openapi/`.
They are also served live at `/v3/api-docs.yaml` and `/swagger-ui.html`. Request and response examples are in
[testing.md §5](testing.md#5-api-payloads-requests-and-responses).

| Role | Endpoints |
|---|---|
| Any signed-in user | `GET /api/me` |
| Claimant | `POST /api/claims` · `GET /api/claims` · `GET /api/claims/{id}` · `GET .../history` · `GET .../info-requests` · `POST .../info-requests/{rid}/response` · `POST/GET .../documents` · `GET .../documents/{docId}` · `GET /api/notifications` |
| Officer | `GET /api/staff/claims/queue` · `GET /api/staff/claims` · `GET /api/staff/officers` · `POST /api/staff/claims/{id}/assign \| info-requests \| assess \| approve \| reject \| settle \| reassign` |
| Manager | `GET /api/reports/summary \| exposure \| exposure/total \| workload \| performance \| sla-breaches` · read-only `GET /api/staff/**` · `POST .../reassign` |

Errors are RFC 7807 `application/problem+json`: `400` validation (with an `errors` list), `401`, `403`, `404`
(also for another claimant's claim), `405`, `409` lifecycle or concurrency conflict, `413` file too large, `415`
file type, `422` business rule, `500`.

## 9. HTTP Basic authentication

- The services are stateless HTTP Basic with roles `CLAIMANT`, `OFFICER` and `MANAGER` (`claims-security`). URL
  rules are deny-by-default per service, and record rules live in the service layer.
- Demo users: `tan@example.com`, `lee@example.com` (claimants), `officer-1`, `officer-2`, `manager-1`. They all use
  `APP_SECURITY_DEMO_PASSWORD`. If it is not set, each service generates a random password and logs it once.
  Real users can be configured under `app.security.users` (username, password, role).
- **In the frontend:** the sign-in page sends the credentials to `GET /api/me`. A 200 returns the username and
  roles; a 401 shows "Incorrect username or password". The credentials are then kept in `sessionStorage`, so they
  survive a refresh and are gone when the tab closes. `authInterceptor` adds `Authorization: Basic …` to every
  `/api` request and nothing else. On any later 401 the user is signed out and returned to sign-in with a return URL.
  A 403 shows a message and keeps the session. Credentials never appear in URLs, logs or error messages.
- The services send `WWW-Authenticate: Basic` on a 401. The dev proxy and nginx remove that header so browsers
  don't show their own login dialog over the app.
- **In production:** serve only over HTTPS (Basic sends the password on every request), put TLS on nginx or the load
  balancer, and preferably replace Basic with OIDC/JWT. Only `AuthService` and the interceptor would change.

## 10. Frontend-to-backend integration

- The browser calls relative `/api/...` URLs on its own origin. Routing by prefix:
  - development: `claims-web/proxy.conf.mjs` (override targets with `CLAIMS_URL`, `REPORTING_URL`, `NOTIFICATION_URL`)
  - Docker / production: `claims-web/nginx.conf` (upstreams are the compose service names)

  Keep the two in sync. No CORS is needed because the browser sees one origin.
- API clients in `src/app/core/api/` map one-to-one to the endpoints. Their models in `src/app/core/models.ts`
  mirror the DTOs, so a backend DTO change means updating `models.ts` and, for rules, `core/reference.ts`.
- `ApiError` normalises every failure. A 409 reloads the claim, and `LatestRequest` stops an older, slower response
  from overwriting a newer one.

## 11. Environment variables and configuration

| Variable / property | Where | Default | Purpose |
|---|---|---|---|
| `APP_SECURITY_DEMO_PASSWORD` | all services | random, logged once | password for the demo users |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | all services | in-memory H2, `sa`, empty | database |
| `KAFKA_BOOTSTRAP_SERVERS` | all services | `localhost:29092` | Kafka |
| `DOCUMENTS_DIR` | claims-service | system temp dir | document store |
| `app.documents.max-bytes` | claims-service | 5 MB | upload limit |
| `claims.outbox.enabled`, `claims.outbox.poll-interval-ms` | claims-service | `true`, `1000` | outbox relay |
| `reporting.sla.unassigned-hours`, `reporting.sla.open-days` | reporting-service | 24, 7 | SLA thresholds |
| `reporting.fx.rates-to-usd.*` | reporting-service | static table | indicative FX rates |
| `CLAIMS_URL`, `REPORTING_URL`, `NOTIFICATION_URL` | frontend dev proxy | `http://localhost:8081/8082/8083` | proxy targets |
| `DEMO_PASSWORD`, `E2E_BASE_URL` | Playwright | –, `http://localhost:4200` | e2e credentials and target |

`.env` (git-ignored) holds the Docker secrets; `.env.example` lists the keys.

## 12. Database and migrations

Each service owns one schema, managed by Flyway (`src/main/resources/db/migration`, `ddl-auto: none`):

- **claims**: `claims`, `info_requests`, `claim_history`, `claim_documents`, `outbox_events`
- **reporting**: `claim_view`
- **notifications**: `notifications`

Migrations run automatically at startup. Add a new `V<n>__description.sql` and never edit an applied one. Keep the
SQL portable between H2 (PostgreSQL mode) and PostgreSQL. In Docker, `docker/postgres-init.sql` creates the three
databases. Postgres is not published to the host; inspect it with
`docker compose exec postgres psql -U $DB_USERNAME -d claims`. `docker compose down -v` wipes all data.

## 13. Testing

| Suite | Command | Needs |
|---|---|---|
| Backend unit + integration (MockMvc, embedded Kafka) | `./gradlew test` | JDK only |
| Frontend unit + component (Vitest, jsdom) | `cd claims-web && npm test` | Node |
| Frontend coverage | `cd claims-web && npx ng test --watch=false --coverage` | Node |
| End-to-end (Playwright, real services) | `cd claims-web && DEMO_PASSWORD=... npm run e2e` | services + Kafka running |
| E2E against Docker | `E2E_BASE_URL=http://localhost:8080 DEMO_PASSWORD=... npm run e2e` | `docker compose up` |
| One e2e test | `npx playwright test -g "reassign"` | as above |
| API collection | `newman run docs/postman/claims-platform.postman_collection.json --working-dir docs/postman --env-var demoPassword=...` | services running |

The e2e suite is serial, adds data to the running services, and tolerates data left by earlier runs. Measured
results are in [test-report.md](test-report.md).

## 14. Code coverage reports

| Report | Location (generated, not committed) |
|---|---|
| Backend (JaCoCo HTML / XML) | `<service>/build/reports/jacoco/test/html/index.html`, `jacocoTestReport.xml` |
| Frontend (V8: HTML, lcov) | `claims-web/coverage/claims-web/index.html` |
| Playwright | `claims-web/playwright-report/index.html` (traces of failures in `test-results/`) |

## 15. Production build and deployment

```bash
cd claims-web && npx ng build             # → dist/claims-web/browser (hashed, minified)
./gradlew bootJar                          # → <service>/build/libs/*.jar
docker compose build                       # images: the three services + web (nginx)
```

Deployment checklist:
- TLS in front of nginx; set real users or, better, an identity provider (§9).
- Real PostgreSQL and Kafka endpoints via `DB_*` and `KAFKA_BOOTSTRAP_SERVERS`; secrets from a secret store, never
  from the repository.
- A persistent volume (or an S3/Blob implementation of `DocumentStorage`) for documents.
- nginx already sends `X-Content-Type-Options`, `X-Frame-Options` and `Referrer-Policy`, caches hashed assets for a
  year and never caches `index.html`; add a Content-Security-Policy for your domain.
- One instance of claims-service at a time (the outbox relay is a single poller; see Known limitations in
  [decisions-and-assumptions.md](decisions-and-assumptions.md)).

## 16. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Sign-in says "Incorrect username or password" with the right password | The services were started with different `APP_SECURITY_DEMO_PASSWORD` values, or none (random). Use one value for all three. |
| "Cannot reach the server" in the app | The service behind that prefix is not running, or the proxy targets are wrong (§10). Check `/actuator/health` on 8081-8083. |
| Reports or notifications don't change after an action | They are built from Kafka events, which takes about a second, so press Refresh. If they never update, Kafka is down: the outbox keeps the events and delivers them when it returns. |
| Browser shows its own login pop-up | You are calling a service port (8081…) directly instead of the app origin. Use :4200 or :8080. |
| `docker compose up` fails with "run scripts/init-env first" | `.env` is missing. Run `scripts/init-env`. |
| A container exits with code 137, or the build fails with `failed to solve: … EOF` | Docker ran out of memory (seen with about 3 GB while building images next to the running stack). Raise Docker's memory, or build images one at a time (`docker compose build web`) before `up`. |
| Port already in use | A local `bootRun` and the Docker stack are both running. Stop one. |
| Angular CLI errors on install | Node is too old. Angular 22 needs Node 22+. |
| e2e fails on the first test only | The services are not up yet, or `DEMO_PASSWORD` doesn't match. Open `playwright-report/index.html` for the trace. |
| Debugging an API call | Browser DevTools → Network shows the problem+json `detail` and `errors`; service logs record every error with its cause (500s only log details server-side). |

## 17. Requirements traceability

The feature-by-feature checklist (27 features: screen, API, and the test that verifies each) is in
[frontend.md → Feature coverage](frontend.md#feature-coverage), and the backend test catalogue is in
[testing.md §3](testing.md#3-test-catalogue-62-tests). The latest verified status of every suite is in
[test-report.md](test-report.md).
