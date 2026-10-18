package com.taskpilot.task.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

// "Not in the past" is deliberately not a bean-validation constraint: it is a local-date rule
// that needs the injected Clock and configured zone, so the domain owns it (Task.schedule).
public record ScheduleTaskRequest(
        @NotNull OffsetDateTime scheduledFor
) {
}
