package com.taskpilot.scheduling;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import com.taskpilot.task.TaskService;
import com.taskpilot.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worker integration tests against PostgreSQL with a fixed Clock (§41, §42, §44): due-date and
 * downtime-recovery semantics, configured-zone dates, real per-task transactions, and isolation
 * of an optimistic-lock conflict. The scheduled trigger is disabled in tests
 * (app.activation.enabled=false), so the run is invoked directly.
 *
 * Not @Transactional: each activation must commit (or fail) in its own transaction.
 */
@SpringBootTest
@Testcontainers
class ScheduledTaskActivatorTest {

    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");
    // "Today" for the worker: noon Aug 25 in Jerusalem.
    private static final Clock AUG_25_NOON = Clock.fixed(Instant.parse("2026-08-25T09:00:00Z"), JERUSALEM);
    // Fixtures are created earlier so their scheduledFor was still in the future (-> SCHEDULED).
    private static final Clock AUG_10_NOON = Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), JERUSALEM);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        Clock fixedClock() {
            return AUG_25_NOON;
        }

        @Bean
        @Primary
        ConflictInjectingTaskService conflictInjectingTaskService(TaskRepository taskRepository, Clock clock,
                                                                  JdbcTemplate jdbcTemplate,
                                                                  PlatformTransactionManager transactionManager) {
            return new ConflictInjectingTaskService(taskRepository, clock, jdbcTemplate, transactionManager);
        }
    }

    /**
     * Simulates a concurrent API write deterministically: when armed, right after the worker has
     * reloaded and activated the first task -- inside that task's transaction, before its commit --
     * another transaction commits a version bump on the same row. The worker's commit is then a
     * stale write, exactly as in a real race, with the real per-task transaction boundary.
     */
    static class ConflictInjectingTaskService extends TaskService {

        private final JdbcTemplate jdbcTemplate;
        private final TransactionTemplate otherWriter;
        private volatile boolean armed;
        private final AtomicReference<UUID> conflicted = new AtomicReference<>();

        ConflictInjectingTaskService(TaskRepository taskRepository, Clock clock, JdbcTemplate jdbcTemplate,
                                     PlatformTransactionManager transactionManager) {
            super(taskRepository, clock);
            this.jdbcTemplate = jdbcTemplate;
            this.otherWriter = new TransactionTemplate(transactionManager);
            this.otherWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @Override
        @Transactional
        public boolean activateIfDue(UUID id) {
            boolean activated = super.activateIfDue(id);
            if (armed && conflicted.compareAndSet(null, id)) {
                otherWriter.executeWithoutResult(status ->
                        jdbcTemplate.update("update tasks set version = version + 1 where id = ?", id));
            }
            return activated;
        }

        void conflictOnFirstActivation() {
            armed = true;
        }

        UUID conflictedTaskId() {
            return conflicted.get();
        }

        void reset() {
            armed = false;
            conflicted.set(null);
        }
    }

    @Autowired
    private ScheduledTaskActivator activator;

    @Autowired
    private ConflictInjectingTaskService conflictInjectingTaskService;

    @Autowired
    private TaskRepository taskRepository;

    @AfterEach
    void cleanUp() {
        conflictInjectingTaskService.reset();
        taskRepository.deleteAll();
    }

    @Test
    void activatesTasksDueTodayOrEarlierAndLeavesFutureOnesScheduled() {
        Task a = saveScheduled("A: today 18:00", "2026-08-25T18:00:00+03:00");
        Task b = saveScheduled("B: tomorrow", "2026-08-26T09:00:00+03:00");
        Task c = saveScheduled("C: yesterday", "2026-08-24T09:00:00+03:00");
        Task missedRuns = saveScheduled("D: a week ago (downtime)", "2026-08-18T09:00:00+03:00");
        // Still Aug 25 in UTC (a UTC-date comparison would activate it), but Aug 26 01:00 in Jerusalem.
        Task utcTodayLocalTomorrow = saveScheduled("E: UTC today, local tomorrow", "2026-08-25T22:00:00Z");

        activator.activateDueTasks();

        for (Task due : List.of(a, c, missedRuns)) {
            Task after = reload(due);
            assertThat(after.getStatus()).as(due.getTitle()).isEqualTo(TaskStatus.ACTIVE);
            assertThat(after.getUpdatedAt()).isEqualTo(AUG_25_NOON.instant());
            assertThat(after.getScheduledFor().toInstant()).isEqualTo(due.getScheduledFor().toInstant());
            assertThat(after.getCompletedAt()).isNull();
            assertThat(after.getVersion()).isEqualTo(due.getVersion() + 1);
        }
        for (Task notDue : List.of(b, utcTodayLocalTomorrow)) {
            Task after = reload(notDue);
            assertThat(after.getStatus()).as(notDue.getTitle()).isEqualTo(TaskStatus.SCHEDULED);
            assertThat(after.getUpdatedAt()).isEqualTo(notDue.getUpdatedAt());
            assertThat(after.getVersion()).isEqualTo(notDue.getVersion());
        }
    }

    @Test
    void aConflictOnOneCandidateSkipsOnlyThatTaskAndTheNextRunActivatesIt() {
        List<Task> due = List.of(
                saveScheduled("due 1", "2026-08-24T09:00:00+03:00"),
                saveScheduled("due 2", "2026-08-23T09:00:00+03:00"),
                saveScheduled("due 3", "2026-08-22T09:00:00+03:00"));

        // Whichever candidate the worker processes first gets a concurrent write before its commit,
        // so the remaining candidates are the "later" ones that must still be processed.
        conflictInjectingTaskService.conflictOnFirstActivation();

        activator.activateDueTasks();

        UUID conflictedId = conflictInjectingTaskService.conflictedTaskId();
        assertThat(conflictedId).isNotNull();
        for (Task task : due) {
            TaskStatus expected = task.getId().equals(conflictedId) ? TaskStatus.SCHEDULED : TaskStatus.ACTIVE;
            assertThat(reload(task).getStatus()).as(task.getTitle()).isEqualTo(expected);
        }

        // No retry in the same run; the next run re-evaluates the skipped task from its current state.
        activator.activateDueTasks();

        assertThat(due).allSatisfy(task -> assertThat(reload(task).getStatus()).isEqualTo(TaskStatus.ACTIVE));
    }

    @Test
    void aRunWithNoDueTasksChangesNothing() {
        Task future = saveScheduled("next week", "2026-09-01T09:00:00+03:00");

        activator.activateDueTasks();

        assertThat(reload(future).getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(reload(future).getVersion()).isEqualTo(future.getVersion());
    }

    private Task saveScheduled(String title, String scheduledFor) {
        Task task = Task.create(title, null, null, null, null, OffsetDateTime.parse(scheduledFor), AUG_10_NOON);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        return taskRepository.save(task);
    }

    private Task reload(Task task) {
        return taskRepository.findById(task.getId()).orElseThrow();
    }
}
