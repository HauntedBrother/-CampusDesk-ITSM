# Project progress

Updated: 15 November 2026. Baseline: mandatory OOP project requirements and the agreed stack/business rules.

## Phases 1-2 retained

Java 21/Spring Boot, PostgreSQL/Flyway, React/TypeScript/Vite/Tailwind/shadcn UI, JWT/BCrypt and RBAC remain intact. Original auth routes, employee ownership, technician scope, private-note isolation, eight-state lifecycle, verification/closure, feedback, history, search and catalogs are preserved. Existing suites remain part of validation.

## Phase 3 - Priority, assignment and SLA

Implemented:

- Backend score = impact x urgency x service criticality (each 1-3), with P1 >=18, P2 >=9, P3 >=4, otherwise P4. Active incident factor edits and service criticality changes recalculate and audit priority/score. Completed records retain historical contracts.
- Strategy Pattern: skill-based (proficiency then workload) and load-based (workload then proficiency), with deterministic tie-breaking. Both require category qualification, an enabled account, availability and capacity (default 5). Development seeds assume no skills.
- Persistent strategy selection/automatic toggle, admin skill/capacity/availability editing, explicit strategy retry and manual override with the same eligibility checks. Empty pools leave a saved, visibly unassigned incident. Existing assignments are not silently rebalanced. Required active skills/capacity cannot be removed.
- Automatic report processing confirms the employee-supplied category and follows NEW -> CLASSIFIED -> ASSIGNED through normal State objects. With automatic mode off, reports remain NEW for manual classification. This is not Phase 4 description categorization.
- Configurable global/service-specific SLA policies and required P1-P4 defaults. Original creation time anchors response/resolution deadlines, 24/7 without pauses. Factor/policy edits never reset the clock. First actual acknowledgement/response is retained separately from resettable lifecycle milestones.
- Independent response/resolution outcomes, configurable approaching threshold (80% elapsed default), deduplicated warning/violation history and retained breaches. Completion exactly at deadline is on time. Missing policy produces an explicit issue; an existing contract is retained if its policy disappears.
- Scheduled monitoring drains batches of 100 in separate transactions each 15-second fixed-delay run. Failed batches retain their cursor for retry. Background/migration audit events identify System as actor. No notifications or intelligent features were added.
- A database-backed automation lock precedes incident/profile locks and serializes writers across instances. Capacity/configuration consistency and incident audit entries share transactions. This favors correctness for project scale over maximum write throughput.
- Additive V3 migration for skills, settings, policies, SLA snapshots and configuration audit. Legacy priority/deadline initialization is recorded in incident history; V1/V2 are unchanged.
- Admin Assignment & SLA page; priority scores, qualified manual choices, assignment issues, server-anchored countdowns, warning/breach indicators and configuration history. Detail refreshes every 15 seconds. Priority/allocation/outcome business logic stays on the backend.
- Updated setup instructions and smoke workflow for skills, priority, strategy assignment and persistent SLA acknowledgement. Smoke data is retained; the seed technician's original configuration is restored after successful closure.

## Integration verification - Phases 1-3

Verified on 15 November 2026 against Docker Desktop 4.99.0 / Linux Engine 29.9.3 and real PostgreSQL 17.12. Java 21.0.13 and Maven 3.9.9 executed the complete host suite. Testcontainers 1.21.4 explicitly connected through `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`; no H2 or mocked replacement was used for PostgreSQL integration tests.

### Exact final results

| Check | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: |
| Backend complete `mvn verify` suite | **228** | **0** | **0** |
| Auth PostgreSQL integration subset | 10 | 0 | 0 |
| Core incident PostgreSQL integration subset | 9 | 0 | 0 |
| Automation PostgreSQL integration subset | 9 | 0 | 0 |
| Other backend unit/HTTP cases | 200 | 0 | 0 |
| Frontend `npm test` | **29** | **0** | **0** |

The PostgreSQL subsets total **28 executed cases**. The previously reported 26 skipped entries included an unexpanded parameterized role test; execution expands it into three role cases. Subsets are included in the 228 backend total, not additional tests.

Both host builds passed: Java 21 executable JAR packaging and TypeScript/Vite production build. Docker image builds also passed their 200 backend unit/HTTP tests and 29 frontend tests, plus packaging; these repeat cases already counted above. BuildKit runs unit/HTTP tests without an engine socket; the complete PostgreSQL suite ran on the Docker-enabled host. All three integration classes now require Docker instead of silently skipping when it is unavailable.

