package com.taskpilot.task.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.time.LocalDate;

/**
 * PATCH body for non-lifecycle fields (§24). Each field is tri-state:
 * absent (leave unchanged), present as null (clear), present with a value (set).
 *
 * Jackson only calls a setter for properties that appear in the JSON, including explicit
 * nulls, so each setter records presence. A record can't do this: Jackson resolves an absent
 * creator parameter to the same value as an explicit null. Lifecycle fields (status,
 * scheduledFor, completedAt) are intentionally absent from this type and rejected as unknown.
 *
 * Value rules (blank title, importance range, positive durations) are not repeated here; the
 * Task update methods own them.
 */
public class UpdateTaskRequest {

    private String title;
    private boolean titlePresent;

    private String description;
    private boolean descriptionPresent;

    private Integer importance;
    private boolean importancePresent;

    private Integer estimatedDurationMinutes;
    private boolean estimatedDurationMinutesPresent;

    private Integer minSessionMinutes;
    private boolean minSessionMinutesPresent;

    private LocalDate deadline;
    private boolean deadlinePresent;

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public void setImportance(Integer importance) {
        this.importance = importance;
        this.importancePresent = true;
    }

    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.estimatedDurationMinutesPresent = true;
    }

    public void setMinSessionMinutes(Integer minSessionMinutes) {
        this.minSessionMinutes = minSessionMinutes;
        this.minSessionMinutesPresent = true;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
        this.deadlinePresent = true;
    }

    /**
     * Rejects any property that isn't one of the setters above (lifecycle fields, typos), so an
     * unsupported PATCH is a 400 rather than a silent no-op. DTO-local on purpose: the global
     * Jackson config ignores unknown properties, and @JsonIgnoreProperties(ignoreUnknown = false)
     * cannot override that. Jackson wraps this exception, and Spring maps it to 400.
     */
    @JsonAnySetter
    private void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unsupported property for PATCH: " + name);
    }

    // hasX()/x() rather than isX()/getX(), so Jackson doesn't treat the flags as bindable properties.

    public boolean hasTitle() {
        return titlePresent;
    }

    public String title() {
        return title;
    }

    public boolean hasDescription() {
        return descriptionPresent;
    }

    public String description() {
        return description;
    }

    public boolean hasImportance() {
        return importancePresent;
    }

    public Integer importance() {
        return importance;
    }

    public boolean hasEstimatedDurationMinutes() {
        return estimatedDurationMinutesPresent;
    }

    public Integer estimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public boolean hasMinSessionMinutes() {
        return minSessionMinutesPresent;
    }

    public Integer minSessionMinutes() {
        return minSessionMinutes;
    }

    public boolean hasDeadline() {
        return deadlinePresent;
    }

    public LocalDate deadline() {
        return deadline;
    }
}
