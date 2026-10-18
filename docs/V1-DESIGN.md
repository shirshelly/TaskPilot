# Smart Personal Task Manager — V1 Design

## 1. Purpose

This is a backend-focused personal project intended to demonstrate real backend engineering ability rather than build another basic Todo application.

The system should help answer:

1. **What do I have today?**
2. **What should I focus on right now?**

The project should demonstrate:

- system design
- API design
- database modeling
- business logic
- state management
- transactions
- concurrency
- background processing
- reliability
- testing
- scalability thinking
- engineering trade-offs

Initial stack:

- Java 21
- Spring Boot 4.1.x
- PostgreSQL

Future infrastructure such as Redis, queues, Docker workers, Kafka, RabbitMQ, or additional services should only be added when there is a concrete architectural reason.

---

## 2. V1 Scope

V1 includes:

- Task management
- Task lifecycle
- Scheduled activation
- Today's Agenda
- Recommendations
- Application logging
- Testing
- PostgreSQL persistence
- Flyway migrations

V1 does **not** include:

- recurring tasks
- dependencies
- automatic schedule generation
- calendar integration
- natural-language AI input
- voice input
- analytics
- persistent task history
- multi-user support
- authentication/authorization
- Redis
- queues
- Kafka/RabbitMQ
- microservices

These may be added later when there is a real use case.

---

## 3. Core Use Cases

### 3.1 Today's Agenda

The Agenda answers:

> What has been assigned to today or must be in my attention today?

It is **not** a list of every task that is currently possible to perform.

Examples that should appear:

- a task scheduled for today
- an appointment scheduled for today
- a task with a deadline today
- a scheduled task from a previous day that was not completed
- a task whose deadline has already passed

Example that should not automatically appear:

- `Buy plant`
- `status = ACTIVE`
- no schedule
- no deadline

The Agenda is a computed query/view. It is not a persisted entity.

### Agenda rollover

A task scheduled for a previous day does not disappear automatically.

If it remains unfinished, it continues to appear in the Agenda.

The user may:
- complete it
- reschedule it to today or a future date
- hard-delete it

---

### 3.2 Recommendations

Recommendations answer:

> Out of the things I can actually do now, what should I focus on?

The pipeline is intentionally separated:

```text
Tasks
  ↓
Actionability filtering
  ↓
Eligible tasks
  ↓
Recommendation ranking
```

The ranking engine must not decide whether a task is actionable.

In V1, ranking may consider:

- importance
- deadline proximity
- estimated duration
- task age

The system should support:

```text
GET /recommendations?availableMinutes=20
```

When a strict `availableMinutes` value is supplied:
- tasks whose estimated duration exceeds the limit are excluded
- tasks with unknown estimated duration are excluded in V1

Without `availableMinutes`, tasks with unknown duration may still participate.

The recommendation algorithm should be deterministic in V1.

No AI is required for ranking.

---

## 4. Task Domain Model

Current V1 domain fields:

```text
Task
----
id
title
description
status
importance
estimatedDurationMinutes
deadline
scheduledFor
createdAt
updatedAt
completedAt
```

This is a domain model, not necessarily a direct statement that every concept must become a separate entity.

`Task` is the only persistent domain entity in V1.

---

## 5. Statuses

V1 has exactly three statuses:

```text
SCHEDULED
ACTIVE
COMPLETED
```

There is no `WAITING`.

There is no `OVERDUE` status.

There is no `DELETED` status.

### SCHEDULED

The task exists but is not currently actionable.

It must have a `scheduledFor` value.

### ACTIVE

The task is open and currently actionable.

`ACTIVE` does **not** mean "assigned to today".

An active task may:
- have no schedule
- have a schedule for today
- retain a schedule in the past after activation

### COMPLETED

The task is finished and has no currently planned continuation.

In V1, this applies to non-recurring tasks because recurrence is not implemented yet.

---

## 6. State Machine

Core transitions:

```text
SCHEDULED → ACTIVE
ACTIVE → COMPLETED
SCHEDULED → COMPLETED
COMPLETED → ACTIVE
ACTIVE → SCHEDULED
SCHEDULED → SCHEDULED
```

