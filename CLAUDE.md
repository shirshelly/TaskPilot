# CLAUDE.md

## Project

Smart Personal Task Manager — a backend-focused personal project intended to demonstrate real backend engineering skills.

Primary stack:
- Java
- Spring Boot
- PostgreSQL

The product should answer two core questions:
1. What do I have today?
2. What should I focus on right now?

## Source of Truth

Before making non-trivial changes, read:

- `docs/V1-DESIGN.md`

That document contains the current product and engineering decisions for V1.

Do not silently contradict or reinterpret decisions that are already documented there.

## Working Principles

- Do not expand scope unless explicitly asked.
- Do not implement roadmap features that are not part of V1.
- Do not introduce infrastructure or libraries only because they are common or look good on a CV.
- Before adding Redis, queues, Kafka, RabbitMQ, microservices, caching, extra workers, or similar infrastructure, identify the concrete current problem they solve.
- Prefer the simplest design that satisfies the current requirements.
- Significant engineering decisions must be surfaced explicitly instead of being made silently.
- Small implementation details do not require approval, but architectural/domain/API/schema decisions do.
- If an implementation request conflicts with `docs/V1-DESIGN.md`, stop and explain the conflict before changing code.
- When a requirement is ambiguous, prefer the interpretation most consistent with the existing design and flag the ambiguity.

## AI Collaboration Rules

The human owns:
- domain modeling
- architecture
- API design
- database modeling
- transaction boundaries
- concurrency decisions
- major trade-offs

Claude may help with:
- implementation proposals
- edge cases
- boilerplate
- tests
- refactoring
- code review
- implementation after the design is agreed

For non-trivial features:
1. Inspect the existing code and design document.
2. Propose an implementation plan first.
3. Identify any new engineering decision not already specified.
4. Do not make major design changes silently.
5. Implement only the agreed scope.
6. Prefer small, reviewable changes.

Every piece of code should remain understandable and explainable in a backend interview.

## Domain Principles

- `Task` is the core domain entity and a richer domain model, not a passive data bag.
- Do not expose arbitrary status mutation.
- Lifecycle changes should happen through explicit domain operations such as:
  - complete
  - reopen
  - schedule/reschedule
  - activate
- Business invariants should live as close as possible to the domain object that owns them.
- Controllers must stay thin.
- HTTP-specific concerns must not leak into the domain.
- `Agenda` is a computed use case/view, not an entity.
- `Recommendation` is computed, not persisted.
- History is not persisted in V1. Use application logging only for debugging/observability.
- Do not build analytics infrastructure in V1.

## Time Handling

- All time-dependent application logic must use an injected `Clock`.
- Do not call `now()` directly throughout business logic.
- `deadline` is date-only.
- `scheduledFor` stores a full timestamp.
- V1 activation is date-based: the time portion is stored but ignored when determining whether the task becomes actionable.
- V1 is single-user and uses one configured application/user timezone.

## Persistence

- PostgreSQL is the production database.
- Flyway owns schema creation and schema migrations.
- Hibernate/JPA maps entities and validates the schema.
- Do not use Hibernate automatic schema creation/update as the source of truth.
- Do not use H2 as a substitute for PostgreSQL integration testing.
- Prefer Testcontainers for persistence/integration tests.

## API Principles

- Clients do not set `status` directly when creating or updating a task.
- The backend derives lifecycle state from domain operations.
- Invalid state transitions should map to HTTP `409 Conflict`.
- Missing resources should map to `404 Not Found`.
- Request validation failures should map to `400 Bad Request`.
- Do not expose JPA entities directly from controllers; use request/response DTOs.

## Testing Principles

Test business rules at the lowest level that owns them.

Prefer:
- many domain unit tests
- focused service tests
- PostgreSQL integration tests with Testcontainers
- a smaller number of end-to-end API integration tests

Do not chase arbitrary 100% coverage.
Do not duplicate the same rule across every test layer.

## Implementation Style

Prefer small vertical slices.

Recommended early sequence:
1. Project skeleton and configuration.
2. PostgreSQL + Flyway setup.
3. `Task` + `TaskStatus` domain model and unit tests.
4. Initial `tasks` migration.
5. Repository.
6. `POST /tasks` vertical slice.
7. Read/list endpoints.
8. Update and lifecycle commands.
9. Agenda.
10. Recommendation engine.
11. Scheduled activation worker.
12. Integration and edge-case tests.

Do not implement the entire V1 in one large pass.
