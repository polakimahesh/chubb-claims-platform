# Approach, Challenges and Key Prompts

How the platform was built with Claude Code: the approach for the backend and the frontend, the challenges met and
how each was resolved, and the key prompts that drove the work.

- [1. Overall approach](#1-overall-approach)
- [2. Backend approach](#2-backend-approach)
- [3. Frontend approach](#3-frontend-approach)
- [4. Challenges and how they were resolved](#4-challenges-and-how-they-were-resolved)
- [5. How I worked with the AI](#5-how-i-worked-with-the-ai)
- [6. Key prompts](#6-key-prompts)

The complete, unedited prompt log is in [`prompts/`](../prompts/), and the step-by-step record of what was accepted,
challenged and overridden is in the [AI journal](../ai-journal/claude-journal.md).

---

## 1. Overall approach

```
 Understand the brief  ─►  Backend core  ─►  Harden backend  ─►  Frontend  ─►  Verify end to end  ─►  Document
   (decompose,             (lifecycle,       (security, errors,   (one Angular   (unit, e2e, a11y,      (architecture,
    decide stack)           REST, Kafka)      Docker, tests)       app, 3 roles)  mobile, Docker)        guides, journal)
```

| Principle | What it meant in practice |
|---|---|
| **Backend first**, as the brief advises | A tested, working backend before any UI, so the frontend was built against real APIs |
| **Decide, then record why** | Every choice (service split, outbox, Basic auth, proxy) is in [decisions-and-assumptions.md](decisions-and-assumptions.md) with the alternatives considered |
| **Analyse before code** | Each phase started with Claude reading the brief and the existing code and asking about anything ambiguous |
| **Verify, don't assume** | Nothing counted as done until a test proved it; a failure was traced to its cause before anything was fixed |
| **AI drives, I direct** | Claude generated most of the code; I set the direction, reviewed, challenged and overrode where needed |

---

## 2. Backend approach

1. **Decomposition.** Three actors (claimant, officer, manager) and one write aggregate (the claim), plus two
   downstream concerns (reporting and notifications) with very different load and failure profiles.
2. **Stack.** Java 17, Spring Boot 3.3, Gradle, PostgreSQL/H2, Kafka.
3. **Service split by profile, not by noun.** `claims-service` (transactional), `reporting-service` (read-heavy,
   eventually consistent) and `notification-service` (slow, failure-isolated). This made Kafka load-bearing:
   notifications were added later without changing claims-service.
4. **Rules in the domain.** The lifecycle lives in the `Claim` entity, so no controller can bypass it. State checks
   run before business rules (409 before 422).
5. **Reliable events.** A transactional outbox, events keyed by claim, full-state events with a version, idempotent
   consumers, retry and a dead-letter topic.
6. **Hardening.** Roles and record-level access, RFC 7807 errors everywhere, server-side validation, document
   checks, no secrets in the repository, Docker Compose, OpenAPI and Postman.
7. **Tests.** Unit, MockMvc integration and embedded-Kafka tests; JaCoCo coverage of about 95% per service.

---

## 3. Frontend approach

1. **Requirements first.** Claude read the brief and every controller, DTO, security rule and lifecycle rule, then
   listed every user-facing feature (27 in total) before writing any UI.
2. **Questions before building.** Scope (keep the tested backend), UI library (Angular Material), how to choose a
   reassignment target (a new officer-list endpoint), Node version (upgraded).
3. **Architecture.** One Angular 22 app with lazy-loaded areas per role, a shared claim page, `core/` for the API
   contract, authentication and the rule mirror, signals for state, and one origin through a proxy (no CORS).
4. **The backend stays authoritative.** The UI mirrors the lifecycle rules only to decide which buttons to show; the
   server still enforces every rule.
5. **Build, then integrate against the real services**, screen by screen.
6. **Verify everything.** 91 unit and component tests, 37 Playwright tests (workflows, accessibility in both themes,
   mobile), run against the dev server and the Docker production build.
7. **Design.** A token-based design system with light and dark themes, matched to the design language of a live
   insurer claims portal (measured from its production stylesheet), plus a submission confirmation with a reference
   number, as live portals do.

---

## 4. Challenges and how they were resolved

### Backend

| Challenge | Resolution |
|---|---|
| A new claim (application-assigned UUID) was treated as existing because `@Version` was a primitive `long` | Changed to `Long`, so new entities are detected correctly (caught in review before the first build) |
| Publishing to Kafka inside the request could lose events (dual write) | Transactional outbox plus an ordered relay |
| The topic was auto-created with one partition by an early consumer | Broker auto-create disabled; every service declares the topic with 3 partitions |
| An exception handler clashed with Spring's base handler | Rewritten on `ResponseEntityExceptionHandler`; RFC 7807 for every error, including 401 and 403 |
| A default demo password in code in the first version | First moved to a generated, git-ignored `.env` with random values; for the final hand-over, the demo users and the documented demo password (`Test@123`) moved into each service's `application.yml`, so anyone can run the app with one command |
| The OpenAPI export returned 401 (`/v3/api-docs.yaml` not public) | One shared public-endpoint list, plus a test |
| Postgres port 5432 reserved on Windows | Postgres no longer published to the host (also safer) |
| An oversized upload was accepted in tests (the servlet limit doesn't apply in MockMvc) | An explicit size check in `DocumentService` |
| Docker image builds killed for lack of memory (exit 137) | Images built one at a time; documented in troubleshooting |
| `OutboxRecoveryTest` failed intermittently | Root cause proven with a controlled experiment: a cached test context's relay shared the in-memory database. Fixed with test isolation; the same experiment then passed |

### Frontend

| Challenge | Resolution |
|---|---|
| `/api/me` (needed for sign-in) existed but was blocked by deny-by-default rules | A security rule and a test |
| No way to list officers for reassignment | A new `GET /api/staff/officers` endpoint, with a test |
| Three services on three ports, with no CORS | One origin: the dev proxy and nginx route by path |
| Browsers showed their own Basic-auth pop-up on 401 | The proxy drops `WWW-Authenticate`; the app has its own sign-in |
| Node 16 too old for Angular 22 | Node 24 LTS installed |
| The first draft of the action dialog was flawed (wrong dialog reference, muddled validators) | Rewritten after review, before the first build |
| Initial bundle over budget (650 kB) | Lazy-loaded shell; 400 kB |
| A slow, older list response could overwrite a newer filtered one | `LatestRequest` cancels stale requests; unit test added |
| nginx dropped security headers on static files (`add_header` inheritance) | Headers repeated per location |
| Accessibility scans found two defects (button name not matching its text; unnamed loading bars) | Both fixed; 8/8 axe scans pass in light and dark |
| A unitless `0` in theme tokens removed form-field padding | Changed to `0px` (found in screenshots) |
| End-to-end failures that were test bugs (races, ambiguous locators, leftover data) | Each traced with screenshots and API checks before fixing the test, never the app by guesswork |

---

## 5. How I worked with the AI

- **Accepted:** the service split by read/write profile, the transactional outbox, full-state events, the shared
  claim page, rule mirroring with the server as the authority, the one-origin proxy.
- **Challenged:** flawed first drafts (the entity version type, an unfinished service method, the dialog
  component), the e2e suite's assumptions about data, and claiming a test was "flaky" without a cause.
- **Overrode:** default passwords in configuration; adding images or illustrations; the direction of the visual design.
- **Verification habit:** every "done" was backed by a test run or a screenshot, and every failure was reproduced
  before it was fixed.

The detailed record is in the [AI journal](../ai-journal/claude-journal.md).

---

## 6. Key prompts

A selection of the prompts that shaped the work, quoted as given. Short follow-up messages (questions, small
corrections) are in the full log in [`prompts/`](../prompts/).

### Backend: architecture and implementation

> I'm thinking of using **Java Spring Boot with Gradle**. So, let's build the application using Gradle.
>
> Please follow all the steps and guidelines mentioned in the document. Make sure we follow the guidelines completely
> and implement **all the APIs that are expected or required**.
>
> If the guidelines allow us to use a **microservices architecture**, let's use microservices and build the
> application properly with a good architecture.
>
> If microservices are not required, we can build it as a single application and still implement the **Kafka producer
> and consumer** functionality within the application.
>
> But overall, let's build the project properly, following all the guidelines and best practices mentioned in the
> document.

### Backend: production quality

> Please make sure we complete the assigned task without missing even a single requirement or point from the
> assignment.
>
> The implementation must be complete and production-quality. Please don't implement only the basic functionality;
> review the existing code and architecture carefully and choose the best approach.
>
> The solution should be:
>
> * Fully implemented across all required services.
> * Efficient and properly optimized.
> * Scalable and maintainable.
> * Secure, especially for handling secrets and credentials.
> * Consistent with the existing project architecture and coding standards.
> * Properly tested and verified.
> * Free from unnecessary duplicate logic or configuration.
> * Designed so that existing functionality and APIs are not broken.
>
> Please review the complete requirement, existing code, configuration, and service-to-service flows before making
> changes. If there is a better or more efficient approach, use it and explain the recommendation.

### Frontend: a structured, step-by-step build

> Now, our task is to build a modern, user-friendly frontend using the latest version of Angular, with a clean
> design, an intuitive user interface, and a professional look and feel.
>
> We need to thoroughly review the entire documentation, the existing backend implementation, and all the available
> features before starting the new implementation. We must carefully analyze all of them and ensure that no
> functionality is missed during the frontend development.
>
> Our approach should be step by step:
>
> 1. **Review the documentation:** Understand all the requirements for both the frontend and backend.
> 2. **Analyze the existing backend:** Thoroughly inspect the implementation, including all features, business logic,
>    APIs, validations, and workflows.
> 3. **Plan the architecture:** Define a proper frontend and backend architecture while preserving the existing
>    business logic and functionality.
> 4. **Implement the frontend:** Build a modern, responsive, user-friendly application using the latest version of
>    Angular, with a professional design and a consistent UI/UX.
> 5. **Integrate the frontend and backend:** Connect all Angular screens, forms, components, and workflows to the
>    existing backend APIs.
> 6. **Validate every feature:** Verify that all backend features are properly represented and functional in the
>    frontend, with no missing requirements or business logic.
> 7. **Test the complete application:** Perform end-to-end validation to ensure that everything works correctly and
>    consistently.

### Full stack: requirements, testing and documentation

> We must ensure that **100% of the project requirements are implemented without missing even a single feature or
> detail**. The application must be highly user-friendly, professionally designed, modern, responsive, and reliable.
>
> - Use the latest stable version of Angular. Follow proper Angular architecture and coding standards. Provide clear
>   error messages, loading indicators, success notifications, and appropriate empty states.
> - Preserve all existing business logic, API contracts and HTTP Basic Authentication. Configure Angular HTTP
>   interceptors appropriately for authentication and error handling.
> - Testing must cover all business features, frontend component and service tests, backend unit and integration
>   tests, authentication and authorization, API integration and error handling, form validations, positive,
>   negative, boundary and edge cases, end-to-end workflows, and regression testing.
> - Provide accurate test coverage for both frontend and backend. **Generate these figures from actual test execution
>   rather than estimating or inventing percentages.**
> - **No requirement should be marked complete without verification.**

### Frontend: design quality

> Please fill all the gaps; it should be completed 100%. Use production-ready styling on the frontend, add light and
> dark themes, and use proper frontend styles for a production-ready application.

> Compare the application with a real, live reference application so that our design closely matches its overall
> look, layout, structure and user experience, while maintaining a clean, modern and professional appearance. Do not
> add images unless they are genuinely required. All features, buttons, forms, validations, navigation and workflows
> must work correctly, and verify the final result against the reference from both design and functionality
> perspectives.