### SCHEDULED → ACTIVE

Actor:
- SYSTEM

Condition:

```text
scheduledFor local date <= today
```

The comparison is intentionally `<=`, not `==`.

This allows the system to recover correctly after downtime or a missed worker execution.

### ACTIVE → COMPLETED

Actor:
- USER

Effects:

```text
status = COMPLETED
completedAt = now
updatedAt = now
```

Completing an already completed task is an invalid transition.

### SCHEDULED → COMPLETED

Actor:
- USER

Effects are identical to `ACTIVE → COMPLETED`:

```text
status = COMPLETED
completedAt = now
updatedAt = now
```

This is an intentional exception to actionability-gated completion: the user may dismiss a
`SCHEDULED` task as done without first activating it. It exists so a task whose `deadline`
has passed while it is still `SCHEDULED` for a future date always has a resolution path from
the Agenda (see §30, §11).

### COMPLETED → ACTIVE

Actor:
- USER

This is an explicit reopen operation.

Effects:

```text
status = ACTIVE
completedAt = null
updatedAt = now
```

Only a COMPLETED task may be reopened.

### ACTIVE → SCHEDULED

Actor:
- USER

This is a defer/schedule operation.

A new future `scheduledFor` must be provided.

The task becomes non-actionable until its scheduled date arrives.

### SCHEDULED → SCHEDULED

Actor:
- USER

This is a reschedule operation.

The status remains SCHEDULED while `scheduledFor` changes.

---

## 7. Domain Invariants

### SCHEDULED

```text
scheduledFor != null
completedAt == null
```

When a user explicitly schedules or reschedules a task in V1:

```text
scheduledFor local date >= today
```

Scheduling or rescheduling to today is allowed. When the resulting `scheduledFor` local date
is today, the schedule operation derives status the same way task creation does (§23): the
task becomes `ACTIVE` immediately rather than remaining `SCHEDULED` for the worker to pick up
on its next run.

The database should not attempt to enforce the "future relative to today" rule because it is dynamic and timezone-dependent. That is business logic.

### ACTIVE

```text
completedAt == null
```

`scheduledFor` may be:
- null
- today
- in the past

The old scheduled date is intentionally preserved after activation.

### COMPLETED

```text
completedAt != null
```

The old `scheduledFor` and `deadline` values are preserved.

They are part of the task's business context and are not cleared on completion.

### General invariants

```text
importance ∈ [1, 5]
```

If `estimatedDurationMinutes` exists:

```text
estimatedDurationMinutes > 0
```

A deadline may be in the past.

Scheduling a task to a date after its deadline is allowed by the backend.

This may later produce a UI warning, but it is not prohibited by the domain.

---

## 8. Actionability

In V1, actionability is intentionally simple:

```text
actionable(task) = task.status == ACTIVE
```

Therefore:

```text
SCHEDULED  → not actionable
ACTIVE     → actionable
COMPLETED  → not actionable
```

Future dependencies may extend the predicate without requiring a `WAITING` status.

For example:

```text
status == ACTIVE
AND dependencies satisfied
```

---

## 9. scheduledFor

`scheduledFor` stores a full date and time.

Purpose:

> The moment/date when the task should return to attention and become relevant.

In V1, automatic activation uses only the local **date** portion.

Example:

```text
Dental hygienist
scheduledFor = 2026-08-25 16:00
```

The task becomes ACTIVE at the start of August 25 according to the configured user/application timezone.

The 16:00 time is still stored for future calendar/scheduling support.

V1 intentionally uses `scheduledFor` for scheduled tasks and follow-up/check-in style tasks rather than separate fields such as:

- `availableFrom`
- `scheduledAt`
- `followUpAt`

No fixed-event entity exists in V1.

Appointments are represented as scheduled tasks.

---

## 10. Deadline

`deadline` is separate from `scheduledFor`.

Meaning:

> The date by which the task should be completed.

It does not determine when the task becomes actionable.

Java/domain type:

```text
LocalDate
```

PostgreSQL type:

