# Project Structure

The folder layout and what each component is responsible for.

- [Repository layout](#repository-layout)
- [Frontend: `claims-web`](#frontend-claims-web)
- [Backend services](#backend-services)
- [Shared libraries](#shared-libraries)
- [Infrastructure, documentation and process](#infrastructure-documentation-and-process)

---

## Repository layout

```
chubb-claims-platform/
├── claims-web/                 Angular 22 frontend (single app for all user types)
├── claims-service/             Spring Boot · system of record · :8081
├── reporting-service/          Spring Boot · read model and manager reports · :8082
├── notification-service/       Spring Boot · claimant e-mail notifications · :8083
├── claims-events/              Java library · Kafka event contract
├── claims-security/            Java library · shared security
├── docker/                     PostgreSQL init script (creates the three databases)
├── docs/                       documentation (see README → Documentation)
│   └── postman/                Postman collection, its generator, a sample PDF
├── ai-journal/                 AI working journal (accepted / challenged / overridden)
├── prompts/                    the prompts given to Claude Code, by phase
├── assessment-brief/           the full-stack assessment brief and repository guidelines
├── build.gradle                Gradle root (Java 17 toolchain, Spring Boot BOM, JaCoCo)
├── settings.gradle             module list
├── Dockerfile                  multi-stage build for any backend service (SERVICE build arg)
├── docker-compose.yml          the full stack: Postgres, Kafka, three services, web
├── CLAUDE.md                   guidance for AI assistants working in this repo
└── README.md
```

---

## Frontend: `claims-web`

```
claims-web/
├── src/
│   ├── index.html                 shell page; applies the saved theme before the app boots
│   ├── main.ts                    bootstrap
│   ├── styles.scss                design system: Material 3 theme, --app-* tokens, light/dark
│   ├── styles/_theme-colors.scss  generated Material palette
│   ├── testing.ts                 unit-test helpers
│   └── app/
│       ├── app.routes.ts          lazy routes and role guards
│       ├── app.config.ts          providers: router, HttpClient + interceptor, date adapter
│       ├── core/                  non-visual building blocks
│       ├── layout/                application shell
│       ├── shared/                reusable UI components and pipes
│       └── features/              one folder per area of the app
├── e2e/                           Playwright end-to-end, accessibility and mobile tests
├── public/                        favicon
│   └── config/demo-accounts.json  demo accounts shown on the sign-in page
├── proxy.conf.mjs                 dev-server proxy: /api → the three services
├── nginx.conf                     production server: SPA + /api routing + security headers
├── Dockerfile                     Node build → nginx runtime
├── playwright.config.ts           desktop and mobile projects
├── angular.json · tsconfig*.json · package.json
```

| Folder | Responsibility |
|---|---|
| `core/models.ts` | TypeScript models that mirror the backend DTOs (the API contract on the client side) |
| `core/api/` | `ClaimsApi`, `ReportsApi`, `NotificationsApi`: one method per backend endpoint |
| `core/auth.service.ts` | Sign-in through `GET /api/me`, the session (HTTP Basic credentials in `sessionStorage`), roles, sign-out |
| `core/auth.interceptor.ts` | Adds credentials to `/api` calls, turns errors into `ApiError`, signs out on 401 |
| `core/guards.ts` | `authGuard`, `roleGuard`, `guestGuard`, `homeRedirect` |
| `core/reference.ts` | Markets and currencies, status labels, document limits, and `staffActions()` (the lifecycle-rule mirror) |
| `core/api-error.ts` | RFC 7807 problem → readable message and field errors |
| `core/latest.ts` | `LatestRequest`: cancels the in-flight request when a newer one starts |
| `core/theme.service.ts` | Light / dark / system theme, remembered per browser |
| `core/toast.service.ts` | Success and error notifications |
| `layout/shell` | Side navigation grouped by role, toolbar, theme and account menus, skip link |
| `shared/` | `ClaimTable`, `StatusChip`, `FilePicker`, `BarList`, `PageHeader`, `EmptyState`, pipes (money, market, hours, file size) |
| `features/login` | Sign-in page |
| `features/claimant` | My claims; Report an incident (3-step wizard with documents) |
| `features/claim` | The claim page for every role (tracker, confirmation, actions, information requests, documents, timeline) and the action dialog |
| `features/staff` | Intake queue (filters, pick-up); Workload (officer, or any officer for managers) |
| `features/manager` | Dashboard (summary, exposure, FX total), Team performance, SLA breaches |
| `features/notifications` | Notification inbox (claimants: their own; staff: all or filtered by claimant) |
| `e2e/` | `claims.spec.ts` (workflows), `accessibility.spec.ts` (axe), `mobile.spec.ts` (Pixel 7), `support.ts` |

Unit tests sit next to the code they test (`*.spec.ts`).

---

## Backend services

All three services use the same layering under `src/main/java/com/chubb/<service>/`.

| Package | Responsibility |
|---|---|
| `controller` | REST endpoints, request validation, OpenAPI annotations |
| `service` | Use cases, transactions, record-level authorisation |
| `entity` | JPA entities (the `Claim` aggregate holds the lifecycle rules) |
| `repository` | Spring Data JPA repositories and queries |
| `dto` | Request and response records |
| `exception` | Domain exceptions and one `GlobalExceptionHandler` (RFC 7807) |
| `config` | Security URL rules, Kafka topics, OpenAPI metadata, clock |
| `messaging` | Outbox relay (claims) or Kafka listeners (reporting, notification) |

Each service also has `src/main/resources/application.yml` (including the demo users under `app.security`), `db/migration/` (Flyway SQL) and `openapi/` (the exported
contract), plus `src/test/` with unit and integration tests.

### claims-service (:8081)

| Part | Responsibility |
|---|---|
| `ClaimController`, `DocumentController` | Claimant API: submit, list, track, history, information requests, documents |
| `StaffClaimController`, `StaffDirectoryController` | Staff API: queue, workload, assign, reassign, request info, assess, approve, reject, settle; officer list |
| `CurrentUserController` | `GET /api/me`: the caller's username and roles (frontend sign-in) |
| `ClaimService`, `DocumentService` | Use cases; ownership and assigned-officer rules; document type sniffing and limits |
| `Claim` entity | The lifecycle state machine and business rules |
| `OutboxService`, `OutboxPublisher` | Write events in the same transaction; relay them to Kafka in order |
| `DocumentStorage` | Where document bytes are kept (local files; swappable for S3/Blob) |

### reporting-service (:8082)

| Part | Responsibility |
|---|---|
| `ClaimEventListener` | Consumes `claims.events` (retry, then dead-letter topic) |
| `ClaimProjectionService` | Applies events to `claim_view` idempotently (newer version wins) |
| `ReportService`, `ReportController` | Summary, exposure, FX total, workload, performance, SLA breaches (manager only) |
| `FxRateProvider` | Indicative FX rates from configuration |

### notification-service (:8083)

| Part | Responsibility |
|---|---|
| `ClaimEventListener` | Consumes `claims.events` |
| `NotificationService`, `NotificationTemplates` | Which events notify the claimant, and the message text; idempotent per event |
| `NotificationSender` | Delivery channel (log-only e-mail; swappable) |
| `NotificationController` | Claimants: their own notifications; staff: all, or by claimant |

---

## Shared libraries

| Module | Responsibility |
|---|---|
| `claims-events` | `ClaimEvent` (the Kafka contract), `ClaimStatus`, `ClaimType`, `Market` (with its currency), `ClaimEventType`. Plain Java, no framework. |
| `claims-security` | `PlatformSecurityConfig` (HTTP Basic, BCrypt, public endpoints), `UserDirectory` (demo users and lookups), `Actor`, `Roles`, RFC 7807 401/403 handlers |

---

## Infrastructure, documentation and process

| Path | Responsibility |
|---|---|
| `docker-compose.yml` | Starts PostgreSQL, Kafka, the three services and the web app; no `.env` needed (local-only database login in the file) |
| `Dockerfile` (root) | Builds any backend service into a non-root JRE image |
| `claims-web/Dockerfile`, `nginx.conf` | Builds the Angular app and serves it with nginx |
| `docker/postgres-init.sql` | Creates the `claims`, `reporting` and `notifications` databases |
| `docs/` | Technology stack, architecture, this file, developer guide, frontend, testing, test report, decisions, walkthrough, approach |
| `ai-journal/claude-journal.md` | What was asked of the AI, accepted, challenged and overridden |
| `prompts/` | The prompts used, by phase |