### Fresh database and running stack

- Used isolated Compose project `itsm-verification-20261115` and named volume `itsm-verification-20261115_postgres_data`, retaining all volumes/data. No volume deletion or pruning was performed.
- Confirmed **zero public tables** before application startup on the verification database.
- Flyway applied V1 `identity`, V2 `core incidents`, V3 `priority assignment sla` successfully. All three persisted history rows have `success=true`; fresh Testcontainers databases also applied all migrations and passed Hibernate schema validation.
- PostgreSQL, backend and frontend started successfully using the existing Compose configuration with isolated port overrides: database 15432, backend 18080, frontend **http://localhost:15173**.
- The verification stack is left running. Default development ports remain unchanged for normal use.

### End-to-end runtime checks

- Existing `scripts/smoke.ps1 -BaseUrl http://localhost:15173`: **PASS**. Verified all three seed logins, nine role/dashboard combinations, employee-only registration despite role injection, logout token revocation, complete incident lifecycle, calculated P1 score 18, qualified strategy assignment, private-note isolation, verification-before-closure, feedback and persisted audit history.
- Smoke script stopped and restarted PostgreSQL/backend, then logged in again and verified the same closed incident, feedback, priority, first acknowledgement and history survived: **PASS**.
- Additional live scheduled SLA probe: **PASS**. Created an isolated service-specific P1 policy (20-second response, 25-second resolution, 10% warning threshold), deliberately left a real assigned incident unacknowledged, and observed exactly one approaching event and one breach event per target, all with `System` audit actors. Acknowledgement/resolution/verification/closure retained both historical breach outcomes. This exercised the running scheduler, not a mock or direct substitute for it. Original technician configuration was restored after closure.
- Recreated all three verification containers with `docker compose up -d --force-recreate --wait`, retaining the same named PostgreSQL volume: **PASS**. Before/after logical checksums and counts matched for users, incidents, comments, incident history, configuration audit and SLA policies; Flyway history and incident outcome snapshots matched as well.
- Persisted evidence after recreation: **4 users, 2 closed incidents, 2 comments, 27 incident history rows, 5 configuration audit rows and 5 SLA policies**. Incident 1 retained P1/score 18, acknowledgement and feedback 5; incident 34 retained P1/score 27, acknowledgement and both SLA breaches. Sequence gaps do not affect persistence verification.
- Final HTTP checks after recreation: frontend HTML **200**, referenced JavaScript **200**, CSS **200**, and same-origin `/api/health` **UP**.

### Failures fixed during verification

The initial complete database run executed 228 tests with **2 assertion failures, 1 fixture error, 0 skipped**. Strict SLA deadline comparisons exposed nanosecond-to-microsecond round-trip differences: incident timestamps now normalize to PostgreSQL microsecond precision, and a new round-trip equality assertion remains in place. The late-acknowledgement fixture referenced an unsaved policy UUID; it now ages the existing persisted contract without violating its foreign key. All original assertions remain; the subsequent complete suite passed 228/228.

The cold WSL database initialization exceeded PostgreSQL's initial control-process shutdown timeout. Compose now allows `PGCTLTIMEOUT=300` and uses TCP readiness with a startup grace period, preserving durable writes and the volume. Image dependency resolution was narrowed to actual project dependencies to avoid downloading unused deployment/database plugins. Image builds explicitly run unit/HTTP tests while host verification runs every PostgreSQL test. Vitest concurrency is bounded at two workers to avoid resource exhaustion, without excluding tests. An auxiliary live-probe PowerShell array input was corrected without relaxing backend validation.

### Remaining verification limits

**No remaining integration/test/container blockers.** The browser access host remains unavailable, so an interactive visual/browser walkthrough could not be performed. Frontend component tests, production assets, real same-origin APIs and all required database/container checks above passed. No next-phase implementation was started.

Detailed local command logs, JUnit summaries, scheduler probe results and before/after checksum evidence are retained under ignored `.tmp/`. See README for repeatable Docker Desktop and isolated Compose commands.

## Remaining work

Later deliverables include user/technician administration required by the brief, UML/ER diagrams, technology/security/design justification and recorded workflows. Historical resolution recommendations remain optional. Group number is still needed for academic-year 2026-2027 final filenames; the brief's deadline is 22 November 2026.


## Phase 4 — Notifications, audit and intelligent features (2026-11-15)

### Implementation

