# Test Report

Figures come from actual runs on 2026-10-22 (Windows 11, JDK 17.0.2, Node 24.20). Nothing here is estimated. To
regenerate them, use the commands in [developer-guide.md §13–14](developer-guide.md#13-testing).

## Summary

| Suite | Tool | Tests | Passed | Failed | Skipped |
|---|---|---|---|---|---|
| Backend: claims-service | JUnit 5, MockMvc, embedded Kafka | 41 | 41 | 0 | 0 |
| Backend: reporting-service | JUnit 5, embedded Kafka | 15 | 15 | 0 | 0 |
| Backend: notification-service | JUnit 5, embedded Kafka | 8 | 8 | 0 | 0 |
| Frontend unit and component | Vitest (`ng test`) | 85 | 85 | 0 | 0 |
| End-to-end, dev server + local services (H2) | Playwright | 20 | 20 | 0 | 0 |
| End-to-end, Docker stack (nginx + PostgreSQL + Kafka) | Playwright | 20 | 20 | 0 | 0 |
| **Total** | | **189** | **189** | **0** | **0** |

The backend run was `./gradlew clean test` (BUILD SUCCESSFUL in 7 m 50 s). The 64 backend tests include the 62 from
before the frontend work and 2 new ones for `/api/me` and `/api/staff/officers`. The Postman collection
(`docs/postman`) was not re-run in this session; its last recorded run is in the AI journal (section 10).

## Backend coverage (JaCoCo)

| Service | Instructions | Branches | Lines | Methods | Classes |
|---|---|---|---|---|---|
| claims-service | 95.5% (2175/2278) | 81.2% (65/80) | 93.7% (373/398) | 97.0% (131/135) | 100% (40/40) |
| reporting-service | 97.6% (1117/1145) | 83.3% (30/36) | 97.6% (163/167) | 97.0% (64/66) | 100% (22/22) |
| notification-service | 96.9% (558/576) | 91.3% (21/23) | 93.9% (92/98) | 94.4% (34/36) | 100% (14/14) |

Reports: `<service>/build/reports/jacoco/test/html/index.html`.

## Frontend coverage (Vitest, V8)

**All files: statements 87.35% (2051/2348), branches 85.38% (631/739), functions 67.63% (186/275), lines 89.54%
(1165/1301).**

| Area | File | Statements | Branches | Functions | Lines |
|---|---|---|---|---|---|
| core/api | claims.api.ts, reports.api.ts, notifications.api.ts | 100% | 100% | 100% | 100% |
| core | auth.service.ts | 100% | 89.47% | 100% | 100% |
| core | auth.interceptor.ts, guards.ts, latest.ts | 100% | 100% | 100% | 100% |
| core | reference.ts (rule mirror) | 100% | 96.42% | 100% | 100% |
| core | api-error.ts | 93.75% | 87.5% | 100% | 100% |
| core | toast.service.ts | 100% | 85.71% | 100% | 100% |
| features/claimant | new-claim.ts | 97.87% | 90% | 100% | 97.56% |
| features/claimant | my-claims.ts | 83.33% | 100% | 57.14% | 80.64% |
| features/login | login.ts | 93.1% | 90% | 66.66% | 92% |
| features/manager | sla.ts | 99.24% | 100% | 90% | 100% |
| features/manager | team.ts | 96.96% | 91.66% | 66.66% | 95.29% |
| features/manager | dashboard.ts | 79.59% | 80% | 58.82% | 84.21% |
| features/staff | queue.ts | 84.15% | 100% | 50% | 88.88% |
| features/staff | workload.ts | 83.8% | 88.57% | 53.33% | 86.95% |
| features/claim | action-dialog.ts | 76.28% | 80.59% | 87.5% | 85.18% |
| features/claim | claim-detail.ts | 66.17% | 77.27% | 58.53% | 65.51% |
| features/notifications | notifications.ts | 54.54% | 75% | 38.46% | 67.21% |
| layout | shell.ts | 88% | 72.22% | 77.77% | 93.33% |
| shared | file-picker.ts / claim-table.ts / pipes.ts | 89.24% / 87.7% / 90.9% | 85.71% / 70% / 90.24% | 68.75% / 50% / 100% | 90.69% / 94.73% / 88.23% |
| shared | bar-list, empty-state, page-header, status-chip | 100% | 92–100% | 100% | 100% |

Report: `claims-web/coverage/claims-web/index.html` (`npx ng test --watch=false --coverage`). The total also counts
the test helper `src/testing.ts` and compiled templates (`*.html`).

### Areas covered less well by unit tests

These are covered by the end-to-end suite but have lower unit-test coverage:

- **claim-detail.ts (66%)**: the pick-up, request-info, assess, settle and reassign dialog handlers, document upload
  and download. Unit tests cover approve, reject, cancel, the 409 reload, the claimant answer and every role's
  visibility. The rest is exercised in the e2e suite (pick-up, request info, assess, settle, reassign ×2, upload).
- **notifications.ts (55%)**: paging and the panel icon. Filtering is unit-tested, and the e2e suite covers paging
  and display.
- **Functions at 68% overall**: mostly template event callbacks (`(page)`, `(click)`) and paging handlers that run
  only through user interaction in the e2e suite.

## What the suites cover

- **Business features**: all 27 features (your 25, split slightly differently). Each is mapped to a test in the
  [traceability table](frontend.md#feature-coverage).
- **HTTP Basic auth and authorisation**: backend 401/403 per role, record-level 404, wrong password. Frontend:
  credentials only on `/api`, UTF-8 encoding, 401 signs out, 403 keeps the session, guards per role. E2E: wrong
  password, role homes, claimant blocked from staff screens, session survives reload, sign-out.
- **Validation (boundary cases)**: amount `0` / `0.001` / `0.01` / 13 integer digits / 14 digits; text limits 200,
  1000, 2000, 4000; blank-only text; future incident date (backend); currency/market mismatch (backend); document
  type, empty file, 5 MB, 20 documents.
- **Error handling**: problem+json mapping (400 with field errors, 409, 413, 415, 422, 500, network down), 409
  reload, a stale request overwriting a newer one.
- **Regression**: the full backend suite re-run after the backend changes; the e2e suite run against the dev setup
  and the Docker stack.

## Defects found and fixed during this work

| Defect | Found by | Fix |
|---|---|---|
| `/api/me` blocked (403) by the deny-by-default rules, and the controller was uncommitted | code review | security rule + test |
| Browser Basic-auth pop-up on 401 | analysis | proxy and nginx drop `WWW-Authenticate` |
| A slow, older list response could overwrite a newer filtered one | e2e on Docker | `LatestRequest` on every list/report load + unit test |
| nginx security headers missing on static files (`add_header` inheritance) | review | headers repeated per location |
| `OutboxRecoveryTest` intermittently failed. Root cause: a cached test context's outbox relay (200 ms poll, working embedded Kafka) shared the in-memory H2 database and published the test's "stuck" events. | full backend run; proven by widening the race window (failed every time) | the test gets its own database; the Kafka test context is closed after its class (`@DirtiesContext`); the same experiment then passes |

## Known issues and limitations

- The e2e suite writes to the running services' databases (it does not reset them). It tolerates existing data, but a
  clean environment is the most reproducible.
- With about 3 GB of Docker memory, building the web image while the full stack runs can get a container killed
  (exit 137). See troubleshooting in the developer guide.
- Credentials are HTTP Basic in `sessionStorage`. Use HTTPS in production, and preferably OIDC.
- Reports and notifications are eventually consistent (about 1 s) and refresh on demand, not by push.
