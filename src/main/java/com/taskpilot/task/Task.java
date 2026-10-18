package com.taskpilot.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    public static final int MIN_IMPORTANCE = 1;
    public static final int MAX_IMPORTANCE = 5;
    public static final int DEFAULT_IMPORTANCE = 3;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    // Explicit SMALLINT mapping so ddl-auto=validate matches the smallint column exactly.
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "importance", nullable = false)
    private int importance;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "scheduled_for")
    private OffsetDateTime scheduledFor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Task() {
        // required by JPA
    }

    private Task(UUID id, String title, String description, TaskStatus status, int importance,
                 Integer estimatedDurationMinutes, LocalDate deadline, OffsetDateTime scheduledFor,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.importance = importance;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.deadline = deadline;
        this.scheduledFor = scheduledFor;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(String title,
                               String description,
                               Integer importance,
                               Integer estimatedDurationMinutes,
                               LocalDate deadline,
                               OffsetDateTime scheduledFor,
                               Clock clock) {
        String validatedTitle = requireNonBlankTitle(title);
        int resolvedImportance = importance == null ? DEFAULT_IMPORTANCE : requireValidImportance(importance);
        Integer validatedDuration = requireValidDuration(estimatedDurationMinutes);

        Instant now = clock.instant();
        // Same-day-or-past scheduledFor activates immediately; only a strictly future date defers it (§23).
        TaskStatus initialStatus = isStrictlyFutureLocalDate(scheduledFor, clock)
                ? TaskStatus.SCHEDULED
                : TaskStatus.ACTIVE;

        return new Task(UUID.randomUUID(), validatedTitle, description, initialStatus, resolvedImportance,
                validatedDuration, deadline, scheduledFor, now, now);
    }

    // --- lifecycle operations ---

    public void complete(Clock clock) {
        if (status == TaskStatus.COMPLETED) {
            throw new TaskStateException("Task is already completed");
        }
        // ACTIVE or SCHEDULED may both complete directly (§26) so a passed deadline
        // always has a resolution path from the Agenda without forcing activation first.
        Instant now = clock.instant();
        this.status = TaskStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void reopen(Clock clock) {
        if (status != TaskStatus.COMPLETED) {
            throw new TaskStateException("Only a completed task can be reopened");
        }
        this.status = TaskStatus.ACTIVE;
        this.completedAt = null;
        this.updatedAt = clock.instant();
        // scheduledFor is intentionally left untouched: reopen does not imply reschedule (§27).
    }

    public void schedule(OffsetDateTime newScheduledFor, Clock clock) {
        if (status == TaskStatus.COMPLETED) {
            throw new TaskStateException("A completed task cannot be scheduled directly; reopen it first");
        }
        if (newScheduledFor == null) {
            throw new TaskValidationException("scheduledFor is required");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate newLocalDate = localDateOf(newScheduledFor, clock);
        if (newLocalDate.isBefore(today)) {
            throw new TaskValidationException("scheduledFor must not be in the past");
        }
        this.scheduledFor = newScheduledFor;
        // Resolving to today activates immediately rather than waiting for the worker (§25).
        this.status = newLocalDate.isAfter(today) ? TaskStatus.SCHEDULED : TaskStatus.ACTIVE;
        this.updatedAt = clock.instant();
    }

    public void activate(Clock clock) {
        if (status != TaskStatus.SCHEDULED) {
            throw new TaskStateException("Only a scheduled task can be activated");
        }
        if (scheduledFor == null) {
            throw new IllegalStateException("Scheduled task is missing scheduledFor");
        }
        if (!isDueForActivation(clock)) {
            throw new TaskStateException("Task is not due yet");
        }
        this.status = TaskStatus.ACTIVE;
        this.updatedAt = clock.instant();
        // scheduledFor is intentionally preserved after activation (§7).
    }

    /**
     * The single definition of "due" (§41): SCHEDULED and scheduledFor's local date is today or
     * earlier. Lets the worker skip a candidate that changed since it was queried, without using
     * exceptions for an expected condition; activate() stays strict and relies on the same rule.
     */
    public boolean isDueForActivation(Clock clock) {
        return status == TaskStatus.SCHEDULED
                && scheduledFor != null
                && !localDateOf(scheduledFor, clock).isAfter(LocalDate.now(clock));
    }

    /** Overdue (§11) is computed, never stored: a passed deadline or a passed schedule date. */
    public boolean isOverdue(Clock clock) {
        return isDeadlineOverdue(clock) || isScheduleOverdue(clock);
    }

    public boolean isDeadlineOverdue(Clock clock) {
        return deadline != null && deadline.isBefore(LocalDate.now(clock));
    }

    /**
     * ACTIVE only: a SCHEDULED task whose date has passed is waiting for the worker, not overdue.
     * Because scheduledFor is preserved after activation (§7), an activated task reads as
     * schedule-overdue from the next day until it is completed or rescheduled (§11).
     */
    public boolean isScheduleOverdue(Clock clock) {
        return status == TaskStatus.ACTIVE
                && scheduledFor != null
                && localDateOf(scheduledFor, clock).isBefore(LocalDate.now(clock));
    }

    // --- non-lifecycle updates ---

    public void rename(String newTitle, Clock clock) {
        this.title = requireNonBlankTitle(newTitle);
        this.updatedAt = clock.instant();
    }

    public void updateDescription(String newDescription, Clock clock) {
        this.description = newDescription;
        this.updatedAt = clock.instant();
    }

    public void updateImportance(int newImportance, Clock clock) {
        this.importance = requireValidImportance(newImportance);
        this.updatedAt = clock.instant();
    }

    public void updateEstimatedDurationMinutes(Integer newEstimatedDurationMinutes, Clock clock) {
        this.estimatedDurationMinutes = requireValidDuration(newEstimatedDurationMinutes);
        this.updatedAt = clock.instant();
    }

    public void updateDeadline(LocalDate newDeadline, Clock clock) {
        this.deadline = newDeadline;
        this.updatedAt = clock.instant();
    }

    // --- invariant helpers ---

    private static String requireNonBlankTitle(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new TaskValidationException("title must not be blank");
        }
        return candidate;
    }

    private static int requireValidImportance(int candidate) {
        if (candidate < MIN_IMPORTANCE || candidate > MAX_IMPORTANCE) {
            throw new TaskValidationException(
                    "importance must be between " + MIN_IMPORTANCE + " and " + MAX_IMPORTANCE);
        }
        return candidate;
    }

    private static Integer requireValidDuration(Integer candidate) {
        if (candidate != null && candidate <= 0) {
            throw new TaskValidationException("estimatedDurationMinutes must be positive");
        }
        return candidate;
    }

    private static boolean isStrictlyFutureLocalDate(OffsetDateTime candidate, Clock clock) {
        return candidate != null && localDateOf(candidate, clock).isAfter(LocalDate.now(clock));
    }

    private static LocalDate localDateOf(OffsetDateTime dateTime, Clock clock) {
        return dateTime.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getImportance() {
        return importance;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public OffsetDateTime getScheduledFor() {
        return scheduledFor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Task task)) {
            return false;
        }
        return id != null && id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
