# Decisions and Assumptions

## Assumptions

- The brief says "six markets" without naming them. Assumed `SG, HK, MY, TH, ID, AU`, each settling in its own
  currency (SGD, HKD, MYR, THB, IDR, AUD). A claim must be lodged in its market's currency (`Market` enum is the one place to change).
- Claimants are identified by their e-mail address, which is also their login.
- Any officer may pick up a `SUBMITTED` claim; once assigned, only that officer acts on it. The officer or a manager
  can hand it to another officer while it is in review.
- Managers have read access to staff views and may reassign, but do not decide claims themselves.
- A claim is "outstanding" until `SETTLED` or `REJECTED`; `APPROVED` is still liability.
- Exposure = assessed amount if one exists, otherwise the claimant's estimate.
- Claims waiting for the claimant (`INFO_REQUESTED`) do not count against the officer's SLA.
- Documents are PDF, PNG or JPEG up to 5 MB, at most 20 per claim, and only while the claim is open.
- All timestamps are stored and returned in UTC.

## Decisions

| Decision | Choice | Alternative considered |
|---|---|---|
| Service split | Three services by read/write profile and failure domain | One modular monolith (simpler, but the brief asks what drives the split and wants Kafka used meaningfully); a service per noun (distributed transactions for no benefit) |
| Events | Transactional outbox + polling relay | Publish inside the request (dual write can lose events); CDC/Debezium (more infrastructure) |
| Event shape | Full state + version + claimant contact + note (event-carried state transfer) | Thin events (consumers would have to call back into claims-service) |
| Idempotency | Reporting: compare claim `version`. Notifications: unique event id | Processed-event table for reporting (does not handle reordering) |
| Concurrency | JPA `@Version` optimistic lock → `409` | Pessimistic locks (hold DB locks during human workflows) |
| Business rules | In the `Claim` entity; state checked before business rules | Rules in the service layer (easier to bypass) |
| Authentication | HTTP Basic + roles from a shared module; record-level checks in services | OIDC/JWT (right for production, but needs an identity provider for a local demo) |
| Credentials | Nothing in the repo: `.env` (git-ignored, random values via `scripts/init-env`) or a random password generated at startup | Default passwords in config (convenient but a committed secret) |
| Other claimants' claims | Answer `404`, not `403` | `403` (confirms the claim exists) |
| Documents | Local filesystem behind `DocumentStorage`; server-generated keys; content sniffed against the declared type | Storing bytes in the DB (bloats the transactional store); trusting `Content-Type` (spoofable) |
| Notifications | Separate consumer service; `NotificationSender` interface with a log-only e-mail implementation | Sending e-mail from claims-service (a mail outage would fail claim decisions) |
| Reports | Pre-aggregated in SQL from each service's own read model | Querying claims-service's DB (couples schemas, adds load); in-memory aggregation (replaced, see journal) |
| FX | `FxRateProvider` with static configured rates, labelled "not live" in every response | Calling a live rate API (external dependency, keys) |
| SLA | Computed on demand from the read model with configurable thresholds | Scheduled timers per claim (more moving parts for the same report) |
| DB | PostgreSQL in Docker, H2 default for zero-setup local runs and tests | H2 only (not representative) |
| Migrations | Flyway, SQL portable to both databases | Hibernate `ddl-auto` (no history) |
| Errors | RFC 7807 `ProblemDetail` everywhere, including 401/403 | Custom error body |
| Topic | `claims.events`, 3 partitions, key = claimId; declared by every service; broker auto-create off | Auto-created topics (a consumer created it with 1 partition during testing, see journal) |
| Containers | Multi-stage build, non-root user, Postgres not published to the host | Root user, all ports published |

## Error handling

Each service has one `GlobalExceptionHandler` (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) and
the shared security module renders 401/403, so **every** failure is an RFC 7807 `application/problem+json` response.

| Case | Status |
|---|---|
| Malformed JSON, empty body, unknown enum, bad UUID/param type, missing required parameter, bean-validation failures (with `errors` list), currency not matching the market, empty upload, unsupported report currency | 400 |
| Missing or wrong credentials | 401 (with `WWW-Authenticate: Basic`) |
| Authenticated but not allowed (role, other officer's workload, unmapped route) | 403 |
| Unknown claim/document, or a claim that belongs to another claimant | 404 |
| Wrong HTTP method | 405 |
| Invalid lifecycle transition, acting on a closed/unassigned claim, concurrent modification, data-integrity conflict | 409 |
| Document larger than 5 MB | 413 |
| Document type not PDF/PNG/JPEG, or content not matching the declared type | 415 |
| Business rule: approve without assessment, acting on a claim assigned to someone else, info request not on this claim / already answered, document limit reached | 422 |
| Anything unexpected | 500 with a generic message (details only in the log) |

Paging parameters are clamped (page >= 0, 1 <= size <= 100) rather than rejected.

## Verification

- `./gradlew test`: unit, MockMvc integration and embedded-Kafka tests for all three services.
- `docker compose up --build` run end to end, then the Postman collection run headless with newman against the
  containers: all requests and assertions passed (claimant flow, documents, reassignment, reports via Kafka,
  notifications via Kafka, authN/authZ and error cases). See ai-journal for the exact run.
- OpenAPI contracts were exported from the running services after fixing a bug the first export exposed (`/v3/api-docs.yaml` was not public; now covered by a test). The contract served by the rebuilt Docker image is byte-identical to the committed file.

## Known Limitations

- Demo users are in memory (configurable via `app.security.users`); production should use an identity provider.
- BCrypt verification on every Basic-auth request costs CPU; token-based auth (JWT) removes that in production.
- FX rates are static and indicative; the e-mail channel only logs (masked address).
- The outbox relay is a single poller. Multiple instances would need `FOR UPDATE SKIP LOCKED` or leader election;
  published outbox rows are never purged.
- Reports and notifications are eventually consistent (typically about a second behind).
- Both consumers dead-letter to the same `claims.events.DLT` topic (the original consumer group is in the record headers).
- Do not run Docker and local `bootRun` copies of the same service together: they clash on ports and share consumer groups.