```text
DATE
```

Example:

```text
Homework
status = ACTIVE
deadline = Thursday
```

The task is actionable before Thursday.

---

## 11. Overdue

`OVERDUE` is not a status.

It is computed.

In V1, a task may be considered overdue because:

1. its deadline passed, or
2. its scheduled date passed and it remains unfinished/actionable

Conceptually:

```text
isDeadlineOverdue
isScheduleOverdue
```

The public V1 response may expose only:

```text
isOverdue
```

rather than separate flags.

### V1 formula

```text
isDeadlineOverdue =
    deadline != null
    AND deadline < today

isScheduleOverdue =
    status == ACTIVE
    AND scheduledFor != null
    AND (scheduledFor converted to local date via configured ZoneId) < today

isOverdue = isDeadlineOverdue OR isScheduleOverdue
```

`today` is derived from the injected `Clock` and configured `ZoneId`, consistent with §19.

Note: because `scheduledFor` is preserved after activation (§7), an `ACTIVE` task that
originated from a schedule will read as schedule-overdue starting the day after it activates,
and will stay that way until it is completed or rescheduled. This is intentional given the
current formula, not a bug to fix later.

---

## 12. Importance

Importance uses a simple numeric scale:

```text
1..5
```

Suggested interpretation:

```text
1 = low
3 = normal
5 = very important
```

V1 default:

```text
3
```

There is intentionally no 1–100 scale.

---

## 13. Estimated Duration

`estimatedDurationMinutes` describes the user's estimate for completing the whole task.

Examples:

```text
Wash dishes      → 15
Homework         → 180
Backend project  → 1200
```

It may be null when the user does not know.

V1 does not include:

- task splitting
- work sessions
- preferred session duration
- splittable flag

Large tasks may remain ACTIVE over multiple real-world work sessions.

---

## 14. Persistence Model

V1 has one persistent business entity:

```text
Task
```

There is no persistent history table in V1.

Agenda and Recommendations are computed use cases, not entities.

There is no `User` entity in V1.

The application is intentionally single-user.

This keeps focus on lifecycle, scheduling, business logic, persistence, background processing, and recommendations instead of authentication infrastructure.

---

## 15. Application Logging vs Business History

V1 uses application logs for debugging and observability.

Meaningful operations may be logged, for example:

```text
Task created
Task scheduled
Task rescheduled
Task activated
Task completed
Task reopened
Task deleted
```

These logs are **not** the business source of truth and should not later be parsed for analytics.

If a future feature requires persistent completion history, recurrence analytics, auditability, or user-visible history, a dedicated persistent representation should be added with a new migration.

---

## 16. Delete Semantics

Delete is hard delete.

Conceptually:

```text
DELETE task
→ database row is removed
```

There is no:
- soft delete
- `deletedAt`
- `DELETED` status

There is currently no retention/recovery requirement that justifies soft deletion.

---

## 17. PostgreSQL Schema Design

Logical V1 `tasks` table:

```text
tasks
-----
id                          UUID PRIMARY KEY
title                       VARCHAR(255) NOT NULL
description                 TEXT NULL

status                      VARCHAR(...) NOT NULL
importance                  SMALLINT NOT NULL DEFAULT 3
estimated_duration_minutes  INTEGER NULL

deadline                    DATE NULL
scheduled_for               TIMESTAMPTZ NULL

created_at                  TIMESTAMPTZ NOT NULL
updated_at                  TIMESTAMPTZ NOT NULL
completed_at                TIMESTAMPTZ NULL

version                     BIGINT NOT NULL DEFAULT 0
```

`version` backs JPA optimistic locking (`@Version`). See §21 and §42 for the concurrency
strategy this supports.

### Constraints

Status values are enforced using `VARCHAR + CHECK`, not a PostgreSQL enum type.

Allowed statuses:

```text
SCHEDULED
ACTIVE
COMPLETED
```

Importance:

```text
CHECK (importance BETWEEN 1 AND 5)
```

Estimated duration:

```text
CHECK (
  estimated_duration_minutes IS NULL
  OR estimated_duration_minutes > 0
)
```

