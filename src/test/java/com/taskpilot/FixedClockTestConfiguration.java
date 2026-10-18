package com.taskpilot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Fixed application Clock for API tests that derive dates from "today". With the real system
 * clock, a test's setup and the server could see different dates if the run crossed local
 * midnight. Uses the configured app.zone-id, so tests make no timezone assumption of their own;
 * @Primary takes precedence over ClockConfiguration's bean wherever a Clock is injected.
 */
@TestConfiguration
public class FixedClockTestConfiguration {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-08-25T09:00:00Z");

    @Bean
    @Primary
    Clock fixedClock(@Value("${app.zone-id}") String zoneId) {
        return Clock.fixed(FIXED_INSTANT, ZoneId.of(zoneId));
    }
}
