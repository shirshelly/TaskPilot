package com.taskpilot.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * Exposes the application timezone so clients build and display scheduledFor values in the same
 * zone the backend uses for "today" (§19). Read from the Clock bean rather than the property so
 * it always reflects the zone actually in effect.
 */
@RestController
public class AppInfoController {

    private final Clock clock;

    public AppInfoController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/app-info")
    public AppInfoResponse appInfo() {
        return new AppInfoResponse(clock.getZone().getId());
    }

    public record AppInfoResponse(String zoneId) {
    }
}
