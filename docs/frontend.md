# Frontend (`claims-web`)

One Angular application serves all three user types. It is a client of the existing services: no business rule moved
into the browser, and the backend stays the authority on every rule.

## Stack

Angular 22 (standalone components, signals, zoneless change detection, lazy-loaded routes), Angular Material 3,
reactive forms, Vitest for unit tests, Playwright for end-to-end tests. Node 22+ is required.

## Running it

```bash
# backend first (see README): the three services on 8081-8083 with one APP_SECURITY_DEMO_PASSWORD, Kafka for events
cd claims-web
npm ci
npm start                 # http://localhost:4200, proxies /api to the services (proxy.conf.mjs)
npm test                  # Vitest unit tests
DEMO_PASSWORD=... npm run e2e                       # Playwright, against the running services
DEMO_PASSWORD=... npx playwright test -g "reassign"  # one end-to-end test by name
npx ng build              # production build into dist/
```

With Docker, `docker compose up --build` also builds the `web` image: the app is at http://localhost:8080, served by
nginx, which routes `/api` the same way the dev proxy does.

## How it is put together

```
src/app/
  core/          models (mirror the backend DTOs), reference data and rules, auth, interceptor, guards, API clients
    api/         ClaimsApi (claims-service), ReportsApi (reporting-service), NotificationsApi (notification-service)
  layout/        shell: role-based side navigation, toolbar, account menu
  shared/        claim table, status chip, bar list, file picker, page header, empty state, pipes
  features/
    login/       sign-in (verifies credentials with GET /api/me and learns the roles)
    claimant/    my claims, report-an-incident wizard
    claim/       claim page for every role + the action dialog
    staff/       intake queue, workload
    manager/     dashboard, team performance, SLA breaches
    notifications/
```

- **One origin.** The browser only talks to the app's own origin, and each API prefix is routed to its service:
  `/api/reports` → reporting (8082), `/api/notifications` → notification (8083), the rest → claims (8081). The
  routing is in the dev proxy (`proxy.conf.mjs`) and nginx (`nginx.conf`), so the services need no CORS and the app
  does not hard-code their ports.
- **Authentication.** The services use stateless HTTP Basic. Sign-in calls `GET /api/me`, which also returns the
  roles. The credentials are kept in `sessionStorage`, so they are cleared when the tab closes and survive a refresh.
  An interceptor adds them to every `/api` call and signs the user out on a 401. The proxy drops the
  `WWW-Authenticate: Basic` challenge so the browser does not open its own login dialog on top of the app.
