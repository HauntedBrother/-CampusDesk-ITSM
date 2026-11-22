# CampusDesk — Intelligent University ITSM

Phases 1–5 implement identity, persistent authentication, RBAC, service/category catalogs, the incident lifecycle, automatic priority, qualified assignment, SLA monitoring, transactional notifications, immutable structured audits, duplicate suggestions, rule-based categorization and database-backed reporting. The mandatory course brief remains the requirements baseline. See [progress and verification](docs/PROGRESS.md).

## Start with Docker

Prerequisite: Docker Engine / Docker Desktop with Compose v2, running Linux containers.

```powershell
Copy-Item .env.example .env
# For development only: review the values in .env.
docker compose up --build --wait
```

Open http://localhost:5173. API: http://localhost:8080/api/health. The frontend proxies `/api` to the backend; neither development nor Docker requires cross-origin credentials.

Development seed users (only created with the `dev` Spring profile):

| Role | Email | Password |
| --- | --- | --- |
| Employee | employee@university.test | `DEV_SEED_PASSWORD` from .env |
| Technician | technician@university.test | `DEV_SEED_PASSWORD` from .env |
| Administrator | admin@university.test | `DEV_SEED_PASSWORD` from .env |

The example development password is `DevOnly!Change123`. Seeds are idempotent and never reset an existing password. New public registrations always receive EMPLOYEE regardless of supplied role fields. Technician and admin provisioning UI is outside Phase 1.

For a new database, sign in as the administrator and open **Services & categories** to create at least one active IT service and category. Next open **Assignment & SLA** and configure the technician’s category skills, capacity and availability. Seed technicians have no assumed qualifications. Global SLA defaults are installed by Flyway. Then sign in as an employee to report an incident. The backend calculates priority and deadlines, confirms the submitted category, and attempts qualified assignment using the selected strategy. Reports remain classified and visibly unassigned if no candidate can accept them. The technician acknowledges, investigates and resolves; the reporting employee verifies, supplies feedback and closes. Admins can close verified incidents, manually override assignment subject to qualification/capacity checks, or use an explicitly audited lifecycle override. No incident description categorization or notifications are implemented yet.

PostgreSQL data is stored in the `postgres_data` named volume. `docker compose down` preserves it. Removing the volume deletes data. No credentials are committed in a real `.env` file. For a non-development environment, remove `dev` from `SPRING_PROFILES_ACTIVE`, replace all secrets, supply TLS at the reverse proxy, and provision privileged users through a controlled administrative process. Development seed accounts are never created outside `dev`; existing development databases must not be reused in production.

## Local development

Prerequisites: Java **21**, Maven 3.6.3+, Node 22.12+ (or Node 24), and PostgreSQL 17. Docker can run just the database.

```powershell
docker compose up -d db
# Export .env values to the current shell without executing the file:
Get-Content .env | ForEach-Object {
  if ($_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
  }
}
cd backend
mvn spring-boot:run
# In a second terminal:
cd frontend
npm ci
npm run dev
```

Backend uses `DATABASE_URL` (default `jdbc:postgresql://localhost:5432/itsm`), `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET` (at least 32 bytes), and optional `TECHNICIAN_DEFAULT_CAPACITY` (default 5). Flyway owns schema changes; Hibernate validates rather than creates tables.

## Checks

```powershell
cd frontend
npm test
npm run build
cd ../backend
mvn verify
# From repository root, with Docker running and .env configured:
powershell -ExecutionPolicy Bypass -File scripts/smoke.ps1
```

Backend unit tests use JUnit 5 and Mockito. HTTP slice tests exercise real Spring Security with mocked persistence. PostgreSQL integration tests require Testcontainers and a running Docker engine; engine failures fail the suite instead of silently skipping it. On Windows, select Docker Desktop's engine explicitly before the full host run: `$env:DOCKER_HOST = docker context inspect desktop-linux --format '{{.Endpoints.docker.Host}}'`, then run `mvn verify` with Java 21. The smoke script builds/starts Compose, checks all role combinations, registration/login/logout, the incident lifecycle, priority/qualified assignment/SLA tracking, private-note visibility, and database/backend restart persistence. It leaves services and uniquely named test records running for review. Image builds run backend unit/HTTP tests and frontend tests/build; the complete PostgreSQL suite runs on the Docker-enabled host because BuildKit has no engine socket. Frontend test concurrency is capped at two workers without excluding tests.

