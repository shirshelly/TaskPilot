# TaskPilot

A backend-focused, single-user personal task manager. It answers two questions:

1. **What do I have today?** → `GET /agenda/today`
2. **What should I focus on right now?** → `GET /recommendations`

The project is deliberately small in scope and is meant to show backend engineering — domain
modeling, a lifecycle state machine, transactions, concurrency, scheduling, and time handling —
rather than CRUD alone. The full design lives in [`docs/V1-DESIGN.md`](docs/V1-DESIGN.md).

## Tech Stack

Java 21 · Spring Boot 4.1 · Spring Data JPA / Hibernate · PostgreSQL 16 · Flyway ·
JUnit 5 · AssertJ · Mockito · Testcontainers

## What Makes It More Than CRUD

- **Rich domain model.** `Task` owns its lifecycle (`complete`, `reopen`, `schedule`,
  `activate`) and invariants. There is no way to set `status` directly — not in the API, not via
  setters.
- **Schema owned by Flyway.** Hibernate runs with `ddl-auto=validate`; the database also enforces
  invariants with `CHECK` constraints (e.g. `COMPLETED ⇔ completed_at IS NOT NULL`).
- **Explicit transaction boundaries** in the service layer, using JPA dirty checking.
- **Optimistic locking** (`@Version`): a stale API write returns `409`; in the background worker it
  skips only the affected task. `PATCH` also accepts `If-Match: "<version>"`, so an edit based on
  an outdated read is refused with `412` before anything is applied.
- **Timezone-aware date semantics.** All time logic uses an injected `Clock` and one configured
  zone (`app.zone-id`). Date-based queries compare `TIMESTAMPTZ` values against zone-derived
  instant bounds, so results never depend on the JVM or database session timezone.
- **Scheduled background activation** of due tasks, recoverable after downtime.
- **Business queries, not just lists:** the Agenda and a deterministic recommendation ranking.
- **Consistent error semantics:** `400` invalid input, `404` missing task, `409` invalid lifecycle
  transition or concurrent modification — as RFC 9457 Problem Details with a stable `code`
  (`VALIDATION_FAILED`, `TASK_NOT_FOUND`, `INVALID_STATE_TRANSITION`, `VERSION_CONFLICT`, …) so
  clients never parse messages.
- **Integration tests against real PostgreSQL** (Testcontainers), not H2.

## Core Semantics

| Status | Meaning |
|---|---|
| `SCHEDULED` | Exists but is not actionable until its `scheduledFor` date arrives |
| `ACTIVE` | Open and actionable now |
| `COMPLETED` | Done; can be explicitly reopened |

- **`scheduledFor`** (timestamp): when the task should return to attention. V1 uses only its
  *local date*: a task scheduled for today is `ACTIVE` immediately, even if the time is later today.
- **`deadline`** (date): when it should be done. It never affects actionability.
- **Agenda** (`/agenda/today`): unfinished tasks whose `scheduledFor` local date is today or
  earlier, or whose `deadline` is today or earlier. Past days roll over until handled. A plain
  `ACTIVE` task with no schedule or deadline does not appear. Ordered most time-critical first:
  missed deadlines, missed schedules, due today, due within 3 days, then by scheduled time
  (dates only — no importance or scoring; see `docs/V1-DESIGN.md` §30).
- **Recommendations** (`/recommendations`): `ACTIVE` tasks only, optionally limited to those with
  a known duration ≤ `availableMinutes`, ranked by a deterministic score
  (`importance × 10` + bounded deadline-urgency points + age capped at 5) with a fixed
  tie-breaker chain. The formula is documented in `docs/V1-DESIGN.md` §31; scores are not exposed.
- **Scheduled activation:** every 5 minutes (configurable) a worker moves `SCHEDULED` tasks whose
  `scheduledFor` local date is today or earlier to `ACTIVE`. Because the rule is `<= today`, a
  missed run or downtime is caught up by the next run. Each task is activated in its own
  transaction; a concurrent modification skips only that task until the next run.

## API

| Method & path | Purpose | Success |
|---|---|---|
| `POST /tasks` | Create (status is derived, never sent) | `201` |
| `GET /tasks/{id}` | Read one | `200` |
| `GET /tasks[?status=ACTIVE\|SCHEDULED\|COMPLETED]` | List, optionally filtered; newest first (`COMPLETED`: most recently completed first) | `200` |
| `PATCH /tasks/{id}` | Partial update of `title`, `description`, `importance`, `estimatedDurationMinutes`, `deadline` | `200` |
| `DELETE /tasks/{id}` | Hard delete | `204` |
| `POST /tasks/{id}/complete` | `ACTIVE`/`SCHEDULED` → `COMPLETED` | `200` |
| `POST /tasks/{id}/reopen` | `COMPLETED` → `ACTIVE` | `200` |
| `POST /tasks/{id}/schedule` | Schedule/reschedule (`{"scheduledFor": ...}`); today → `ACTIVE`, future → `SCHEDULED` | `200` |
| `GET /agenda/today` | Today's Agenda | `200` |
| `GET /recommendations[?availableMinutes=N]` | Ranked actionable tasks | `200` |
| `GET /app-info` | Configured application timezone (`{"zoneId": ...}`) for clients | `200` |

