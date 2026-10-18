package com.taskpilot.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's built-in scheduling for the activation worker (§41). The activation run is the
 * only scheduled job in V1, so app.activation.enabled=false turns the trigger off (tests do this);
 * the ScheduledTaskActivator bean still exists and can be invoked directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.activation.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
