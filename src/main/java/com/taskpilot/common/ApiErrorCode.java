package com.taskpilot.common;

/**
 * Stable, machine-readable error codes carried in every Problem Details response as the
 * {@code code} property (§33). Clients branch on these, not on HTTP status or message text:
 * VERSION_CONFLICT, for example, arrives as 412 or 409 depending on where it was detected.
 */
public enum ApiErrorCode {
    VALIDATION_FAILED,
    TASK_NOT_FOUND,
    INVALID_STATE_TRANSITION,
    VERSION_CONFLICT,
    REQUEST_NOT_SUPPORTED,
    INTERNAL_ERROR
}
