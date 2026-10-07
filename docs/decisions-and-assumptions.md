# Decisions and Assumptions

## Assumptions

- The brief says "six markets" without naming them. Assumed `SG, HK, MY, TH, ID, AU` (`Market` enum, one place to change).
- Claim amounts carry an ISO-4217 currency; amounts in different currencies are **never summed** (no FX service).
- Authentication and authorisation are out of scope. Staff are identified by an `X-Officer-Id` header; claimants look
  up claims by id or e-mail. A real deployment would put OIDC/JWT in front and derive identity from the token.
- Any officer may pick up a `SUBMITTED` claim; once assigned, only that officer may act on it (reassignment is not built).
- A claim is "outstanding" until `SETTLED` or `REJECTED`; `APPROVED` is still liability.
- Exposure = assessed amount if one exists, otherwise the claimant's estimate.
- All timestamps are stored and returned in UTC.
- Documents/attachments are out of scope; information requests are text only.

## Decisions

| Decision | Choice | Alternative considered |
|---|---|---|
| Service split | Two services by read/write profile | One modular monolith (simpler, but the brief asks what drives the split and wants Kafka used meaningfully); four+ services by noun (distributed transactions with no benefit) |
| Events | Transactional outbox + polling relay | Publish inside the request (dual write can lose events); CDC/Debezium (more infrastructure) |
| Event shape | Full state + version (event-carried state transfer) | Thin events (consumer would have to call back); delta events (order-sensitive) |
| Idempotency | Compare claim `version` | Processed-event-id table (extra table, doesn't handle reordering) |
| Concurrency | JPA `@Version` optimistic lock → `409` | Pessimistic locks (hold DB locks during human workflows) |
| Business rules | In the `Claim` entity | Rules in the service layer (easier to bypass) |
| Reports | Pre-aggregated reads from own read model | Querying claims-service DB directly (couples schemas, adds load) |
| DB | PostgreSQL in Docker, H2 default for zero-setup | H2 only (not representative); Postgres only (needs Docker for every run/test) |
| Migrations | Flyway, SQL portable to both databases | Hibernate `ddl-auto` (no history, not production-like) |
| Errors | RFC 7807 `ProblemDetail` | Custom error body |
| Topic | `claims.events`, 3 partitions, key = claimId | One partition (no scaling); key by officer (breaks per-claim ordering) |

## Known Limitations

- Performance report aggregates closed claims in memory; at scale use SQL window/aggregate functions or a pre-computed table.
- The outbox relay is a single poller. Multiple instances would need `FOR UPDATE SKIP LOCKED` or a leader.
- Published outbox rows are never purged.
- Reports are eventually consistent (typically about a second behind).
- No pagination on report endpoints (result sets are small: one row per market/type/currency or officer).
- Verified: `docker compose up --build` (Postgres + Kafka + both services) was run end to end. Claims submitted
  through the REST API appeared in the reporting-service reports via Kafka (exposure, workload, performance, summary).
  The Kafka transport is also covered by embedded-broker tests: outbox relay (claims-service), and consumer, stale-event
  handling and poison-message handling (reporting-service).
- Kafka topics are created by the services (`NewTopic` beans, 3 partitions); broker auto-create is disabled in
  docker-compose so a consumer cannot auto-create the topic with a single partition first.
- Do not run the Docker stack and local `bootRun` copies of the same service together: they clash on ports and
  share the `reporting-service` consumer group, so events are split between instances.
- Not built (scope): authentication/authorisation, push notifications to claimants (decisions are visible through
  the tracking API; a notification consumer on `claims.events` is the natural next step), document attachments.

## Error handling

Each service has one `GlobalExceptionHandler` (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`), so
**every** failure is an RFC 7807 `application/problem+json` response with a stable shape.

| Case | Status |
|---|---|
| Malformed JSON, empty body, unknown enum, bad UUID/param type, missing/blank required header or parameter, bean-validation failures (with `errors` list) | 400 |
| Unknown claim or route | 404 |
| Wrong HTTP method | 405 |
| Invalid lifecycle transition, acting on closed/unassigned claim, concurrent modification (optimistic lock), data-integrity conflict | 409 |
| Business rule: approve without assessment, acting on a claim assigned to someone else, info request not on this claim / already answered | 422 |
| Anything unexpected | 500 with a generic message (details only in the log, never in the response) |

Order of checks on `approve`: lifecycle state first (409), then the assessment rule (422). Paging parameters are clamped
(page >= 0, 1 <= size <= 100) rather than rejected.
