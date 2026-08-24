package com.taskpilot.task;

/**
 * Thrown when a lifecycle operation is invoked on a {@link Task} whose current
 * status does not permit it (e.g. completing an already-completed task).
 */
public class TaskStateException extends RuntimeException {

    public TaskStateException(String message) {
        super(message);
    }
}
