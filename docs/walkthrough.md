# Walkthrough Notes

Prepared for the 30–60 minute walkthrough in the format the brief sets out.

## Session plan

| Segment (brief) | Planned | Content |
|---|---|---|
| My presentation: architecture, decisions, demo | 18 min | outline below |
| Panel Q&A: "why not X?", trade-offs | 12–15 min | prepared answers below |
| What I would do with more time | 8–10 min | list below |
| My questions | 5 min | questions below |
| **Total** | **about 45 min** | leaves room within the 60-minute maximum |

## Requirements of the brief and where they are met

| The brief asks for | Where it is |
|---|---|
| Claimants: report an incident, track the claim, provide more information when asked, receive decisions | Report-an-incident wizard, My claims, claim page (tracker, timeline, information requests, documents), Notifications |
| Claims staff: pick up claims, review and assess, progress to settlement or rejection, see the team's workload and performance | Intake queue, claim page actions (pick up, request info, assess, approve, reject, settle, reassign), My workload; managers: Dashboard, Officer workload, Team performance, SLA breaches |
| Both user types in the same Angular application | One app (`claims-web`), role-guarded lazy areas, navigation built from the signed-in role |
| Angular frontend; C#/.NET or Java/Spring Boot backend | Angular 22; Java 17 / Spring Boot 3.3 (Gradle) |
| REST and Kafka, with a reason for each | REST for commands and queries that need an immediate answer; Kafka (`claims.events`) to fan claim changes out to reporting and notifications (docs/architecture.md, "REST vs Kafka") |
| Start with the backend, extend to the frontend | Backend built and tested first, then the frontend |
| Git history showing the process | Commits phase by phase, from scaffold to final verification |
| Working application that starts locally | `docker compose up --build` → http://localhost:8080, sign in as `officer-1` / `Test@123` |
| AI working journal | `ai-journal/claude-journal.md` (accepted, challenged and overridden), prompts in `prompts/` |
| Supporting documentation | README, `docs/` (architecture, decisions, frontend, developer guide, testing, test report) |

### The brief's design questions

- **Core entities and how they relate.** A `Claim` aggregate owns its `InfoRequest`s, `ClaimHistory` (audit
  timeline) and `ClaimDocument`s. `ClaimEvent` is the integration contract. Reporting keeps a `claim_view` read model;
  notification keeps a `notifications` log.
- **One service or more, and why.** Three, split by read/write profile and failure domain rather than by noun:
  - claims: transactional, strict rules
  - reporting: read-heavy, can be stale
  - notification: slow and failure-prone e-mail that must never block a decision

  Adding notifications needed no change to claims-service.
- **What is synchronous, what is event-driven.**
  - Synchronous: everything a user waits on (submit, assess, approve, upload, and reads of the system of record).
  - Event-driven: everything downstream of a change (reports, e-mails), through a transactional outbox, so no event
    is lost or sent for a rolled-back change.
- **Two user types from one Angular codebase.** Sign-in learns the roles from `GET /api/me`, and route guards and
  navigation follow them. One claim page shows claimant tasks (answer, upload) or staff actions, chosen by
  `staffActions()`, which mirrors the entity rules. The backend still enforces every rule; the UI only decides what
  to show.
- **The contract between frontend and backend, and who defines it.** The backend owns it: OpenAPI exported from the
  running services (`*/src/main/resources/openapi`) and RFC 7807 errors. The frontend's typed models
  (`core/models.ts`) and API clients mirror it. Unit tests pin every endpoint, method and body, and the end-to-end
  suite catches drift.
- **The async nature of claims processing across both tiers.** Commands return the updated claim straight away.
  Reports and notifications are about a second behind, so report pages show when they were loaded and have Refresh.
  A 409 (state conflict or concurrent update) reloads the claim, and a slow older response never overwrites a newer
  one (`LatestRequest`).
- **What a claims officer needs to manage their workload.** An oldest-first intake queue with market and type
  filters and one-click pick-up; their own claims filtered by status; on each claim only the actions its state
  allows (approve is blocked until assessed); and a clear banner when a claim is waiting on the claimant.
- **What the system needs to know about outstanding liability exposure.** Open = not settled or rejected. Exposure
  per claim is the assessed amount, or the estimate before assessment. It is reported per market, type and currency,
  and as one converted total with clearly indicative FX rates. SLA breaches flag claims stuck in the queue or open too
  long.