- Added V4 Flyway migration: persistent notification outbox/recipient snapshots, in-app messages/read state, simulated email delivery logs, category rules and a development seed version marker. Existing migration files and Docker volumes are preserved.
- Observer delivery uses Spring AFTER_COMMIT events and separate transactions. Incident changes, structured history and outbox writes commit together; channel writes and delivery completion commit together. Row locks and unique keys deduplicate concurrent retries. Failed events retain bounded exponential backoff and a scheduled retry drain. Recipients are enabled reporter/current technician/admin users, with current incident authorization rechecked during delivery and reads.
- Creation, assignment, calculated priority changes, SLA warning/breach, resolution, closure and resolved/closed administrator overrides emit notifications. Internal investigation notes never emit messages. Administrators inspect simulated email logs; no external email provider is contacted.
- Incident audits now persist actor/system actor, incident ID, action, time, and structured old/new values across report edits, lifecycle, priority/SLA/assignment, comments/notes, resolution/verification/closure, feedback and overrides. PostgreSQL enforces append-only history. Existing legacy rows retain their original fields and explicitly mark status-only legacy snapshots. Employee reads exclude internal records.
- Added deterministic description/title trigram matching scoped to the same service and current user's accessible active incidents, with a bounded newest-200 candidate window, threshold 0.35 and top-five results. No automatic merging or cross-owner leakage. No PostgreSQL extension installation or paid AI API is required.
- Added persisted, weighted whole-word/phrase category rules. Omitted API categories use the highest matching rule, with explicit no-match validation; supplied categories and UI corrections take precedence. Admin rule changes are audited; unchanged updates do not add duplicate audit records.
- Added notification center/unread badge, administrator simulated-email logs and category rule editor, incident form/detail suggestions, and structured audit timeline. Frontend data comes from authorized backend APIs.
- Added development-only network/application/standby technicians with varied skills, capacities and availability, plus real labelled demo incident workloads. A locked version marker prevents reseeding on restart and preserves existing profiles/settings.
- Extended the existing smoke script to cover Phase 4 APIs and notification/read-state persistence through database/backend restart. Added PostgreSQL rollback, post-commit, channel failure/recovery, concurrent replay, permissions, similarity scope, categorization/manual correction, immutable structured audit, SLA notifications and seed-idempotence tests.

### Verification

- Backend current JUnit report aggregate: **252 passed, 0 failures, 0 errors, 0 skipped**, across 17 suites: **210 unit/HTTP + 42 real PostgreSQL tests**. The complete `clean verify` and cleanup `verify` runs each passed all then-existing 251 tests. A subsequent focused `IntelligenceTest` verify passed 10/10 after adding the final canonical Unicode keyword validation regression, bringing the distinct current test count to 252.
- PostgreSQL suites: authentication 10, core incidents 9, priority/assignment/SLA 9, Phase 4 13, development scenario 1. Docker-required Testcontainers used `npipe:////./pipe/dockerDesktopLinuxEngine` with PostgreSQL `17-alpine`; no H2 replacements, disabled tests or optional-Docker skips. Each fresh database applied Flyway V1–V4 and passed Hibernate schema validation.
- Frontend Vitest: **38 passed, 0 failed, 0 skipped**, four suites. `npm run build` completed successfully.
- The full regression exposed and fixed audit repository property-path ambiguity (explicit JPQL now traverses `incident.id`), a test SLA fixture referencing an unsaved policy (now uses a persisted contract), and cached test contexts retaining schedulers after container shutdown (contexts now close after every integration class). A final guard rejects keywords that expand beyond 80 characters during Unicode normalization. Original assertions and lifecycle/security checks remain enforced.
- Backend Docker image build: **PASS**, including **210/210 unit/HTTP tests, 0 skipped**. Frontend image build: **PASS**, including **38/38 tests** and the production Vite build. Full PostgreSQL tests run on the Docker-enabled host; BuildKit does not have an engine socket.
- Started the updated stack in the existing `itsm-verification-20261115` Compose project on ports PostgreSQL 15432, backend 18080, frontend **http://localhost:15173**, retaining `itsm-verification-20261115_postgres_data`. Flyway upgraded the existing V3 database to V4 successfully; V1–V4 all have `success=true`.
- Existing-data preservation: **PASS**. Original **4 users, 2 incidents and all original fields of 27 audit rows** retained exact logical checksums after upgrade. V4 marked all 27 legacy status snapshots explicitly; no original values were fabricated or overwritten. New development workloads were verified as network 2, application 1 and unavailable standby 0.
- Extended `scripts/smoke.ps1 -BaseUrl http://localhost:15173`: **PASS**. All-role login/RBAC/logout, registration role-injection protection, lifecycle/verification/feedback, calculated P1 priority, qualified assignment, SLA targets, internal-note privacy, notification ownership/read state, administrator-only simulated logs, duplicate matching and persisted category-rule suggestions passed. PostgreSQL/backend were stopped and restarted; incident, structured closure audit and notification read state persisted.
- Live scheduled SLA probe: **PASS** on incident 39. Observed exactly one response warning, resolution warning, response breach and resolution breach, with `System` audit actors. Every action produced exactly one in-app message for each of employee/assigned technician/admin and three corresponding `SIMULATED` delivery logs. Closure retained both breach outcomes; original technician configuration was restored.
- Full `docker compose up -d --force-recreate --wait`: **PASS** using the same named volume. Before/after counts and logical checksums matched for users, incident business fields, comments, structured incident audits, outbox/recipients, in-app messages/read state, simulated logs, rules, seed markers, configuration history, SLA policies, technician profiles and skills. Active incidents legitimately receive scheduler evaluation times/ORM version increments; these two volatile fields were excluded from business hashes and separately verified not to regress. Original closed incidents and legacy audit fields retained their exact original hashes.
- Persisted final evidence: **8 users, 7 incidents (3 development assignments remain active), 4 comments, 69 incident audit rows, 23 processed outbox events, 59 recipient entries, 59 in-app messages, 59 simulated email logs, 14 category rules, 1 seed version, 12 configuration audit rows, 6 SLA policies, 4 technician profiles and 6 skill records**. Runtime queries confirmed **0 pending outbox events, 0 duplicate in-app deliveries and 0 duplicate simulated deliveries**.
- Final HTTP checks: frontend HTML **200**, deployed Phase 4 JavaScript **200** (contains the intelligence API integration), CSS **200**, same-origin `/api/health` **UP**. PostgreSQL/backend are healthy and frontend is running at **http://localhost:15173**. The verification stack is left running; no volume deletion/pruning was performed.