For isolated verification, set `COMPOSE_PROJECT_NAME` to a unique project name and optionally set `POSTGRES_PORT`, `BACKEND_PORT`, and `FRONTEND_PORT`; defaults remain 5432, 8080 and 5173. The project gets its own named PostgreSQL volume. Pass the selected frontend URL to `scripts/smoke.ps1 -BaseUrl`. Existing projects/volumes do not need to be stopped or deleted. PostgreSQL health checks use TCP to avoid the temporary initialization server, and `PGCTLTIMEOUT=300` accommodates slow first initialization without disabling durable writes. See [PostgreSQL pg_ctl documentation](https://www.postgresql.org/docs/17/app-pg-ctl.html).

## Structure and security

- `backend/.../identity`: users, roles, technician profiles and development provisioning.
- `backend/.../auth`: validated API DTOs, BCrypt authentication, JWT issuance and persistent revocable sessions.
- `backend/.../security`: JWT signature/issuer/expiry validation, active-session checks and backend URL authorization.
- `backend/.../dashboard`: role-restricted foundation endpoints, without incident data.
- `backend/.../catalog`: persistent IT services and incident categories, with admin-only writes and deactivation.
- `backend/.../incident`: incident aggregate, state-specific lifecycle objects, ownership policy, comments, append-only history, scoped search and command APIs.
- `backend/.../incident/automation`: priority policy, interchangeable skill/load assignment strategies, calendar SLA policies, administrator configuration and configuration audit.
- `backend/.../shared`: consistent input/conflict error responses.
- `frontend/src/auth`: authentication context and protected routes.
- `frontend/src/components/ui`: local shadcn/ui source components using Radix Slot, CVA and Tailwind; configured through `components.json`.
- `frontend/src/pages`: authentication screens, responsive application layout and three dashboard shells.
- `frontend/src/incidents`: searchable/paginated list, create/edit forms, detail/timeline, role-specific commands, notes/feedback, catalog and automation management, server-anchored SLA countdowns and breach indicators. Dashboard counts come from authorized incident queries.

JWTs expire in one hour. Every authenticated API request validates a persisted session and retrieves the current role from the database. Logout revokes the session server-side. Browser sessionStorage preserves login through reload within one tab; closing that tab removes its local token, while server sessions still expire normally. No refresh token is issued. Bearer tokens are never cookies, so CSRF protection is disabled for this stateless API. The frontend clears expired/unauthorized sessions and refuses logout completion if server revocation fails due to connectivity. Same-origin proxying avoids permissive CORS. Protected UI routes supplement backend authorization. JSON errors exclude password/hash details. Database constraints also protect normalized-email uniqueness during concurrent registration.

This is a development foundation, not a production deployment: TLS termination, rate limiting and operational secrets management must be configured for any public deployment. Expired-session cleanup and account-management workflows can follow in later phases.

## Incident rules and APIs

Normal lifecycle: `NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED`. State objects permit only the next normal step; command endpoints additionally enforce roles and ownership. Automatic assignment audits NEW → CLASSIFIED → ASSIGNED in order using the supplied category. With automatic assignment off, reports remain NEW for manual classification. Employees see only their own reports, technicians see only assigned work, and administrators see all incidents. Both direct lookup and searches enforce this scope. Unauthorized IDs return 404. Employee edits are limited to NEW/CLASSIFIED; admins can edit before resolution. Assignment/reassignment is allowed in CLASSIFIED/ASSIGNED, and capacity counts ASSIGNED/ACKNOWLEDGED/IN_PROGRESS incidents. Reassigning work already in progress requires an audited admin override to CLASSIFIED first. Employees cannot read internal notes or their private audit entries.

Normal verification is reserved for the reporting employee. Employee/admin closure requires VERIFIED. An admin override requires a reason of at least 10 characters; later states still require an assigned technician and a recorded resolution. An exceptional closure records administrative verification explicitly in its override history, never impersonating the employee. Reopening clears current lifecycle milestones, resolution and feedback while preserving prior content in audit history. First actual SLA acknowledgement/response and recorded breaches are retained. All mutations and their audit entries share a transaction. Writers acquire a database-backed automation lock before incident/profile locks, serializing assignments, configuration and monitoring across instances. This favors correctness over write throughput for the project’s scale; scaling would require finer-grained locking.

| API | Purpose |
| --- | --- |
| `GET/POST /api/incidents` | Scoped paginated search / employee creation |
| `GET/PUT /api/incidents/{id}` | Authorized detail / editable report fields |
| `POST /api/incidents/{id}/classify` | Admin confirms category/service classification |
| `POST /api/incidents/{id}/assign` | Admin manual assignment, body `{technicianId}` |
| `POST /api/incidents/{id}/auto-assign` | Admin explicitly runs the selected strategy, including when automatic mode is off |
| `POST /api/incidents/{id}/acknowledge`, `/start` | Assigned technician workflow |
| `POST /api/incidents/{id}/resolve` | Assigned technician, body `{resolution}` |
| `POST /api/incidents/{id}/verify`, `/close` | Verification and closure |
| `POST /api/incidents/{id}/comments` | Public comment or staff internal note, body `{body,internal}` |
| `POST /api/incidents/{id}/feedback` | Reporter feedback after resolution, body `{rating,feedback}` |
| `POST /api/incidents/{id}/override` | Admin exception, body `{target,reason}` |
| `GET/POST /api/catalog/services`, `/categories` | Active catalogs / admin creation |
| `PUT /api/catalog/services/{id}`, `/categories/{id}` | Admin update/deactivation |
| `GET /api/catalog/technicians` | Admin assignment candidates |
| `GET/PUT /api/admin/automation/settings` | Assignment strategy and automatic-assignment toggle |
| `GET/PUT /api/admin/automation/policies` | Global/service-specific SLA configuration, upsert by scope + priority |
| `GET /api/admin/automation/technicians` | Skills, availability, capacity and actual active workload |
| `PUT /api/admin/automation/technicians/{id}` | Configure capacity, availability and category proficiency |
| `GET /api/admin/automation/history` | Most recent 100 configuration audit entries |

Search accepts `id` (numeric or `INC-000001`), `status`, `categoryId`, `serviceId`, `priority` (`P1`–`P4` or `UNSET`), `technicianId`, `createdFrom`, `createdTo`, `page` (zero-based), and `size` (1–100). Dates are inclusive UTC calendar dates. Normal JSON update DTOs cannot set status, reporter, technician, priority or milestones. Priority is calculated by the backend; `UNSET` remains a compatibility filter. Catalog deactivation preserves linked history. Admins can list inactive entries with `includeInactive=true`.

## Priority, assignment and SLA behavior

Score = impact × urgency × business criticality, each 1–3. Thresholds: P1 ≥18; P2 ≥9; P3 ≥4; otherwise P4. Incident factor edits and service criticality edits recalculate active incidents and audit priority/score. Completed incidents retain their historical priority/targets. Migration V3 initializes legacy priorities and deadlines from original creation time, with a system history entry.

Skill-based assignment ranks eligible technicians by category proficiency (1–3, highest first), then active workload. Load-based ranks by workload (lowest first), then proficiency. UUID breaks remaining ties deterministically. Both require an explicit category skill, an enabled account, availability and spare capacity. Default capacity is 5; admins configure 1–1000. Manual selection uses the same checks and is audited, with an optional reason. Existing work is not rebalanced. Empty pools produce a visible assignment issue; admins can retry with **Run selected assignment strategy**. Capacity cannot be reduced below workload, and skills required by active assignments cannot be removed. A category change incompatible with the assigned technician is rejected until the incident is returned to CLASSIFIED explicitly.

| Priority | Response | Resolution |
| --- | --- | --- |
| P1 | 5 minutes | 30 minutes |
| P2 | 15 minutes | 2 hours |
| P3 | 1 hour | 8 hours |
| P4 | 4 hours | 24 hours |

SLA uses 24/7 calendar time without pauses. An active service-specific policy overrides the priority’s global default. Admin policy input: `{serviceId,priority,responseSeconds,resolutionSeconds,warningPercent,active}`; null service means global. Resolution must be at least as long as response, targets are 1–31536000 seconds, and warning thresholds are 1–99% elapsed (default 80%).

All deadlines derive from original creation time. Priority/service/policy changes recalculate active incidents without resetting that clock. First actual acknowledgement is immutable for SLA purposes. Resolution without a separate acknowledgement fulfills response without inventing an acknowledgement. Completion exactly at the deadline is on time; strictly later is a breach. Response and resolution outcomes are independent. Recorded breaches survive extensions, downgrades and reopening. Completed incidents keep their agreed targets.

Missing policies never invent deadlines or discard a report: an explicit issue and audit entry are recorded. If an existing contract loses its policy, its previous targets remain in force with an issue. Monitoring drains batches of 100 in separate transactions each 15-second fixed-delay run, initially after 10 seconds. Warnings and violations are deduplicated in persistent snapshots/history; failed batches retain their cursor for retry. UI countdowns use returned server time. Warnings and violations dispatch notifications through the transactional outbox.

Optional Spring properties: `app.sla.monitor-enabled` (true), `app.sla.monitor-delay-ms` (15000), `app.sla.monitor-initial-delay-ms` (10000). Integration tests disable scheduling and invoke monitoring explicitly.

## Notifications, audit and intelligent features

Spring transaction events implement Observer delivery through interchangeable in-app and simulated email channels. The incident transaction persists an outbox event linked uniquely to its audit entry. Delivery starts **after commit**, in a separate transaction. Both channels commit atomically; failures retain the event for retry after exponential backoff (10 seconds to 5 minutes), polled every 15 seconds. Row locks and unique event/recipient constraints prevent duplicate messages during replay/concurrent workers. A crash after incident commit is recovered from the outbox. No external email provider or paid AI API is used.

Creation, assignment, calculated priority changes, response/resolution SLA warnings and breaches, resolution, closure and admin overrides to resolved/closed notify the reporter, current assigned technician and enabled administrators. Internal notes never produce notifications. Recipient IDs are captured with the event, and current incident access is checked again at delivery and inbox reads. Former technicians cannot access stale incident notifications after reassignment. In-app read/unread changes are persisted and idempotent. Simulated delivery logs are administrator-only.

The navigation includes a notification center, unread badge, administrator email logs and editable category rules. Incident history displays actor, timestamp and JSON old/new values. PostgreSQL rejects updates/deletes to incident history; APIs expose history only through authorized incident reads, hiding internal records from employees. V4 preserves legacy history and marks its available status snapshots `legacy=true`; unavailable historic field values are not invented.

Duplicate suggestions use a deterministic, tested fallback to `pg_trgm`: Unicode-normalized character trigram Dice similarity, 80% description and 20% title. Matching searches the newest 200 **accessible active incidents in the selected service**, returns at most five above 0.35, and excludes the current incident. This bounds query cost; older reports outside that window are not considered. Suggestions never merge reports or reveal another employee's incidents. Empty/very short descriptions do not generate similarity matches.

Category rules match whole normalized words/phrases in title/description. Matching active rules for active categories contribute weights; the highest score wins with deterministic ties. The form recommends a category while preserving user corrections. API clients can omit `categoryId` to apply the best rule; no match returns a validation error asking for a category. Supplying a category always takes precedence. Administrators can add/update/deactivate rules; changes are audited.

| API | Purpose |
| --- | --- |
| `GET /api/notifications?unreadOnly=false&page=0&size=20` | Current user's authorized inbox and unread count |
| `POST /api/notifications/{id}/read` with `{read:true}` | Mark own message read/unread |
| `GET /api/notifications/email-deliveries` | Administrator simulated delivery logs |
| `POST /api/intelligence/suggestions` | `{title,description,serviceId,excludeIncidentId?}` suggestions |
| `GET /api/intelligence/rules` | Administrator category rules |
| `PUT /api/intelligence/rules` | Upsert `{categoryId,keyword,weight,active}` |

The `dev` profile additionally seeds `network.tech@university.test`, `software.tech@university.test` and `standby.tech@university.test`, using `DEV_SEED_PASSWORD`. Fresh installations have two network assignments, one application assignment and an unavailable standby technician, with varied skills and capacities. These are real persisted demo incidents labelled **Development sample**. Existing accounts/profiles/settings are preserved; a transactionally locked version marker prevents replay after restart. Production/test profiles do not run these seeds. Existing deployments with custom capacity/availability may produce different demo assignments; qualification rules remain enforced.

Optional notification scheduling properties: `app.notifications.initial-delay-ms` (15000), `app.notifications.retry-delay-ms` (15000). Tests keep retry scheduling delayed to isolate deliberate failure cases while directly exercising the retry drain.

## Reporting and dashboards (Phase 5)

The employee, technician and administrator overview routes now load scoped PostgreSQL reports, recent incidents and persisted notifications. Administrators see organization-wide results, paginated service health and technician analytics; technicians see only their own performance/workload. Employees see only their own incident statistics. The existing role-check dashboard endpoints remain compatible.

Report controls combine incident ID, status, priority, category, service, technician, inclusive UTC creation dates and SLA status. Charts support priority/category/status/SLA and month drill-down; the completed-compliance chart supports accessible segment inspection. The incident table adds SLA filtering, deterministic sorting and CSV export while retaining combined filters across pages. Loading, empty and failure/retry states do not manufacture zero statistics.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/reports` | Scoped totals, performance, status/priority/category/SLA distributions, monthly trends and incident-derived service health |
| `GET /api/reports/technicians?page=0&size=10` | Administrator team performance; technician's own row only; employees forbidden |
| `GET /api/reports/services?page=0&size=10` | Administrator service volume and health signals |
| `GET /api/reports/incidents.csv` | All matching authorized incidents, streamed as UTF-8 CSV |

All report endpoints accept the incident search filters (`id,status,priority,categoryId,serviceId,technicianId,createdFrom,createdTo,slaStatus`). SLA values: `TRACKING`, `APPROACHING`, `MET`, `BREACHED`, `UNCONFIGURED`. Combined status prioritizes a breach in either target, then missing configuration, then an approaching target; both targets must be met for `MET`. V5 PostgreSQL functions mirror domain SLA semantics, retain recorded breach evidence, and are shared by reports, CSV and list filtering. Reports capture one microsecond-precision observation time in a read-only repeatable-read transaction.

Incident lists and CSV accept `sort` and `direction=asc|desc`. Allowed sort fields: `id,title,status,priority,createdAt,updatedAt,resolvedAt,service,category,technician,responseDueAt,resolutionDueAt`. Default: creation time descending. Incident ID provides a deterministic tie-breaker; null ordering follows PostgreSQL. Incident page size is 1–100; report table size is 1–100, default 10. CSV includes the entire filtered cohort, not only the current page, and has no silent row truncation. PostgreSQL cursor fetches use batches of 500 inside the export transaction. CSV quotes commas, double quotes and line breaks, neutralizes formula-leading text, excludes private investigation bodies, and uses `Cache-Control: no-store`. Export uses the normal authenticated API client, without exposing a token in the URL.

Metric definitions:

- **Open:** NEW through IN_PROGRESS. **Resolved:** RESOLVED, VERIFIED or CLOSED. **Critical:** open P1 incidents.
- **SLA violations:** distinct incidents with either computed or historically recorded breach. Response/resolution violation counts are separate; an incident breaching both counts once in the headline total.
- **Completed SLA compliance:** completed incidents meeting both targets / completed incidents with both deadlines configured. Open and unconfigured incidents are excluded from this denominator; an empty denominator returns null, never a fabricated 100%.
- **Resolution performance:** mean elapsed seconds from original creation to current resolution for completed incidents with a valid duration. First-response mean uses the persisted first actual response. Feedback is the mean persisted rating. Missing averages remain null.
- **Dates/trends:** reports use creation-date cohorts with inclusive UTC boundaries. Monthly trends show reports created in that month and how many are currently resolved, rather than pretending those resolutions happened in the creation month.
- **Workload:** live active assignments across all dates, independent of report filters. Cohort assignments/resolutions and performance use current technician ownership; reassigned historical work is not double-counted. Idle/unavailable/disabled technicians remain visible to administrators.
- **Service health:** selected-cohort incident signals, not uptime. CRITICAL = an open P1 or open breached incident; DEGRADED = other open work; CLEAR = matching reports but no open work. Services without matching reports are excluded, never assumed healthy.

Aggregations run in dedicated reporting services/repositories and return projections rather than hydrating incident collections. Workload uses grouped SQL and report tables paginate in PostgreSQL. Incident list DTOs fetch their four to-one relationships in one entity graph; the integration test verifies a full paginated fetch takes exactly two statements (data + count). V5 adds reporting functions/indexes without modifying existing data or Docker volumes. No chart dependency or external analytics service is required.


