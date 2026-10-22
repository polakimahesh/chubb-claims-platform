# Technology Stack

Every technology used in the Claims Platform, with its version and what it is used for. Versions are those in
`claims-web/package.json`, `build.gradle` and `docker-compose.yml`.

- [Frontend](#frontend)
- [Backend](#backend)
- [Data and messaging](#data-and-messaging)
- [Testing and quality](#testing-and-quality)
- [Infrastructure and tooling](#infrastructure-and-tooling)
- [Brief requirements](#brief-requirements)

---

## Frontend

Located in `claims-web/`. One Angular application serves all three user types.

### Framework and language

| Technology | Version | Used for |
|---|---|---|
| **Angular** | 22.2 | Application framework: standalone components, **signals** for state, **zoneless** change detection, lazy-loaded routes, functional guards and interceptors, `@if` / `@for` control flow |
| **TypeScript** | 6.0 | Strictly typed code; models mirror the backend DTOs |
| **RxJS** | 7.8 | HTTP streams, cancelling stale requests (`LatestRequest`), `forkJoin` for parallel loads |
| **SCSS** | – | Styles; one global design system (`src/styles.scss`) and component styles |
| **Angular CLI / `@angular/build`** | 22.2 | Build (esbuild/Vite based), dev server with API proxy, test runner |

### UI and design

| Technology | Version | Used for |
|---|---|---|
| **Angular Material** | 22.2 | Material 3 components: tables, paginator, forms, select, datepicker, stepper, dialogs, menus, snack bars, sidenav, chips |
| **Angular CDK** | 22.2 | Responsive layout (`BreakpointObserver`) |
| Material 3 theming | – | Custom palette generated with `ng generate @angular/material:theme-color`; `theme-type: color-scheme` for **light and dark themes** |
| Design tokens | – | `--app-*` CSS variables for ink, surfaces, borders, shape and status colours (with `light-dark()`) |
| Fonts | – | **Lato** (body) and **Source Serif 4** (headings); **Material Symbols** icons (Google Fonts) |

### Application features (Angular)

| Feature | Implementation |
|---|---|
| Routing | Lazy-loaded feature routes with `authGuard`, `roleGuard`, `guestGuard`; route parameters bound to component inputs |
| HTTP | `HttpClient` + `authInterceptor`: adds HTTP Basic credentials to `/api` calls, maps RFC 7807 errors to `ApiError`, signs out on 401 |
| Forms | Typed reactive forms; validators mirror the backend's (`@NotBlank`, sizes, amount digits, past-or-present date) |
| State | Signals and `computed` per screen; `sessionStorage` for the session; `localStorage` for the theme choice |
| Accessibility | Skip link, visible focus, labelled controls, reduced-motion and high-contrast support |

### Serving

| Environment | How |
|---|---|
| Development | `ng serve` on :4200; `proxy.conf.mjs` routes `/api/*` to the three services |
| Production (Docker) | Multi-stage `Dockerfile` (Node 24 build → **nginx 1.27** unprivileged); `nginx.conf` serves the SPA and routes `/api/*`, with security headers and caching |

---

## Backend

Gradle multi-module project: three Spring Boot services and two shared libraries.

### Core

| Technology | Version | Used for |
|---|---|---|
| **Java** | 17 (Gradle toolchain) | Language and runtime; records for DTOs |
| **Spring Boot** | 3.3.5 | Service framework and dependency management (BOM) |
| **Spring Web MVC** | 6.1 (Boot 3.3) | REST controllers, multipart uploads, `ProblemDetail` (RFC 7807) errors |
| **Spring Data JPA / Hibernate** | Boot 3.3 | Persistence, optimistic locking (`@Version`) |
| **Bean Validation** | Boot 3.3 | Request validation (`@NotBlank`, `@Size`, `@Digits`, `@PastOrPresent`) |
| **Spring Security** | 6.3 (Boot 3.3) | Stateless HTTP Basic, BCrypt, deny-by-default URL rules, RFC 7807 401/403 |
| **Spring for Apache Kafka** | Boot 3.3 | Producer (outbox relay) and consumers (retry, dead-letter topic) |
| **Spring Boot Actuator** | Boot 3.3 | `/actuator/health` for Docker health checks |
| **Lombok** | Boot 3.3 | Boilerplate reduction in entities and services |

### Build

| Technology | Version | Used for |
|---|---|---|
| **Gradle** | 8.10.2 (wrapper) | Multi-module build, `bootJar`, test and coverage tasks |
| Spring Dependency Management plugin | 1.1.6 | Consistent dependency versions from the Spring Boot BOM |

### APIs

| Technology | Version | Used for |
|---|---|---|
| REST + JSON | – | All frontend ↔ backend communication |
| **springdoc-openapi** | 2.6.0 | OpenAPI 3 contracts (`/v3/api-docs.yaml`, committed under `src/main/resources/openapi/`) and **Swagger UI** |
| RFC 7807 Problem Details | – | One error format for every failure, including 401 and 403 |

### Services and libraries

| Module | Type | Port | Responsibility |
|---|---|---|---|
| `claims-service` | Spring Boot app | 8081 | System of record: claim lifecycle, claimant and staff APIs, documents, transactional outbox |
| `reporting-service` | Spring Boot app | 8082 | Kafka consumer, denormalised read model, manager reports (exposure, FX, workload, performance, SLA) |
| `notification-service` | Spring Boot app | 8083 | Kafka consumer, claimant e-mail notifications (idempotent) |
| `claims-events` | Java library | – | Kafka event contract (`ClaimEvent`) and enums; no framework dependency |
| `claims-security` | Java library | – | Shared security configuration, demo users, `UserDirectory`, RFC 7807 401/403 handlers |

---

## Data and messaging

| Technology | Version | Used for |
|---|---|---|
| **PostgreSQL** | 16 (Alpine image) | One database per service in Docker (`claims`, `reporting`, `notifications`) |
| **H2** | Boot 3.3 | In-memory database (PostgreSQL mode) for local runs and tests |
| **Flyway** | Boot 3.3 | Versioned schema migrations (`db/migration`), portable SQL |
| **Apache Kafka** | 3.8.0 (KRaft, no ZooKeeper) | Topic `claims.events` (3 partitions, keyed by claim id) and `claims.events.DLT` |
| Local file storage | – | Claim documents behind the `DocumentStorage` interface (volume in Docker) |

---

## Testing and quality

| Technology | Version | Used for |
|---|---|---|
| **JUnit 5**, AssertJ | Boot 3.3 | Backend unit and integration tests |
| **MockMvc**, Spring Security Test | Boot 3.3 | API tests with authentication |
| **Embedded Kafka** (`spring-kafka-test`) | Boot 3.3 | Outbox relay and consumers against a real broker |
| **JaCoCo** | Gradle plugin | Backend coverage (HTML and XML) |
| **Vitest** | 5.0 | Frontend unit and component tests via `ng test` (jsdom) |
| `@vitest/coverage-v8` | 5.0 | Frontend coverage |
| **Playwright** | 1.64 | End-to-end tests in Chromium (desktop and Pixel 7) against the real services |
| **axe-core** (`@axe-core/playwright`) | 4.14 | Automated WCAG 2.1 A/AA accessibility scans, light and dark |
| **Postman / newman** | newman 6 | API collection with assertions (51 requests, 93 assertions) |

---

## Infrastructure and tooling

| Technology | Version | Used for |
|---|---|---|
| **Docker / Docker Compose** | Docker 26 | Full stack with one command; multi-stage images, non-root containers |
| Gradle image `gradle:8.10.2-jdk17` / `eclipse-temurin:17-jre` | – | Build and runtime images for the services |
| `node:24-alpine` / `nginxinc/nginx-unprivileged:1.27-alpine` | – | Build and runtime images for the web app |
| **Node.js / npm** | 22+ (tested 24.20) / 11 | Frontend toolchain |
| **Claude Code** | – | AI-assisted development (see `ai-journal/` and `prompts/`) |

---

## Brief requirements

| The brief requires | Used |
|---|---|
| Frontend: Angular (any version) | **Angular 22.2** |
| Backend: C#/.NET or Java/Spring Boot | **Java 17 / Spring Boot 3.3** |
| Communication: REST/HTTP and Kafka | **REST** for user-facing commands and queries; **Kafka** for claim events to reporting and notifications |
| Everything else (database, caching, testing, API tooling, frontend architecture) | Chosen and justified in [decisions-and-assumptions.md](decisions-and-assumptions.md) |
