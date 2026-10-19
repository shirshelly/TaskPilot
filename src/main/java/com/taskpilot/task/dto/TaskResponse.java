package com.taskpilot.task.dto;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        int importance,
        Integer estimatedDurationMinutes,
        Integer minSessionMinutes,
        LocalDate deadline,
        OffsetDateTime scheduledFor,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        boolean isOverdue,
        long version
) {

    public static TaskResponse from(Task task, Clock clock) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getImportance(),
                task.getEstimatedDurationMinutes(),
                task.getMinSessionMinutes(),
                task.getDeadline(),
                task.getScheduledFor(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                task.isOverdue(clock),
                task.getVersion());
    }
}
