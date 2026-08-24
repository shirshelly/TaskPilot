# TaskPilot

A backend-focused personal task manager, built to demonstrate real backend engineering — domain
modeling, state machines, persistence, and testing — rather than another basic to-do app.

The system is designed to answer two questions:

1. **What do I have today?**
2. **What should I focus on right now?**

(Both answers — Agenda and Recommendations — are part of the V1 design but not implemented yet;
see [Roadmap](#roadmap).)

## Tech Stack

- **Java 21**, **Spring Boot 4.1.x**
- **PostgreSQL 16**, schema owned by **Flyway** migrations
- **Spring Data JPA** / **Hibernate** for persistence (schema *validation* only — see below)
- **Testcontainers** for real-PostgreSQL integration tests (no H2)
- **JUnit 5**, **AssertJ**, **Mockito**

## Key Engineering Decisions

- **`Task` is a rich domain model, not a data bag.** No arbitrary setters for lifecycle state.
  Every state change goes through an explicit operation — `create`, `complete`, `reopen`,
  `schedule`, `activate` — each enforcing its own invariants (importance range, positive
  duration, schedule/reschedule operations reject past dates, no double-completion, etc.).
- **Status is always backend-derived.** Clients cannot set `status` directly, at creation or
  otherwise; the request DTO simply has no such field.
- **All time-dependent logic uses an injected `Clock`**, never `now()` directly. The app runs on
  a single configured timezone (`app.zone-id`, default `Asia/Jerusalem`), so "today" is
  deterministic and independent of the deploying machine's OS timezone.
- **Flyway is the schema source of truth.** Hibernate is configured with `ddl-auto=validate`
  only — it validates the entity mapping against the Flyway-created schema and never creates or
  alters tables itself.
- **Optimistic locking via `@Version`**, backed by a real `version` column, protecting against
  concurrent writes (e.g. a future scheduled-activation worker racing a user API call).
- **DTOs at the API boundary.** Controllers never return JPA entities directly.
- **Thin controllers.** HTTP concerns stop at the controller; business rules live in `Task`,
  orchestration lives in the service layer.

## Currently Implemented

- **Domain model**: `Task` (rich entity) and `TaskStatus` (`SCHEDULED` / `ACTIVE` / `COMPLETED`),
  with lifecycle operations `create`, `complete`, `reopen`, `schedule`, `activate`, validated
  non-lifecycle field updates, and a computed `isOverdue(Clock)`.
- **Persistence**: Flyway migration creating the `tasks` table (with `CHECK` constraints for
  status/importance/duration/schedule/completion invariants and a `(status, scheduled_for)`
  index) and a `TaskRepository` (Spring Data JPA).
- **API**: `POST /tasks` — creates a task. Client supplies `title` (required), and optionally
  `description`, `importance` (default `3`), `estimatedDurationMinutes`, `deadline`,
  `scheduledFor`. Status is derived: `SCHEDULED` if `scheduledFor`'s local date (in the
  configured zone) is after today, otherwise `ACTIVE`. Returns `201` with a `TaskResponse`;
  invalid input returns `400`.

**Not yet implemented**: `GET`/`PATCH`/`DELETE /tasks`, the `complete`/`reopen`/`schedule`
lifecycle endpoints, Agenda, Recommendations, the scheduled activation worker, and every item in
`docs/AI-ROADMAP.md` (natural-language task input, tool-calling assistant, guardrails, evals).
None of these are started.

## Local Setup

**Prerequisites**: Java 21, Docker (for PostgreSQL and for running the Testcontainers-based
tests).

1. Start PostgreSQL, matching the app's default connection settings:
   ```bash
   docker run -d --name taskpilot-postgres \
     -e POSTGRES_DB=taskpilot \
     -e POSTGRES_USER=taskpilot \
     -e POSTGRES_PASSWORD=taskpilot \
     -p 5432:5432 \
     postgres:16
   ```
2. Run the application (applies Flyway migrations on startup):
   ```bash
   ./mvnw spring-boot:run
   ```
3. Create a task:
   ```bash
   curl -X POST http://localhost:8080/tasks \
     -H "Content-Type: application/json" \
     -d '{"title": "Buy plant"}'
   ```

Connection settings are overridable via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and the
configured timezone via `APP_ZONE_ID`.

## Testing

```bash
./mvnw test
```

Docker must be running — the persistence and API tests spin up a real PostgreSQL 16 container
via Testcontainers rather than substituting H2, so constraint enforcement, optimistic locking,
and Flyway migration behavior are verified against the same database engine used by the application.

- **Domain unit tests** (`TaskTest`) — no Spring context, no database; cover status derivation
  and every lifecycle transition, valid and invalid.
- **Service tests** (`TaskServiceTest`) — Mockito-based, covering orchestration only (repository
  interaction, `Clock` usage), not business rules already covered above.
- **Persistence integration tests** (`TaskRepositoryTest`) — real Postgres via Testcontainers:
  full field-type round-tripping, `@Version` optimistic-lock conflicts, and each database `CHECK`
  constraint verified independently of `Task`'s own validation via raw JDBC inserts.
- **API integration tests** (`TaskCreationApiTest`) — end-to-end `HTTP → Controller → Service →
  Repository → PostgreSQL` via `MockMvc` against the same Testcontainers database.

## Roadmap

Remaining V1 work, in the order defined in `CLAUDE.md`:

- Read/list endpoints (`GET /tasks`, `GET /tasks/{id}`)
- Update and lifecycle command endpoints (`PATCH`, `complete`, `reopen`, `schedule`)
- Today's Agenda (`GET /agenda/today`)
- Recommendation engine (`GET /recommendations`)
- Scheduled activation worker (`SCHEDULED → ACTIVE` background transition)
- Broader integration and edge-case test coverage

AI features (natural-language task capture, a tool-calling assistant, guardrails/confirmation,
evals) are an explicit later phase per `docs/AI-ROADMAP.md`, intentionally deferred until the
core V1 backend and its API/domain rules are stable.