## Presentation outline (18 min)

Running clock in brackets.

1. **The problem and how I decomposed it** (2 min, 0:00–2:00): three actors, one write aggregate, two downstream
   concerns.
2. **Architecture** (3 min, 2:00–5:00): the three services, REST vs Kafka, the outbox, idempotent consumers; one
   origin for the browser (dev proxy / nginx).
3. **Frontend design** (2 min, 5:00–7:00): one app with role-based areas, a shared claim page, rule mirroring with
   the server as the authority, errors and eventual consistency in the UI.
4. **Live demo** (7 min, 7:00–14:00) at http://localhost:8080. Start the stack and sign in before the session, so no
   time is lost on start-up:
   1. as `tan@example.com`: report an incident with a PDF, see the confirmation and reference number
   2. as `officer-1`: pick it up from the queue, request information
   3. as `tan@example.com`: see the e-mail notification and answer
   4. as `officer-1`: assess, approve (blocked until assessed), settle; reject and reassign another claim
   5. as `manager-1`: dashboard exposure (per currency and converted), team performance, SLA breaches
   6. switch to dark mode; show the app on a phone-sized window
5. **Quality** (2 min, 14:00–16:00): 64 backend, 91 frontend unit and 37 end-to-end tests (including accessibility
   and mobile), coverage figures, and the defects the tests caught (docs/test-report.md).
6. **Prioritisation and how I worked with AI** (2 min, 16:00–18:00):
   - backend first, as the brief advises, then the frontend
   - what I would cut first under pressure: visual polish, then the extra reports, then hardening
   - two examples from the AI journal where I challenged or overrode the AI's output

## Likely "why not X?" questions

Backend:

- *Why not a monolith?* It would be valid at this scale. The split isolates the read profile and the slow e-mail
  side effect, and makes Kafka load-bearing. The seams are clean: consumers only know the event contract.
- *Why not publish to Kafka directly in the request?* That is a dual write: a crash between commit and send loses
  the event.
- *Why not exactly-once?* At-least-once plus idempotent consumers gives the same effect with less machinery.
- *Why full-state events?* They are idempotent and order-tolerant, and new consumers need no history and no
  call-backs.
- *Why 404 for another claimant's claim?* A 403 would confirm the claim exists.
- *Why sniff document bytes?* `Content-Type` is client-controlled; a renamed executable must not be stored as a PDF.

Frontend:

- *Why HTTP Basic and credentials in sessionStorage?* It runs locally with no identity provider and clears when the
  tab closes. With OIDC only `AuthService` and the interceptor change. Production needs HTTPS.
- *Why a proxy instead of CORS?* The browser sees one origin, so no CORS on three services and no ports in the app.
  nginx does the same in production and also drops the Basic challenge so browsers don't show their own login pop-up.
- *Why mirror the business rules in the UI at all?* Only to show the right buttons. The server stays authoritative,
  and a wrong UI guess shows the server's message and reloads.
- *Why Angular Material?* Accessible components maintained by the Angular team; themed through tokens to the
  reference design language, with light and dark themes.
- *Why not push updates (WebSockets/SSE)?* It would be a new backend capability. Refresh-on-demand with an "updated
  at" time is honest about eventual consistency and was enough for the time box.
- *Why signals and not NgRx?* Each screen owns its small state. A global store would add ceremony without a shared
  state problem to solve.

## What I would do with more time

- OIDC/JWT against a real identity provider; per-market data access for staff
- Push updates to the UI (SSE) for queue, reports and notifications
- Generate the frontend models from the OpenAPI contract in CI, so contract drift fails the build
- Real e-mail/SMS behind `NotificationSender`; live FX behind `FxRateProvider`; S3/Blob plus virus scanning behind
  `DocumentStorage`
- Outbox: `SKIP LOCKED` for multiple instances, purge job, lag metrics and alerts; DLT replay tooling
- Schema registry for the event contract; Testcontainers (real Postgres and Kafka) in CI
- Self-hosted fonts, a Content-Security-Policy, and i18n for the six markets

## My questions for the panel

- How do claims teams split work across the six markets today: by market, by line of business, or both?
- Which parts of the platform already use Kafka, and is there a schema registry or event-contract standard?
- What identity provider would the claimant and staff portals use?