### Remaining limits

**No remaining build, automated-test, PostgreSQL or container blockers.** Automated results: **252 backend + 38 frontend passed; 0 failed, 0 errors, 0 skipped**. Interactive visual/browser QA could not run because the browser access host is unavailable; it was checked again during this phase. Component behavior, production assets and real same-origin APIs passed. Matching is intentionally bounded to the newest 200 authorized active incidents, as documented in README. Historical resolution recommendations and later-phase deliverables were not started.

Exact local command logs, JUnit report aggregate, live SLA evidence and preservation/recreation checksums are retained in ignored `.tmp/phase4-*` files. Implementation/API/seed/startup details are in README.


## Phase 5 - Reporting, analytics and dashboards (2026-11-15)

### Implementation

- Added dedicated read-only report controller/service/JDBC projection repository. PostgreSQL computes all summary statistics, creation-cohort trends, priority/category/status/SLA distributions, technician performance and incident-derived service health. No incident collections are loaded to compute aggregates.
- Added V5 SQL SLA functions shared between report queries, exports and JPA incident filtering, plus creation/owner/workload indexes. Existing V1–V4 migrations and data are untouched. Outcomes preserve strict deadline boundaries, first-response/completion semantics and historical breaches.
- Added SLA-status filtering and whitelisted stable sorting to incident lists, retaining all prior combined filters and pagination. To-one entity graphs remove per-row lookups; a PostgreSQL test verifies exactly two statements for a full page and count.
- Implemented employee/technician/admin dashboards with PostgreSQL totals and averages, interactive trend/priority/category/status/SLA charts, completed-SLA compliance segment inspection, service health, role-scoped recent incidents/deadlines, persisted notifications and paginated workload/service tables. Technician active capacity usage includes all dates; filtered performance uses the selected creation cohort.
- Added inclusive UTC report date ranges, all-time/reset/last-30-day controls, chart drill-down, loading/empty/error/retry states, responsive overflow and authorized CSV download. CSV streams all matching rows in batches of 500 with UTF-8, standard quoting, formula neutralization and no-store headers; it omits private investigation notes.
- Extended smoke verification with live aggregate accuracy, combined SLA filtering/sorting/pagination, administrator analytics permissions, technician resolution performance, scoped CSV export and report persistence through restart. Added 14 PostgreSQL report tests and CSV/frontend coverage. README documents API/query parameters and exact metric definitions.

### Verification

