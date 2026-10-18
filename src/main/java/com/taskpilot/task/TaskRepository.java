package com.taskpilot.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(TaskStatus status);

    /**
     * Agenda membership (§30): unfinished tasks scheduled on or before today, or due on or
     * before today. "scheduledFor local date <= today" is expressed as an instant comparison
     * against the start of tomorrow in the configured zone, so the result never depends on the
     * database session's timezone. No ORDER BY: the design defines no Agenda ordering.
     */
    @Query("""
            select t from Task t
            where t.status <> com.taskpilot.task.TaskStatus.COMPLETED
              and (t.scheduledFor < :startOfTomorrow or t.deadline <= :today)
            """)
    List<Task> findAgendaTasks(@Param("today") LocalDate today,
                               @Param("startOfTomorrow") OffsetDateTime startOfTomorrow);

    /**
     * Activation candidates (§41): SCHEDULED with scheduledFor's local date <= today, using the
     * same start-of-tomorrow bound as Agenda. Served by idx_tasks_status_scheduled_for. IDs only:
     * the worker reloads each candidate in its own transaction.
     */
    @Query("""
            select t.id from Task t
            where t.status = com.taskpilot.task.TaskStatus.SCHEDULED
              and t.scheduledFor < :startOfTomorrow
            """)
    List<UUID> findDueScheduledTaskIds(@Param("startOfTomorrow") OffsetDateTime startOfTomorrow);
}