- **Two user types, one codebase.** Routes are lazy-loaded per area and guarded by role. Navigation is built from the
  roles. The claim page is shared: claimants answer information requests and attach documents there, and staff get an
  action bar. The guards only hide screens a role cannot use. The backend's URL rules and record checks are still the
  real enforcement (a claimant opening another claimant's claim gets "not found" from the API).
- **Business rules are mirrored, not re-implemented.** `core/reference.ts` `staffActions()` decides which buttons to
  show from the same rules the `Claim` entity enforces. Form validators mirror the request records (sizes, required
  fields, amount digits, date not in the future, currency derived from the market). The file picker checks the
  `DocumentService` limits. If the UI gets something wrong, the server still refuses, and the UI shows its message.
- **Errors.** Every RFC 7807 response becomes an `ApiError` with the server's `detail` and its validation `errors`. A
  409 (lifecycle conflict or concurrent update) reloads the claim, so a stale screen corrects itself.
- **Eventual consistency.** Reports and notifications are built from Kafka events and trail claim changes by about a
  second. Report pages show when they were loaded and have a refresh button.

## Feature coverage

Every backend capability, the screen that exposes it, and the end-to-end test that drives it
(`e2e/claims.spec.ts`; unit tests in `src/app/**/*.spec.ts`).

| # | Feature (backend) | Screen | API | Verified by |
|---|---|---|---|---|
| 1 | Sign in, role detection | Sign in | `GET /api/me` | e2e: wrong password; each role lands on its own home |
| 2 | Submit a claim (validation, currency per market) | Report an incident (3-step wizard) | `POST /api/claims` | e2e: claimant reports an incident |
| 3 | List own claims, newest first | My claims | `GET /api/claims` | e2e: claim appears in list |
| 4 | Track a claim (detail) | Claim page | `GET /api/claims/{id}` | e2e: several |
| 5 | Status timeline / audit trail | Claim page → Timeline | `GET /api/claims/{id}/history` | e2e: timeline shows assessment note |
| 6 | Answer information requests (claim returns to review when all are answered) | Claim page → Information requests | `GET .../info-requests`, `POST .../info-requests/{rid}/response` | e2e: claimant answers |
| 7 | Upload documents (PDF/PNG/JPEG, 5 MB, 20, open claims only) | Wizard step 2, Claim page → Documents | `POST /api/claims/{id}/documents` | e2e: upload + type rejection + closed claim; unit: `fileProblem` |
| 8 | List / download documents (owner and staff) | Claim page → Documents | `GET .../documents`, `GET .../documents/{docId}` | e2e: document listed |
| 9 | Claimant notifications | Notifications | `GET /api/notifications` | e2e: info-request and settled e-mails appear |
| 10 | Intake queue, oldest first, market/type filters | Intake queue | `GET /api/staff/claims/queue` | e2e: market filter |
| 11 | Pick up a claim | Queue row action, Claim page | `POST .../assign` | e2e: pick up from queue and from claim page |
| 12 | Officer workload, status filter | My workload | `GET /api/staff/claims` | e2e: status filter |
| 13 | Request information | Claim page → Request info | `POST .../info-requests` | e2e |
| 14 | Assess | Claim page → Assess | `POST .../assess` | e2e |
| 15 | Approve (needs an assessment) | Claim page → Approve (disabled until assessed) | `POST .../approve` | e2e: blocked then approved; unit: `staffActions` |
| 16 | Reject with a reason (shown to the claimant) | Claim page → Reject | `POST .../reject` | e2e |
| 17 | Settle | Claim page → Settle | `POST .../settle` | e2e |
| 18 | Reassign own claim (in review only) | Claim page → Reassign (officer list) | `POST .../reassign`, `GET /api/staff/officers` | e2e |
| 19 | Manager: read-only queue and any officer's workload | Intake queue (no pick-up), Officer workload | `GET /api/staff/claims?officerId=` | e2e: manager views officer workload |
| 20 | Manager reassigns any claim, never decides | Claim page (Reassign only) | `POST .../reassign` | e2e; unit: `staffActions` |
| 21 | Summary: by status, open, backlog | Dashboard tiles + bars | `GET /api/reports/summary` | e2e |
| 22 | Exposure by market/type/currency, market filter | Dashboard table | `GET /api/reports/exposure` | e2e: new claim adds to exposure, market filter |
| 23 | Exposure total in one currency (indicative rates) | Dashboard tile + by-currency bars, "Convert to" | `GET /api/reports/exposure/total` | e2e: currency switch, rate note shown |
| 24 | Officer workload report | Team performance → Open workload | `GET /api/reports/workload` | e2e |
| 25 | Officer performance | Team performance → Performance | `GET /api/reports/performance` | e2e |
| 26 | SLA breaches | SLA breaches | `GET /api/reports/sla-breaches` | e2e (page and counters) |
| 27 | Staff view of notifications, by claimant | Notifications (e-mail filter) | `GET /api/notifications?claimantEmail=` | e2e |
| – | Record-level access (another claimant's claim is 404) | Claim page "not found" | – | e2e |
| – | Role-guarded screens, session survives refresh, sign-out | Guards, shell | – | e2e |

## Backend changes made for the frontend

- `GET /api/me` (`CurrentUserController`) existed uncommitted and was blocked by the deny-by-default rules; it now
  requires only authentication.
- `GET /api/staff/officers` lists the users with the OFFICER role (from a new `UserDirectory` bean in
  `claims-security`), so reassignment offers a list instead of a free-text id.

Both are covered by tests in `SecurityAndFeaturesIntegrationTest`. No existing endpoint or rule changed.

## Known limitations

- HTTP Basic credentials sit in `sessionStorage` for the tab's lifetime. With OIDC this becomes a short-lived token,
  and only `AuthService` changes.
- Reports and notifications are not pushed; they refresh on load or on demand. Server-sent events would remove the
  refresh button.
- The UI is English only, and amounts use the `en-SG` number format with the ISO currency code.
- The end-to-end suite needs the services running. It adds data to them and is written to tolerate data left by
  earlier runs.
