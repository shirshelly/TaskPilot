package com.taskpilot.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Shared configured-zone day boundary for queries over scheduled_for (TIMESTAMPTZ).
 *
 * "scheduledFor local date <= today" in the Clock's zone is exactly "scheduledFor is before local
 * midnight tomorrow". Comparing against that absolute instant keeps queries independent of the
 * database session timezone and the JVM default timezone. atStartOfDay(zone) also handles DST.
 */
public final class DayBoundaries {

    private DayBoundaries() {
    }

    public static OffsetDateTime startOfTomorrow(Clock clock) {
        return LocalDate.now(clock).plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
    }
}
