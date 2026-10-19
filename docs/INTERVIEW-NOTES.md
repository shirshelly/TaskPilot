# TaskPilot — Interview Talking Points

Short, defensible points about the design. Each links an engineering choice to the problem it
solves and the trade-off it accepts. Details: [`V1-DESIGN.md`](V1-DESIGN.md).

## Backend

**Domain state machine.** `SCHEDULED → ACTIVE → COMPLETED`, with `reopen` and `schedule` as
explicit commands on `Task`. There is no `setStatus`; invariants live in the entity and are
repeated as database `CHECK` constraints (e.g. `COMPLETED ⇔ completed_at IS NOT NULL`). Invalid
transitions are `409 INVALID_STATE_TRANSITION`. *Why:* behaviour stays next to the data it
protects, and the API can't drive the task into an impossible state.

**Transactions and optimistic locking.** Services own transaction boundaries and rely on dirty
checking; `@Version` turns a lost update into an exception at commit. *Trade-off:* no locks are
held, at the cost of occasionally asking the client to retry.

**Conditional PATCH with `If-Match`.** `@Version` alone only catches transactions that overlap by
milliseconds; a user who edits for five minutes would silently overwrite changes. The client
sends the version it read; the service compares it inside the update transaction *before
applying any field* → `412 VERSION_CONFLICT`. A race between that check and commit is still
caught by `@Version` → `409 VERSION_CONFLICT`. Validation order follows the real request
pipeline (body binding → header → 404 → 412 → domain 400), and tests pin it.

**Presence-aware PATCH.** Omitted vs. `null` vs. value, implemented with setter-recorded
presence because Jackson can't distinguish them in a record. Unknown and lifecycle fields are
rejected rather than ignored.

**Structured errors.** RFC 9457 Problem Details with a stable `code`, built on Spring's
`ResponseEntityExceptionHandler`: domain messages are safe to show, framework errors keep generic
details, unexpected exceptions are logged and answered generically (tested for leaks).

**Background activation.** A fixed-delay worker activates due `SCHEDULED` tasks using
`scheduledFor local date <= today`, so a missed run or downtime is recovered automatically. One
transaction per task; an optimistic-lock conflict skips only that task until the next run.

**Recommendation eligibility vs. ranking.** A pipeline: actionable (`ACTIVE`) → fits the time →
deterministic score (importance, deadline bands, capped age) with a fixed tie-breaker chain. The
minimum session changes *eligibility only* ("can I make progress in 15 minutes?"), never the
score — two concerns, two places.

**Agenda ordering.** Tiers by why a task is on today's plate (missed deadline, missed schedule,
due today, due soon, planned), then by date and time of day. Sorted in the service to reuse
`Task.isDeadlineOverdue` / `isScheduleOverdue` instead of restating them in JPQL.

**Timezones.** One configured zone and an injected `Clock`. "Scheduled on or before today" is an
instant comparison against the start of tomorrow in that zone, so queries never depend on the
database or JVM timezone. `GET /app-info` exposes the zone so the client uses the same one.

**Schema evolution.** Flyway owns the schema (`ddl-auto=validate`); the minimum-session feature
was an append-only V2 migration with a nullable column, so existing rows and clients kept working.

## Frontend / full stack

**TanStack Query as the server-state layer.** One `['tp', …]` key root; every mutation is
server-confirmed and awaits invalidation of all task views, because Agenda membership, overdue
flags and ranking are backend decisions the client shouldn't predict. Views on screen poll every
60 s while visible, refetch on focus, and refresh when the date rolls over in the app zone.

**Version-aware editing.** A pure `buildPatch(base, draft)` sends only changed fields (cleared
→ `null`). On a conflict, `rebaseDraft` re-applies only the user's own changes onto the latest
version, so a server-only change can never be reverted; fields changed on both sides need
explicit confirmation. Nothing is retried or merged automatically.

**Session-aware completion without backend complexity.** Tasks with a minimum session ask
*"What did you finish?"*. One hook (`useCompletionFlow`) is the only way any component completes a
task, so no entry point can bypass it. "I'm done for now" records nothing — honest about V1 having
no time tracking — and adds no status, type or endpoint.

**Exact unit conversion.** Hours are converted to whole minutes with integer arithmetic; inputs
that aren't whole minutes are rejected, never rounded. Switching units preserves the underlying
value, so a unit change alone never produces a PATCH.

**Accessibility and resilience.** Radix primitives, focus returned to the originating row after
programmatic dialogs close, a skip link, reduced-motion support, 44 px touch targets on phones;
axe-core clean (WCAG 2.1 AA) at desktop and 320 px. Errors are explained by code; failed refreshes
keep stale data visible with a retry.

**Restraint.** No global store, no optimistic UI, no form library, no backend grouping or
pagination: each was considered and rejected for a concrete reason, which is itself a talking
point.
