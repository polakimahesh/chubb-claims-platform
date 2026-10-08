# Testing Guide

How the platform is tested, how to run every kind of test, and the exact payloads used to exercise the APIs.

- [1. Test strategy](#1-test-strategy)
- [2. Running the automated tests](#2-running-the-automated-tests)
- [3. Test catalogue (62 tests)](#3-test-catalogue-62-tests)
- [4. Postman / newman](#4-postman--newman)
- [5. API payloads (requests and responses)](#5-api-payloads-requests-and-responses)
- [6. Negative scenarios and expected errors](#6-negative-scenarios-and-expected-errors)
- [7. Latest results and known gaps](#7-latest-results-and-known-gaps)

---

## 1. Test strategy

| Layer | What it proves | Tools |
|---|---|---|
| Unit | Lifecycle rules in the `Claim` entity, error handler never leaks internals | JUnit 5, AssertJ |
| API integration | Real HTTP layer + security + validation + real Flyway schema (H2 in PostgreSQL mode) | `@SpringBootTest`, MockMvc, spring-security-test |
| Messaging | Real Kafka transport: outbox relay, consumers, ordering, idempotency, poison messages, outage recovery | embedded Kafka broker, Awaitility, Mockito |
| End to end | Full Docker stack (Postgres, Kafka, three services) driven through the public APIs | Postman collection run with newman |

Automated tests need neither Docker nor a running Kafka. Test-only credentials live in
`src/test/resources/application.properties` (`test-only-password`); they are not secrets and are never used outside tests.

## 2. Running the automated tests

Run from the repository root.

| What | bash / Git Bash | PowerShell |
|---|---|---|
| Everything | `./gradlew test` | `.\gradlew.bat test` |
| Clean build + tests + jars | `./gradlew clean build` | `.\gradlew.bat clean build` |
| One service | `./gradlew :claims-service:test` | `.\gradlew.bat :claims-service:test` |
| One class | `./gradlew :claims-service:test --tests '*NegativeScenariosIntegrationTest'` | `.\gradlew.bat :claims-service:test --tests "*NegativeScenariosIntegrationTest"` |
| One method | `./gradlew :claims-service:test --tests '*NegativeScenariosIntegrationTest.malformedJsonIs400'` | same with double quotes |
| Lower CPU use | add `--max-workers=2` | add `--max-workers=2` |

Reports: `<service>/build/reports/tests/test/index.html` (HTML) and `<service>/build/test-results/test/*.xml` (JUnit XML).

> On Windows the embedded Kafka broker may print `FileSystemException ... being used by another process` while
> shutting down. It is the broker failing to delete its temp files, not a test failure.

## 3. Test catalogue (62 tests)

### claims-service (39)

| Class | Tests | Covers |
|---|---|---|
| `ClaimLifecycleTest` (unit, 9) | `newClaimIsSubmittedWithClaimNumber`, `happyPathSubmitToSettled`, `infoRequestRoundTripReturnsToReview`, `rejectClosesClaimWithReason`, `cannotApproveWithoutAssessment`, `cannotSkipReview`, `terminalStatesAreFinal`, `cannotAssessOutsideReview`, `onlyAssignedOfficerMayAct` | Every allowed and forbidden status transition |
| `ClaimApiIntegrationTest` (2) | `fullLifecycleWithInfoRequestAndOutboxEvents`, `rejectedClaimCannotBeSettled` | Full REST flow; 7 history rows; 7 ordered outbox events with increasing versions and claimant e-mail/note |
| `NegativeScenariosIntegrationTest` (14) | `malformedJsonIs400`, `unknownEnumValueIs400`, `futureIncidentDateAndBadCurrencyAreFieldErrors`, `currencyMustMatchTheMarket`, `emptyBodyIs400`, `invalidUuidInPathIs400`, `invalidFilterEnumIs400`, `blankReasonOrQuestionOrAmountIs400`, `unknownRouteIsDeniedAndWrongMethodIs405`, `unknownClaimIs404OnEveryEndpoint`, `informationRequestEdgeCases`, `actionsOnUnassignedOrClosedClaimsAreConflicts`, `outOfRangePagingIsClampedNotAnError`, `twoOfficersPickingUpTheSameClaimAtOnceYieldExactlyOneWinner` | Input validation, 404s, business rules, concurrent pick-up (exactly one 200 and one 409) |
| `SecurityAndFeaturesIntegrationTest` (10) | `noCredentialsIs401WithBasicChallenge`, `wrongPasswordIs401`, `healthAndApiDocsArePublic`, `rolesAreEnforced`, `claimantsCannotSeeEachOthersClaims`, `officersSeeOnlyTheirOwnWorkloadManagersSeeAll`, `owningOfficerOrManagerCanReassignOthersCannot`, `claimantUploadsListsAndDownloadsDocuments`, `documentNegativeScenarios`, `documentsCannotBeAddedToClosedClaims` | Authentication, roles, ownership, reassignment, documents (path stripping, type sniffing, 413/415) |
| `GlobalExceptionHandlerTest` (unit, 2) | `unexpectedErrorsBecomeGeneric500WithoutLeakingInternals`, `dataIntegrityViolationIs409WithoutLeakingSql` | 500/409 bodies never contain secrets or SQL |
| `OutboxKafkaIntegrationTest` (1) | `submittedAndAssignedClaimAreRelayedToKafkaInOrder` | REST call → outbox → real Kafka topic, keyed by claim id, same partition, ordered |
| `OutboxRecoveryTest` (1) | `eventsSurviveKafkaOutageAndAreDeliveredInOrderAfterRecovery` | Kafka down: API keeps working, events stay in the outbox; Kafka back: all delivered |

### reporting-service (15)

| Class | Tests | Covers |
|---|---|---|
| `ReportingIntegrationTest` (8) | `duplicateAndStaleEventsAreIgnored`, `exposureCoversOpenClaimsOnlyAndPrefersAssessment`, `exposureTotalConvertsAcrossCurrencies`, `workloadPerformanceAndSummary`, `slaBreachesFlagOldUnassignedAndLongOpenClaimsOnly`, `reportsRequireTheManagerRole`, `unsupportedBaseCurrencyIs400`, `eventJsonRoundTripsThroughJackson` | Projection idempotency, exposure rules, FX maths, SQL performance aggregation, SLA rules, MANAGER-only access |
| `ReportingErrorHandlingTest` (5) | `unknownMarketFilterIs400Problem`, `wrongPasswordIs401`, `unknownRouteIsDeniedAndWrongMethodIs405`, `emptyReadModelReturnsEmptyReportsNotErrors`, `unexpectedErrorsAreGeneric500` | Error format and status codes |
| `ClaimEventListenerKafkaTest` (2) | `consumesEventsAndIgnoresStaleRedelivery`, `poisonMessageDoesNotBlockLaterMessages` | Real Kafka consumption, stale events, dead-lettering |

### notification-service (8)

| Class | Tests | Covers |
|---|---|---|
| `NotificationServiceIntegrationTest` (7) | `claimantIsNotifiedOfClaimantFacingMilestones`, `internalEventsDoNotNotify`, `redeliveredEventNotifiesOnlyOnce`, `eventWithoutRecipientIsIgnoredAndMessagesCarryTheReason`, `claimantsSeeOnlyTheirOwnManagersSeeAll`, `authenticationIsRequired`, `emailAddressesAreMaskedInLogs` | Which events notify, idempotency, message content, access scoping, PII masking |
| `NotificationKafkaTest` (1) | `consumesEventsDeduplicatesRedeliveryAndSurvivesPoisonMessages` | Real Kafka: poison message dead-lettered, duplicate delivered once |

## 4. Postman / newman

Files in `docs/postman/`:

| File | Purpose |
|---|---|
| `claims-platform.postman_collection.json` | 51 requests, 93 assertions, 5 folders |
| `sample-police-report.pdf` | File used by the upload request |
| `generate-collection.js` | Generates the collection; edit this, not the JSON |

Folders (run in order): **0** Health & API docs (public) · **1** Claim lifecycle (submit, upload, list/download
documents, queue, assign, workload, reassign, request info, answer, assess, approve, settle, track, timeline, my claims,
plus a rejected claim) · **2** Manager reports (waits 5 s for Kafka) · **3** Claimant notifications (waits 5 s) ·
**4** Negative scenarios (401, 403, 404, 400, 409, 415, 422).

Collection variables: `claimsUrl` (`http://localhost:8081`), `reportingUrl` (`:8082`), `notificationUrl` (`:8083`),
`demoPassword` (**empty: set it yourself**), `claimantUser`, `otherClaimantUser`, `officerUser`, `officer2User`,
`managerUser`. Ids (`claimId`, `infoRequestId`, `documentId`, ...) are captured automatically.

**Postman app:** Import → the JSON file → collection *Variables* tab → set `demoPassword` to your
`APP_SECURITY_DEMO_PASSWORD` → *Run collection*. For the upload request, set Postman's working directory
(Settings → General) to `docs/postman`, or reselect `sample-police-report.pdf` in the request's Body tab.

**Headless (newman):**

```bash
npm install -g newman
newman run docs/postman/claims-platform.postman_collection.json \
  --working-dir docs/postman \
  --env-var demoPassword="$APP_SECURITY_DEMO_PASSWORD"
# different ports: add --env-var claimsUrl=http://localhost:18081 (and reportingUrl / notificationUrl)
```

Run it against a fresh stack: folder 4 expects the claim from folder 1 to be settled.

## 5. API payloads (requests and responses)

All endpoints except health and API docs need HTTP Basic auth. Responses below are real shapes; ids and timestamps vary.

### 5.1 Submit a claim — `POST /api/claims` (CLAIMANT)

```http
POST http://localhost:8081/api/claims
Authorization: Basic base64(tan@example.com:<password>)
Content-Type: application/json
```
```json
{
  "claimantName": "Tan Wei",
  "market": "SG",
  "claimType": "MOTOR",
  "description": "Rear-ended at a traffic light",
  "incidentDate": "2026-01-10",
  "currency": "SGD",
  "estimatedAmount": 5000.00
}
```

| Field | Rule |
|---|---|
| `claimantName` | required, max 200 |
| `market` | `SG`, `HK`, `MY`, `TH`, `ID`, `AU` |
| `claimType` | `MOTOR`, `PROPERTY` |
| `description` | required, max 4000 |
| `incidentDate` | required, today or earlier |
| `currency` | 3 upper-case letters **and** the market's currency (SG→SGD, HK→HKD, MY→MYR, TH→THB, ID→IDR, AU→AUD) |
| `estimatedAmount` | ≥ 0.01, max 13 digits + 2 decimals |

The claimant's e-mail is **not** in the body: it is the authenticated username.

`201 Created`, `Location: /api/claims/{id}`:
```json
{
  "id": "2f007704-1fdc-45fa-a79f-4fd5641c6f23",
  "claimNumber": "CLM-SG-2026-2F007704",
  "market": "SG",
  "claimType": "MOTOR",
  "status": "SUBMITTED",
  "claimantName": "Tan Wei",
  "claimantEmail": "tan@example.com",
  "description": "Rear-ended at a traffic light",
  "incidentDate": "2026-01-10",
  "currency": "SGD",
  "estimatedAmount": 5000.00,
  "assessedAmount": null,
  "assignedOfficerId": null,
  "rejectionReason": null,
  "submittedAt": "2026-10-08T09:30:12.345Z",
  "closedAt": null
}
```

### 5.2 Upload a document — `POST /api/claims/{id}/documents` (CLAIMANT, owner)

`multipart/form-data` with one part named `file` (PDF, PNG or JPEG, max 5 MB, max 20 per claim, claim must be open):

```bash
curl -u tan@example.com:$PW -F "file=@docs/postman/sample-police-report.pdf;type=application/pdf" \
  http://localhost:8081/api/claims/$ID/documents
```
`201 Created`:
```json
{
  "id": "7d1c2a9e-4b0f-4f5e-9a51-1c0f2b8e6d10",
  "fileName": "sample-police-report.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 213,
  "uploadedBy": "tan@example.com",
  "uploadedAt": "2026-10-08T09:30:14.002Z"
}
```
List: `GET /api/claims/{id}/documents` → array of the above. Download: `GET /api/claims/{id}/documents/{documentId}`
→ the bytes with `Content-Disposition: attachment; filename="..."` and `X-Content-Type-Options: nosniff`.

### 5.3 Officer actions — `/api/staff/claims/{id}/...` (OFFICER; the acting officer is the authenticated user)

| Action | Request body | Result |
|---|---|---|
| `POST .../assign` | none | `SUBMITTED → UNDER_REVIEW`, `assignedOfficerId` = caller |
| `POST .../reassign` (officer of the claim, or MANAGER) | `{"toOfficerId": "officer-2", "reason": "Workload balancing"}` | new officer, status unchanged |
| `POST .../info-requests` | `{"question": "Please provide the police report number"}` | `UNDER_REVIEW → INFO_REQUESTED`; returns the request |
| `POST .../assess` | `{"assessedAmount": 4200.00, "note": "Repair estimate verified"}` | amount recorded, status stays `UNDER_REVIEW` |
| `POST .../approve` | none | `UNDER_REVIEW → APPROVED` (needs an assessment) |
| `POST .../reject` | `{"reason": "Policy lapsed at date of incident"}` | `UNDER_REVIEW → REJECTED`, `closedAt` set |
| `POST .../settle` | none | `APPROVED → SETTLED`, `closedAt` set |

All return the claim (same shape as 5.1) except `info-requests`, which returns:
```json
{
  "id": "c6a5b4f1-8d2e-4c3b-a1f0-9e8d7c6b5a40",
  "question": "Please provide the police report number",
  "requestedBy": "officer-2",
  "requestedAt": "2026-10-08T09:31:02.118Z",
  "response": null,
  "respondedAt": null,
  "open": true
}
```

### 5.4 Answer an information request — `POST /api/claims/{id}/info-requests/{requestId}/response` (CLAIMANT, owner)

```json
{ "response": "Police report no. 12345" }
```
`200 OK`: the request with `"response"`, `"respondedAt"` and `"open": false`. When no open requests remain the claim
returns to `UNDER_REVIEW`.

### 5.5 Reads

| Endpoint | Who | Response |
|---|---|---|
| `GET /api/claims/{id}` | owner claimant, staff | claim (5.1) |
| `GET /api/claims?page=0&size=20` | CLAIMANT | `{"items":[summary...],"page":0,"size":20,"totalItems":1,"totalPages":1}` |
| `GET /api/claims/{id}/history` | owner claimant, staff | `[{"fromStatus":null,"toStatus":"SUBMITTED","actor":"tan@example.com","note":"Claim submitted","occurredAt":"..."}, ...]` |
| `GET /api/claims/{id}/info-requests` | owner claimant, staff | array of info requests (5.3) |
| `GET /api/staff/claims/queue?market=SG&claimType=MOTOR` | OFFICER, MANAGER | page of unassigned claims, oldest first |
| `GET /api/staff/claims?status=UNDER_REVIEW` | OFFICER (own), MANAGER (`officerId=` any) | page of claims |

A list item (`summary`) is: `id, claimNumber, market, claimType, status, currency, estimatedAmount, assessedAmount,
assignedOfficerId, submittedAt`. Paging: `page` ≥ 0, `size` 1–100 (out-of-range values are clamped, not rejected).

### 5.6 Manager reports — `http://localhost:8082/api/reports/...` (MANAGER)

`GET /summary`
```json
{
  "claimsByStatus": {"SUBMITTED": 1, "UNDER_REVIEW": 0, "INFO_REQUESTED": 0, "APPROVED": 0, "REJECTED": 1, "SETTLED": 1},
  "openClaims": 1,
  "unassignedBacklog": 1,
  "exposure": [{"market": "HK", "claimType": "MOTOR", "currency": "HKD", "openClaims": 1, "totalExposure": 900.00}]
}
```
`GET /exposure?market=SG` (optional filter)
```json
[{"market": "SG", "claimType": "MOTOR", "currency": "SGD", "openClaims": 2, "totalExposure": 5200.00}]
```
`GET /exposure/total?baseCurrency=USD`
```json
{
  "baseCurrency": "USD",
  "totalExposure": 868.00,
  "openClaims": 2,
  "breakdown": [
    {"currency": "HKD", "openClaims": 1, "originalAmount": 1000.00, "convertedAmount": 128.00},
    {"currency": "SGD", "openClaims": 1, "originalAmount": 1000.00, "convertedAmount": 740.00}
  ],
  "rateSource": "Static indicative rates from configuration (reporting.fx.rates-to-usd); not live market rates"
}
```
`GET /workload`
```json
[{"officerId": "officer-1", "openClaims": 2, "byStatus": {"UNDER_REVIEW": 1, "INFO_REQUESTED": 1}}]
```
`GET /performance`
```json
[{"officerId": "officer-2", "closedClaims": 2, "settled": 1, "rejected": 1, "averageResolutionHours": 4.0}]
```
`GET /sla-breaches` (thresholds: unassigned > 24 h, open > 7 days; `INFO_REQUESTED` excluded)
```json
[{"claimId": "...", "claimNumber": "CLM-SG-2026-1A2B3C4D", "market": "SG", "claimType": "MOTOR",
  "status": "SUBMITTED", "assignedOfficerId": null, "breachType": "UNASSIGNED_TOO_LONG",
  "ageHours": 30, "thresholdHours": 24}]
```

### 5.7 Notifications — `GET http://localhost:8083/api/notifications` (CLAIMANT own; staff all or `?claimantEmail=`)

```json
{
  "items": [{
    "id": "...",
    "claimId": "...",
    "claimNumber": "CLM-SG-2026-2F007704",
    "recipientEmail": "tan@example.com",
    "eventType": "CLAIM_REJECTED",
    "channel": "EMAIL",
    "subject": "Decision on your claim CLM-SG-2026-2F007704",
    "body": "Dear Tan Wei,\n\nWe are sorry to tell you that your claim CLM-SG-2026-2F007704 has been declined.\n\nReason: Policy lapsed at date of incident",
    "createdAt": "2026-10-08T09:32:40.551Z"
  }],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```
Notified events: `CLAIM_SUBMITTED`, `CLAIM_ASSIGNED`, `INFO_REQUESTED`, `CLAIM_APPROVED`, `CLAIM_REJECTED`, `CLAIM_SETTLED`.
Not notified: `CLAIM_ASSESSED`, `CLAIM_REASSIGNED`, `INFO_PROVIDED`.

### 5.8 Kafka event — topic `claims.events`, key = claim id

```json
{
  "eventId": "3e0f9a6c-1b2d-4c5e-8f70-a1b2c3d4e5f6",
  "type": "INFO_REQUESTED",
  "claimId": "2f007704-1fdc-45fa-a79f-4fd5641c6f23",
  "claimNumber": "CLM-SG-2026-2F007704",
  "version": 3,
  "occurredAt": "2026-10-08T09:31:02.118Z",
  "market": "SG",
  "claimType": "MOTOR",
  "status": "INFO_REQUESTED",
  "assignedOfficerId": "officer-2",
  "currency": "SGD",
  "estimatedAmount": 5000.00,
  "assessedAmount": null,
  "submittedAt": "2026-10-08T09:30:12.345Z",
  "closedAt": null,
  "claimantEmail": "tan@example.com",
  "claimantName": "Tan Wei",
  "note": "Please provide the police report number"
}
```

## 6. Negative scenarios and expected errors

Every error is RFC 7807 `application/problem+json`:
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Cannot move claim from SETTLED to SETTLED",
  "instance": "/api/staff/claims/2f007704-1fdc-45fa-a79f-4fd5641c6f23/settle"
}
```
Validation failures add an `errors` list:
```json
{
  "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Validation failed",
  "instance": "/api/claims",
  "errors": ["incidentDate: must be a date in the past or in the present", "currency: must be an ISO-4217 code, e.g. SGD"]
}
```

| Scenario | Example | Status | Tested in |
|---|---|---|---|
| Malformed JSON / empty body | `{not json` | 400 | Negative…, Postman 4 |
| Field validation | `{"estimatedAmount": -1}` | 400 + `errors` | Negative…, Postman 4 |
| Currency not the market's | `"market":"SG","currency":"USD"` | 400 | Negative…, Postman 4 |
| Unknown enum / bad UUID / bad filter | `"market":"MARS"`, `/api/claims/not-a-uuid`, `?market=XX` | 400 | Negative…, Reporting… |
| Blank reason / question / zero amount / empty reassign | `{"reason":" "}` | 400 | Negative… |
| Unsupported report currency | `?baseCurrency=XYZ` | 400 | Reporting… |
| No or wrong credentials | no `Authorization` header | 401 + `WWW-Authenticate: Basic` | Security…, Postman 4 |
| Wrong role | claimant → `/api/staff/**`, officer → `/api/reports/**`, manager → assign | 403 | Security…, Reporting…, Postman 4 |
| Officer viewing another officer's workload | `?officerId=officer-1` as officer-2 | 403 | Security… |
| Another claimant's claim | lee@ reading tan@'s claim | 404 (no enumeration) | Security…, Postman 4 |
| Unknown claim / document | random UUID | 404 | Negative…, Postman 4 |
| Wrong HTTP method | `GET .../assign` | 405 | Negative…, Reporting… |
| Invalid transition / closed claim / unassigned claim | settle twice; reject before assign | 409 | Negative…, Postman 4 |
| Two officers pick up at once | concurrent `assign` | one 200, one 409 | Negative… |
| Document > 5 MB | 5 MB + 10 bytes | 413 | Security… |
| Document wrong type / content not matching type | `.exe`; HTML sent as `application/pdf` | 415 | Security…, Postman 4 |
| Approve without assessment | assign then approve | 422 | Negative…, Postman 4 |
| Acting on a claim assigned to someone else | officer-2 assesses officer-1's claim | 422 | Negative…, Security…, Postman 4 |
| Info request on another claim / already answered | answer twice | 422 | Negative… |
| Unexpected failure | any unhandled exception | 500, generic message | GlobalExceptionHandlerTest |
| Duplicate / stale / poison Kafka message | redelivery, older version, non-JSON | ignored / dead-lettered | Kafka tests |
| Kafka outage | broker down while claims change | API works; events delivered later | OutboxRecoveryTest |

## 7. Latest results and known gaps

| Run | Result |
|---|---|
| `./gradlew clean test` | 62 tests, 0 failures, 0 skipped |
| newman against the full Docker stack (Postgres, Kafka, three services) | 51 requests, 93 assertions, 0 failures |
| Security checks on the running stack | no password in any container log; e-mail addresses masked; containers run as non-root `app` |

Known gaps:
- The catch-all 500 handler is tested at unit level only (it cannot be triggered through the API without fault injection).
- Automated tests use H2 and an embedded Kafka broker; PostgreSQL and the real broker are covered by the Docker + newman run,
  not by an automated CI job (Testcontainers would close this).
- No load or performance tests.
