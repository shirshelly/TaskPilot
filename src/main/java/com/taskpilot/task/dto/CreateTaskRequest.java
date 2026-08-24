package com.taskpilot.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        @Min(1) @Max(5) Integer importance,
        @Positive Integer estimatedDurationMinutes,
        LocalDate deadline,
        OffsetDateTime scheduledFor
) {
}
