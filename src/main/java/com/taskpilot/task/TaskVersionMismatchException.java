package com.taskpilot.task;

import java.util.UUID;

/**
 * The client's expected version (from If-Match) no longer matches the task: someone changed it
 * since the client read it (§42). Detected before any change is applied, unlike an
 * optimistic-locking failure, which surfaces only when the transaction commits.
 */
public class TaskVersionMismatchException extends RuntimeException {

    public TaskVersionMismatchException(UUID id, long expectedVersion) {
        super("Task " + id + " has changed since version " + expectedVersion + " was read");
    }
}
