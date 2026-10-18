package com.taskpilot.scheduling;

import com.taskpilot.common.DayBoundaries;
import com.taskpilot.task.TaskRepository;
import com.taskpilot.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Background SCHEDULED -> ACTIVE activation (§41). Deliberately not @Transactional: each
 * candidate goes through TaskService.activateIfDue (a separate bean, so its transactional proxy
 * applies) in its own transaction, so one task's conflict rolls back only that task.
 */
@Component
public class ScheduledTaskActivator {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskActivator.class);

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final Clock clock;

    public ScheduledTaskActivator(TaskRepository taskRepository, TaskService taskService, Clock clock) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.clock = clock;
    }

    // Fixed delay (not rate): runs never overlap. Correctness doesn't depend on timing because
    // every run re-evaluates "local date <= today", which also recovers after downtime.
    @Scheduled(fixedDelayString = "${app.activation.fixed-delay:PT5M}")
    public void activateDueTasks() {
        List<UUID> candidateIds = taskRepository.findDueScheduledTaskIds(DayBoundaries.startOfTomorrow(clock));
        int activated = 0;
        int noLongerDue = 0;
        int conflicts = 0;

        for (UUID id : candidateIds) {
            try {
                if (taskService.activateIfDue(id)) {
                    activated++;
                    log.debug("Task {} activated", id);
                } else {
                    noLongerDue++;
                    log.debug("Task {} skipped: deleted or no longer due", id);
                }
            } catch (OptimisticLockingFailureException e) {
                // Concurrently modified: skip for this run, no retry; the next run re-evaluates it (§42).
                conflicts++;
                log.debug("Task {} skipped: concurrent modification", id);
            }
        }

        log.info("Scheduled activation run: {} candidate(s), {} activated, {} no longer due, {} skipped on conflict",
                candidateIds.size(), activated, noLongerDue, conflicts);
    }
}