- Complete backend `mvn clean verify`: **268 passed, 0 failures, 0 errors, 0 skipped**, across 19 suites: **212 unit/HTTP + 56 real PostgreSQL integration tests**. The reporting suite adds 14 cases for precise totals, chart reconciliation, cohort/live workload separation, pagination/sorting, inclusive UTC dates, all five SLA outcomes across reports/lists, authorization, streaming CSV content and formula safety, empty data, and a two-statement paginated fetch. Existing Phase 1–4 integration suites remain intact.
- Frontend Vitest: **51 passed, 0 failed, 0 skipped**, five suites. Production TypeScript/Vite build: **PASS**. New coverage includes drill-down combinations, date/month selection, role-specific API requests, completed-compliance segment inspection, workload pagination, empty/error/retry states, CSV text/bearer handling, download/revocation and filtered table sorting.
- All fresh Testcontainers databases applied Flyway V1–V5 on PostgreSQL `17-alpine` through Docker Desktop `npipe:////./pipe/dockerDesktopLinuxEngine`; no H2, mocks replacing PostgreSQL, disabled tests or optional-Docker skips.
- Corrected initial test setup ordering for the per-class PostgreSQL container, supplied required resolution/verification/closure fixture values, and renamed a reserved SQL trend alias. Two concurrent host frontend tests initially exceeded their unchanged five-second timeout under JVM/resource pressure; the sequential complete run passed all 51 without raising timeouts, removing assertions or skipping cases.
- Backend Docker image build: **PASS**, including **212/212 unit/HTTP tests, 0 skipped**. Frontend Docker image build: **PASS**, including **51/51 tests** and production TypeScript/Vite build. Docker builds ran sequentially; the complete PostgreSQL suite ran on the Docker-enabled host.
- Existing-stack upgrade: **PASS** in Compose project `itsm-verification-20261115` on PostgreSQL 15432 / backend 18080 / frontend **http://localhost:15173**. Flyway V5 applied successfully; V1–V5 all retain `success=true`. The existing database container and named volume `itsm-verification-20261115_postgres_data` were retained during deployment; no volume deletion or pruning was performed.
- Existing-data preservation: **PASS**. Recorded pre/post upgrade business hashes matched exactly for **9 users, 7 incidents, 4 comments, 69 incident audits, 23 outbox events, 59 recipients/in-app messages/simulated delivery logs, 14 rules, 1 seed marker, 12 configuration audits, 6 policies, 4 technician profiles and 6 skill records**. As in Phase 4, active scheduler evaluation timestamps and corresponding ORM versions are excluded from incident business hashes. V5 changed functions/indexes, not stored incident or identity values.
- Expanded `scripts/smoke.ps1 -BaseUrl http://localhost:15173`: **PASS**. Existing login/RBAC/logout, registration role-injection protection, lifecycle/priority/qualified assignment/SLA, private notes, verification/feedback, notifications/read state, simulated email logs, duplicate/category suggestions and structured audit checks passed. New report totals, completed compliance, all combined filters including SLA status, deterministic sorting/pagination, technician resolution analytics, scoped UTF-8 CSV export and analytics permissions passed. PostgreSQL/backend were stopped/restarted; incident/audit/notification and report source data persisted.
- Live API versus direct PostgreSQL comparison after restart: **PASS**. Actual dashboard summary: **8 total, 3 open, 5 resolved, 0 critical-open, 2 distinct SLA violations; 3/5 eligible completions compliant (60%)**. These counts were compared to fresh SQL, not hardcoded frontend expectations. Report averages and charts returned real stored data. Snapshot evidence is retained in `.tmp/phase5-live-report.json`.
- Final HTTP checks: deployed HTML **200**, Phase 5 JavaScript (contains the reporting/export integration) **200**, CSS **200**, same-origin `/api/health` **UP**. All five persisted migrations remain successful; **0 pending notification outbox events**. PostgreSQL/backend are healthy and frontend is running; the verification stack is left available at **http://localhost:15173**.

### Remaining limits

**No remaining build, automated-test, database or container blockers.** Final automated results: **268 backend + 51 frontend passed; 0 failed, 0 errors, 0 skipped**. Interactive visual/browser QA remains unavailable because the browser access host cannot be reached; it was rechecked during this phase. Component interaction tests, production builds/assets and real same-origin API workflows passed. Service health intentionally reports incident-derived cohort signals rather than uptime; workload/performance definitions and CSV behavior are documented in README. No later phase was started.

Exact command logs, JUnit aggregate, migration/preservation hashes and live report/asset checks are retained under ignored `.tmp/phase5-*`.
