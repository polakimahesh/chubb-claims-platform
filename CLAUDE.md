# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Chubb APAC take-home: a motor and property claims platform. It has three Spring Boot services and one Angular frontend
(`claims-web`, see `docs/frontend.md`). The original brief and the
repository guidelines are in `assessment-brief/`. The brief is deliberately underspecified, and the design decisions
are part of what is assessed. They are recorded in `docs/decisions-and-assumptions.md`. Keep that file, `README.md`
and `ai-journal/claude-journal.md` current when you change behaviour or architecture, and add the prompts you were
given to `prompts/`.

## Commands

Java 17, Gradle wrapper (no global Gradle needed). Run from the repository root. In PowerShell use `.\gradlew.bat`.

```bash
./gradlew build                                   # compile + all tests + jars
./gradlew test                                    # all tests (H2 + Flyway + embedded Kafka; no Docker needed)
./gradlew :claims-service:test --tests '*ClaimLifecycleTest'                                  # one class
./gradlew :claims-service:test --tests '*NegativeScenariosIntegrationTest.malformedJsonIs400'  # one method
./gradlew :claims-service:bootRun        # :8081 (also :reporting-service 8082, :notification-service 8083)
docker compose up --build                # full stack on :8080; demo users sign in with Test@123
node docs/postman/generate-collection.js docs/postman/claims-platform.postman_collection.json   # regenerate Postman
newman run docs/postman/claims-platform.postman_collection.json --working-dir docs/postman
```

Frontend (Node 22+, run inside `claims-web/`):

```bash
npm start                                   # dev server :4200; proxy.conf.mjs routes /api to 8081/8082/8083
npm test                                    # Vitest unit tests (ng test --watch=false)
npx ng build                                # production build
npm run e2e                                 # Playwright e2e; needs the three services + Kafka running
npx playwright test -g "reassign"           # one e2e test by name
```

There is no lint task configured. Swagger UI is at `/swagger-ui.html` on each service. Docker serves the web app
on :8080 (nginx, `claims-web/nginx.conf`).

## Architecture

Five Gradle modules:

- `claims-events`: plain-Java Kafka contract (`ClaimEvent`, `ClaimStatus`, `Market` with its currency, `ClaimType`,
  `ClaimEventType`). It has no framework dependency.
- `claims-security`: shared HTTP Basic auth. It provides `PlatformSecurityConfig` (users, BCrypt, the
  `PUBLIC_ENDPOINTS` list, `baseline(http)`), `Actor` (the caller's username and roles) and RFC 7807 401/403 handlers.
  Each service has its own `config/SecurityConfig` holding its URL role rules, with deny-by-default.
- `claims-service` (8081): the **system of record**. Layers are controller, service, repository, entity, dto,
  exception, config, messaging and storage.
- `reporting-service` (8082): the **read side**. A Kafka listener builds `claim_view`, and SQL-aggregated reports
  cover exposure, the FX total, workload, performance and SLA.
- `notification-service` (8083): consumes the same topic and records and sends claimant e-mails. The sender is a
  log-only implementation of `NotificationSender`.

Cross-file behaviour that is easy to miss:

- **Lifecycle rules live in the `Claim` entity** (`ALLOWED` map, `requireTransition`, `reassignTo`). The state check
  runs before business rules, so acting on a closed or unassigned claim gives 409 before any 422. `ClaimService`
  orchestrates and enforces **record-level access**:
  - `loadVisible` returns 404 to a claimant asking for someone else's claim
  - `requireAssignedTo` is the only-the-assigned-officer rule
  - only a manager or the current officer can reassign

  URL role rules are in `SecurityConfig`. Identity always comes from `Authentication` → `Actor`, never from the
  request body or a header.
- **Transactional outbox.** `ClaimService.recordChange` flushes the claim (bumping `@Version`), writes
  `claim_history` and calls `OutboxService.enqueue` (with the history note), all in one transaction.
  `OutboxPublisher` relays the events to `claims.events`, keyed by claim id, in order. Delivery is at-least-once.
  Set `claims.outbox.enabled=false` to turn the relay off, as most tests do.
- **Events carry the full claim state** plus its `version`, the claimant's e-mail and name, and a `note`. Reporting
  applies an event only when its version is newer than the stored one. Notifications are idempotent through a unique
  `event_id`. Keep `version` monotonic, and treat event field changes as contract changes for both consumers. If you
  change the `ClaimEvent` constructor, update the test fixtures in all three services.
- Consumers retry 3 times, then send the record to `claims.events.DLT`. Every service declares the topics through
  `NewTopic` beans, and broker auto-create is disabled in docker-compose.
- **Documents:** `DocumentService` checks the size limit itself (`app.documents.max-bytes`, because MockMvc
  bypasses the multipart limit) and sniffs the magic bytes against the declared type. File names are stored only as
  sanitised metadata. The bytes go to `DocumentStorage` under server-generated keys.
- **Credentials** are demo-only, for local development and assessment.
  - Each service's `src/main/resources/application.yml` holds `app.security.demo-password` (`Test@123`) and the
    `app.security.users` list. Keep `demo-password` the same in all three services.
  - The frontend shows the demo accounts from `claims-web/public/config/demo-accounts.json`, which the e2e tests and
    the Postman generator also read.
  - Tests use `src/test/resources/application.properties` (a test-only password) and `TestAuth` in the claims tests.
- **Schema** is owned by Flyway (`db/migration`, `ddl-auto: none`). Keep the SQL portable between H2 (PostgreSQL mode)
  and PostgreSQL.
- The OpenAPI YAML under `src/main/resources/openapi/` is exported from the running services
  (`/v3/api-docs.yaml`). Re-export it after changing endpoints. The Postman JSON is generated by
  `docs/postman/generate-collection.js`, so edit the generator rather than the JSON.

### Frontend (`claims-web`)

- Angular 22, standalone components, signals, zoneless, Angular Material 3. Features in `src/app/features/<area>`;
  API clients, models, auth and rules in `src/app/core`.
- **The backend stays authoritative.** `core/reference.ts` `staffActions()` mirrors the `Claim` entity and
  `ClaimService` rules only to decide which buttons to show. Form validators mirror `ClaimRequests`. If you change a
  backend rule or DTO, update `core/models.ts` and `core/reference.ts` too, and the e2e suite will catch drift.
- The browser sees one origin: `/api/reports` → 8082, `/api/notifications` → 8083, other `/api` → 8081. This lives in
  both `proxy.conf.mjs` (dev) and `nginx.conf` (Docker), so keep them in sync. Both drop `WWW-Authenticate` so
  browsers don't show their own Basic-auth dialog.
- Auth is HTTP Basic with the credentials in `sessionStorage` (`AuthService`). `authInterceptor` adds them, maps
  every failure to `ApiError` (RFC 7807 `detail` + `errors`), and signs out on 401.
- The e2e tests (`e2e/claims.spec.ts`) are serial and write to the running services. They must tolerate data left by
  earlier runs, so measure aggregates before asserting.

## Repo conventions (from the assessment guidelines)

- Meaningful, intent-describing commit messages, ending with the Co-Authored-By trailer.
- Keep real prompts in `prompts/` and an honest running log in `ai-journal/claude-journal.md`. Do not fabricate history.
- Never commit real credentials. The only credentials in the repo are the documented demo users in
  `application.yml` (local and assessment use only).
- Don't run the Docker stack and local `bootRun` copies of the same service together. They clash on ports and share
  Kafka consumer groups.