Scheduled invariant:

```text
status = SCHEDULED
→ scheduled_for IS NOT NULL
```

Completed invariant:

```text
status = COMPLETED
→ completed_at IS NOT NULL

status != COMPLETED
→ completed_at IS NULL
```

Do not create a dynamic database constraint such as `scheduled_for > today`.

That rule belongs in business logic.

---

## 18. Indexes

Do not index fields without a demonstrated query need.

Initial justified index:

```text
(status, scheduled_for)
```

Primary use:

```text
find SCHEDULED tasks
where scheduled_for is due
```

Additional indexes, such as on `deadline`, should be added only when query behavior justifies them.

---

## 19. Java Time Types

Recommended representation:

```text
id            → UUID
deadline      → LocalDate
createdAt     → Instant
updatedAt     → Instant
completedAt   → Instant
```

`scheduledFor` may use a timezone-aware Java representation such as:

```text
OffsetDateTime
```

or an absolute `Instant` combined with the configured application/user timezone.

Important semantic requirement:

```text
scheduledFor
→ convert using configured ZoneId
→ extract LocalDate
→ compare with today
```

V1 is single-user and uses one configured timezone.

All time-dependent application logic must use an injected:

```text
Clock
```

Do not scatter direct `now()` calls through business code.

The application is responsible for lifecycle timestamps such as:

- `createdAt`
- `updatedAt`
- `completedAt`

---

## 20. UUID Strategy

Task IDs use UUID.

The application may generate IDs with:

```text
UUID.randomUUID()
```

This allows the domain object to have identity before persistence.

---

## 21. Flyway and Hibernate

Responsibility split:

### Flyway

Owns schema evolution.

Example future progression:

```text
V1__create_tasks_table.sql
V2__add_recurrence_fields.sql
V3__create_task_history.sql
```

Migrations are append-only historical changes.

Do not rewrite old migrations after they have become part of the project history unless still in very early local development and intentionally resetting the database.

### Hibernate / JPA

Used for:
- ORM mapping
- loading data
- inserting/updating/deleting data
- schema validation

Hibernate should **not** be the source of truth for schema mutation.

Do not rely on:

```text
ddl-auto=update
```

for production-style schema management.

Prefer validation against the Flyway-managed schema.

---

## 22. API Design

Initial V1 surface:

```text
POST   /tasks
GET    /tasks/{id}
GET    /tasks
PATCH  /tasks/{id}
DELETE /tasks/{id}

POST   /tasks/{id}/complete
POST   /tasks/{id}/reopen
POST   /tasks/{id}/schedule

GET    /agenda/today
GET    /recommendations
```

---

## 23. POST /tasks

The client does **not** send `status`.

Input fields:

```text
title
description?
importance?
estimatedDurationMinutes?
deadline?
scheduledFor?
```

Backend derives status.

Conceptually:

```text
if scheduledFor local date > today:
    status = SCHEDULED
else:
    status = ACTIVE
```

If `scheduledFor` is today, the task is created ACTIVE even if its stored time is later today.

This is intentional because V1 activation is date-based.

---

## 24. PATCH /tasks/{id}

Generic update is for non-lifecycle properties.

Allowed examples:

```text
title
description
importance
estimatedDurationMinutes
deadline
```

Do not allow generic patching of:

```text
status
completedAt
scheduledFor
```

`scheduledFor` changes through the explicit schedule/reschedule operation.

### Partial update semantics for nullable fields

`description`, `estimatedDurationMinutes`, and `deadline` are nullable and clearable.

Plain JSON→POJO binding cannot distinguish "field omitted" (leave unchanged) from "field
explicitly set to `null`" (clear it). The request DTO must use a presence-aware wrapper (for
example `JsonNullable<T>` or an `Optional<T>`-backed field) for each nullable field:

```text
field absent from JSON   → leave current value unchanged
field present as null    → clear the field
field present with value → set the field
```

---

## 25. POST /tasks/{id}/schedule

Request contains:

```text
scheduledFor
```

Allowed from:

