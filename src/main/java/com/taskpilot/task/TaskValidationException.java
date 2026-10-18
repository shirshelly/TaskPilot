package com.taskpilot.task;

/**
 * Thrown when input to a {@link Task} operation violates a domain rule (e.g. scheduling into
 * the past). Distinct from {@link TaskStateException}: the request itself is invalid, regardless
 * of the task's current status. Extends IllegalArgumentException because that is what it is;
 * the dedicated subtype lets the API map exactly these to 400 without catching every IAE.
 */
public class TaskValidationException extends IllegalArgumentException {

    public TaskValidationException(String message) {
        super(message);
    }
}