PATCH distinguishes an absent field (unchanged) from an explicit `null` (cleared, for nullable
fields) and rejects unknown or lifecycle fields with `400`.

## Architecture

```text
Controller (HTTP, DTOs) → Service (transactions, orchestration) → Task (domain rules) → Repository → PostgreSQL
```

Separate use cases sit beside the task CRUD/lifecycle flow:

- `agenda/` — `AgendaController → AgendaService → TaskRepository` (one query, read-only)
- `recommendation/` — `RecommendationService`: actionability → duration eligibility → `RecommendationRanker`
- `scheduling/` — `ScheduledTaskActivator` → `TaskService.activateIfDue` (one transaction per task) → `Task.activate`

## Running Locally

**Prerequisites:** Java 21 and Docker. Maven is provided by the wrapper (`./mvnw`).

1. Start PostgreSQL with the app's default connection settings:
   ```bash
   docker run -d --name taskpilot-postgres \
     -e POSTGRES_DB=taskpilot -e POSTGRES_USER=taskpilot -e POSTGRES_PASSWORD=taskpilot \
     -p 5432:5432 postgres:16
   ```
2. Run the application (Flyway applies migrations on startup):
   ```bash
   ./mvnw spring-boot:run
   ```
3. Try it:
   ```bash
   curl -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
     -d '{"title": "Homework", "importance": 4, "estimatedDurationMinutes": 90, "deadline": "2026-12-01"}'
   curl localhost:8080/agenda/today
   curl 'localhost:8080/recommendations?availableMinutes=30'
   ```

Configuration (environment variables, defaults in `application.yml`):

| Variable | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/taskpilot` / `taskpilot` / `taskpilot` |
| `APP_ZONE_ID` | `Asia/Jerusalem` |
| `APP_ACTIVATION_ENABLED` | `true` |
| `APP_ACTIVATION_FIXED_DELAY` | `PT5M` |

## Frontend

A React + TypeScript app in [`frontend/`](frontend), built separately from the backend
(Vite, Tailwind CSS, shadcn/ui, TanStack Query, React Router): **Today** (Agenda + Focus
recommendations with an available-time filter) and **Tasks** (status filters), with quick
create, an edit sheet, complete / reopen / schedule / delete from each row.

Mutations are server-confirmed (no optimistic UI) and refresh every task view afterwards.
Edits send a minimal PATCH with `If-Match`; if the task changed elsewhere, the draft is kept and
the user can review the differences, discard, or save only their own changes over the latest
version.

**Prerequisites:** Node.js 22+ and the backend running on `localhost:8080`.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

The dev server proxies `/api/*` to Spring Boot with the prefix stripped, so the backend needs no
CORS configuration (`TASKPILOT_API_URL` overrides the target). The UI reads the application
timezone from `GET /app-info` and uses it for every date it shows or builds.

```bash
npm test           # Vitest: PATCH diff + conflict rebase, time/DST, errors, URL state, components
npm run typecheck
npm run build
```

To try the UI without touching your normal data, point the backend at a separate database:

```bash
docker exec taskpilot-postgres createdb -U taskpilot taskpilot_dev
DB_URL=jdbc:postgresql://localhost:5432/taskpilot_dev ./mvnw spring-boot:run
```

## Testing

```bash
./mvnw test
```

Docker must be running: persistence, API, and worker tests use a real PostgreSQL 16 container via
Testcontainers. The scheduled trigger is disabled in tests; worker tests invoke it directly with a
fixed `Clock`.

- **Domain unit tests** — lifecycle transitions, invariants, local-date rules (no Spring, no DB)
- **Service tests** — orchestration only (lookups, presence-aware PATCH, eligibility, Clock usage)
- **Ranker unit tests** — every score band, cap, and tie-breaker; input-order independence
- **PostgreSQL integration tests** — mapping, `CHECK` constraints, optimistic locking, Agenda and
  activation queries including timezone boundaries
- **API integration tests** — `HTTP → Controller → Service → Repository → PostgreSQL` via MockMvc,
  verifying committed state
- **Worker tests** — due/future/overdue activation, downtime recovery, and deterministic
  isolation of an optimistic-lock conflict

## Not in V1 / Roadmap

Intentionally out of scope for V1 and **not implemented**: authentication / multi-user,
recurring tasks, dependencies, automatic schedule generation, calendar integration, persistent
history/analytics, and any AI features. Future phases are described at the end of
`docs/V1-DESIGN.md`; optional AI capabilities (natural-language task capture, a tool-calling
assistant) are planned in `docs/AI-ROADMAP.md` for after the core backend is stable.