```text
ACTIVE
SCHEDULED
```

Behavior:

```text
ACTIVE → SCHEDULED
```

or:

```text
SCHEDULED → SCHEDULED
```

for rescheduling.

If the resulting `scheduledFor` local date is today, the outcome is `ACTIVE` instead (see §7):

```text
ACTIVE → ACTIVE
```

or:

```text
SCHEDULED → ACTIVE
```

Direct scheduling of a COMPLETED task is not allowed.

The client must explicitly reopen it first.

---

## 26. POST /tasks/{id}/complete

Allowed only when:

```text
status = ACTIVE
OR
status = SCHEDULED
```

Completing a `SCHEDULED` task directly is intentional (see §6, §30): it gives the user a
resolution path for a task that shows up in the Agenda due to a passed deadline while still
`SCHEDULED` for a future date, without forcing an activation step first.

Effects:

```text
status = COMPLETED
completedAt = now
updatedAt = now
```

Invalid state transitions return `409 Conflict`.

---

## 27. POST /tasks/{id}/reopen

Allowed only when:

```text
status = COMPLETED
```

Effects:

```text
status = ACTIVE
completedAt = null
updatedAt = now
```

An old `scheduledFor` is intentionally retained.

Reopen does not imply reschedule.

---

## 28. DELETE /tasks/{id}

Hard delete.

Recommended success status:

```text
204 No Content
```

---

## 29. GET /tasks

General task listing.

V1 may support basic filtering such as:

```text
GET /tasks?status=ACTIVE
GET /tasks?status=SCHEDULED
GET /tasks?status=COMPLETED
```

Do not build a large generic filtering/query language in V1.

---

## 30. GET /agenda/today

Business endpoint.

The caller does not provide today's date.

The backend uses:

```text
Clock + configured ZoneId
```

Conceptually include non-completed tasks where at least one is true:

```text
scheduledFor local date <= today
OR
deadline <= today
```

A normal ACTIVE task with neither schedule nor deadline does not appear.

Agenda rollover is intentional.

---

## 31. GET /recommendations

Supported:

```text
GET /recommendations
GET /recommendations?availableMinutes=20
```

Pipeline:

```text
ACTIVE tasks
  ↓
actionability filtering
  ↓
optional duration eligibility
  ↓
deterministic ranking
```

Ranking may use:

- importance
- deadline proximity
- estimated duration
- age

Do not allow the ranker to redefine actionability.

---

## 32. Response DTO

Do not return JPA entities directly from controllers.

A V1 Task response may expose:

```text
id
title
description
status
importance
estimatedDurationMinutes
deadline
scheduledFor
createdAt
updatedAt
completedAt
isOverdue
```

`isOverdue` is computed and is not a database column.

---

## 33. HTTP Error Semantics

Use:

```text
404 Not Found
```

when the task does not exist.

Use:

```text
409 Conflict
```

when the request is valid but the task's current state does not allow the requested lifecycle operation.

Examples:
- completing an already completed task
- reopening a non-completed task
- scheduling a completed task directly

Use:

```text
400 Bad Request
```

for input/validation errors.

The domain should not know HTTP status codes.

API exception handling maps domain/application exceptions to HTTP responses.

---

## 34. Spring Boot Application Structure

Avoid both:
- a giant controller/service with all responsibilities
- over-engineered Clean Architecture with many adapter layers before they are needed

Suggested logical structure:

```text
com.example.taskmanager

task/
    Task
    TaskStatus
    TaskRepository
    TaskService
    TaskController
    dto/

agenda/
    AgendaService
    AgendaController

recommendation/
    RecommendationService
    RecommendationRanker
    RecommendationController

scheduling/
    ScheduledTaskActivator

common/
    ApiExceptionHandler
    ClockConfiguration
```

The structure may start somewhat flat and split into subpackages later if growth justifies it.

---

## 35. Domain Model Style

Use a richer domain model.

Prefer:

```text
task.complete(now)
task.reopen(now)
task.schedule(scheduledFor, now)
task.activate(now)
```

over:

```text
task.setStatus(...)
task.setCompletedAt(...)
```

The Task object should protect its own lifecycle invariants.

The service layer orchestrates use cases involving:
- repositories
- loading entities
- transaction boundaries
- external/application context

---

## 36. Service Layer

Start with a pragmatic:

```text
TaskService
```

rather than one class per use case.

Possible operations:

```text
create(...)
update(...)
complete(...)
reopen(...)
schedule(...)
delete(...)
```

Split into dedicated use-case services only if the class becomes meaningfully complex.

Do not create abstractions before they solve a real problem.

---

## 37. Controllers

Controllers should remain thin.

Flow:

```text
HTTP request
  ↓
validation / DTO mapping
  ↓
application service
  ↓
domain behavior
  ↓
response mapping
```

Do not put lifecycle business logic in controllers.

---

## 38. Persistence

Using the JPA entity directly as the domain `Task` is acceptable in V1.

There is no current requirement to create four separate layers such as:

```text
DomainTask
TaskJpaEntity
DomainTaskRepository
JpaTaskRepositoryAdapter
```

That separation may be introduced later if a real problem appears.

---

## 39. Agenda Structure

Agenda is a separate application/business use case.

Prefer:

```text
AgendaController
→ AgendaService
→ TaskRepository
```

rather than putting all agenda logic inside `TaskController`.

No `Agenda` table exists in V1.

---

## 40. Recommendation Structure

Suggested:

```text
RecommendationController
RecommendationService
RecommendationRanker
```

Keep eligibility and ranking conceptually separate.

The ranker should be deterministic and unit-testable.

No `Recommendation` table exists in V1.

---

## 41. Scheduled Activation Worker

A background scheduler periodically finds due scheduled tasks.

Conceptual query:

```text
status = SCHEDULED
AND scheduledFor local date <= today
```

Due tasks transition:

```text
SCHEDULED → ACTIVE
```

The system must not depend on the worker running at an exact second.

Using `<= today` allows recovery after downtime.

The scheduled component should reuse domain/application business logic rather than manually mutating task state.

Possible flow:

```text
ScheduledTaskActivator
  ↓
TaskService / activation use case
  ↓
Task domain behavior
  ↓
TaskRepository
```

---

## 42. Transaction Boundaries

Transactions belong in the application/service layer, not controllers.

Example:

```text
load task
  ↓
validate requested operation
  ↓
invoke domain transition
  ↓
persist
  ↓
commit
```

Future persistent history can be added to the same transaction if/when required.

### Concurrency

The scheduled activation worker and user-initiated API calls (`complete`/`reopen`/`schedule`)
both mutate the same `Task` rows and can race.

V1 uses optimistic locking via JPA `@Version` (see §17's `version` column).

For API requests:

```text
stale write detected
→ 409 Conflict
```

This is consistent with the existing invalid-state-transition mapping (§33).

For the scheduled worker:

```text
stale write detected during activation
→ skip this row for the current run
→ do not retry in-place, do not fail the batch
→ next scheduled run re-evaluates the row's current state
```

A concurrent modification must never be silently overwritten by an older read. Lifecycle
validation stays in the domain model regardless of locking strategy (§35).

---

## 43. Testing Strategy

Use multiple layers with different responsibilities.

### Domain unit tests

Most business rules should be tested here.

No Spring context.
No real DB.

Examples:

```text
ACTIVE → complete → COMPLETED
completedAt set
```

```text
COMPLETED → complete
→ invalid state
```

```text
COMPLETED → reopen
→ ACTIVE
→ completedAt cleared
```

```text
ACTIVE → schedule(future)
→ SCHEDULED
```

```text
importance outside 1..5
→ invalid
```

### Service tests

Test orchestration that the domain object cannot own.

Examples:

- missing task → `TaskNotFoundException`
- repository interaction
- application Clock usage
- transaction-oriented flow

Do not reduce service testing to meaningless mock verification.

### PostgreSQL integration tests

Use PostgreSQL rather than H2.

Prefer Testcontainers.

Test:
- entity mapping
- persistence
- constraints
- date/time mapping
- scheduled task queries
- Agenda-related queries

### API integration tests

A smaller set of full-path tests:

```text
HTTP
→ Controller
→ Service
→ Repository
→ PostgreSQL
```

Examples:

```text
POST /tasks
→ 201
```

```text
POST /tasks/{id}/complete
→ success
```

```text
complete again
→ 409
```

```text
GET missing task
→ 404
```

---

## 44. Worker Tests

Use a fixed injectable Clock.

Example:

```text
today = Aug 25

Task A:
SCHEDULED
scheduledFor = Aug 25 18:00

Task B:
SCHEDULED
scheduledFor = Aug 26

Task C:
SCHEDULED
scheduledFor = Aug 24
```

Expected activation:

```text
A → ACTIVE
B → SCHEDULED
C → ACTIVE
```

This verifies date-based behavior and downtime recovery semantics.

---

## 45. Agenda Tests

Important examples:

```text
ACTIVE + no deadline/schedule
→ not in Agenda
```

```text
ACTIVE + deadline today
→ in Agenda
```

```text
ACTIVE + scheduled yesterday
→ in Agenda
```

```text
COMPLETED + scheduled today
→ not in Agenda
```

```text
SCHEDULED tomorrow
→ not in Agenda
```

---

## 46. Recommendation Tests

Examples:

```text
availableMinutes = 20

Task A = 10 min
Task B = 30 min
Task C = unknown
```

Eligible:

```text
Task A only
```

Ranking tests should verify deterministic ordering for exact inputs.

Examples:
- higher importance increases rank
- closer deadline increases rank
- age may increase rank according to the selected formula

The exact ranking formula should be agreed before implementation rather than silently invented.

---

## 47. Test Philosophy

Test each business rule at the lowest layer that owns it.

Example:

If:

```text
Task.complete()
```

rejects double completion, test that thoroughly as a domain unit test.

Then only one or a few API tests are needed to prove that the domain exception maps to HTTP `409`.

Avoid duplicate test matrices across every layer.

Do not chase arbitrary 100% coverage.

---

# Future Roadmap

## Phase 2 — Recurring Tasks

Planned initial recurrence types:

```text
NONE
DAILY
WEEKLY
MONTHLY
```

Future fields/concepts may include:

```text
recurrence
recurrenceEnd
dayOfWeek
dayOfMonth
```

Recurrence is schedule-based rather than completion-based.

The same Task is reused rather than creating a new Task per occurrence.

If a recurring task is still ACTIVE when its next scheduled occurrence arrives, no duplicate task is created.

When recurring tasks are implemented, completion of an occurrence may be represented differently from terminal completion of the Task and will need a dedicated design pass.

Do not implement recurrence as part of V1.

## Phase 3 — Dependencies

Example:

```text
Update CV
↓
Apply
↓
Follow up
```

Dependencies may extend actionability.

Do not add `WAITING` preemptively.

## Phase 4 — Reliability / Async

Potential topics:
- retries
- idempotency
- concurrent workers
- locking
- queues, only if justified

## Phase 5 — Create Schedule for Today/Tomorrow

Future deterministic scheduling based on:
- fixed/scheduled tasks
- actionable tasks
- deadlines
- importance
- durations
- available time

Future additions may include:
- task splitting
- fixed events
- unavailable hours
- constraints
- conflict handling

## Phase 6 — Calendar

Potential:
- richer time model
- start/end
- calendar view
- Google Calendar integration

## Phase 7 — AI Natural Language Input

Planned pipeline:

```text
Natural language
↓
LLM
↓
Structured Task Draft
↓
Validation
↓
Domain Service
↓
Database
```

The LLM must not write directly to the database.

The backend remains the source of truth.

## Phase 8 — Voice

```text
Audio
↓
Speech-to-Text
↓
same structured AI command pipeline
↓
Domain
```

Voice is only an input adapter.

## Phase 9 — Analytics

Possible future analytics:
- completion rate
- estimation accuracy
- overdue patterns
- recurring completion history
- average task age

When persistent history becomes a real requirement, design and add it explicitly rather than parsing V1 application logs.
