package com.taskpilot.task;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Real-PostgreSQL persistence tests (Testcontainers), not H2. Flyway runs for
 * real against the container, and Hibernate validates for real, so these
 * verify persistence/mapping/constraint behavior that {@link TaskTest}
 * (pure domain, no DB) structurally cannot.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TaskRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywayHasAppliedAllMigrationsToTheTestContainer() {
        List<String> versions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void minimumSessionRoundTrips() {
        Task withSession = Task.create("Read a book", null, null, 600, 10, null, null, CLOCK);
        Task without = Task.create("Clean the oven", null, null, 45, null, null, CLOCK);
        taskRepository.saveAndFlush(withSession);
        taskRepository.saveAndFlush(without);
        entityManager.clear();

        assertThat(taskRepository.findById(withSession.getId()).orElseThrow().getMinSessionMinutes()).isEqualTo(10);
        assertThat(taskRepository.findById(without.getId()).orElseThrow().getMinSessionMinutes()).isNull();
    }

    @Test
    void savesAndReloadsATaskWithAllFieldsMappedCorrectly() {
        OffsetDateTime scheduledFor = OffsetDateTime.of(TODAY.plusDays(3), LocalTime.of(9, 0), ZoneOffset.UTC);
        Task task = Task.create("Dentist", "Yearly checkup", 4, 45, TODAY.plusDays(10), scheduledFor, CLOCK);

        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task loaded = taskRepository.findById(task.getId()).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(task.getId());
        assertThat(loaded.getTitle()).isEqualTo("Dentist");
        assertThat(loaded.getDescription()).isEqualTo("Yearly checkup");
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(loaded.getImportance()).isEqualTo(4);
        assertThat(loaded.getEstimatedDurationMinutes()).isEqualTo(45);
        assertThat(loaded.getDeadline()).isEqualTo(TODAY.plusDays(10));
        assertThat(loaded.getScheduledFor().toInstant()).isEqualTo(scheduledFor.toInstant());
        assertThat(loaded.getCreatedAt()).isEqualTo(task.getCreatedAt());
        assertThat(loaded.getUpdatedAt()).isEqualTo(task.getUpdatedAt());
        assertThat(loaded.getCompletedAt()).isNull();
        assertThat(loaded.getVersion()).isEqualTo(0L);
    }

    @Test
    void nullableFieldsRoundTripAsNull() {
        Task task = Task.create("Buy plant", null, null, null, null, null, CLOCK);

        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task loaded = taskRepository.findById(task.getId()).orElseThrow();

        assertThat(loaded.getDescription()).isNull();
        assertThat(loaded.getEstimatedDurationMinutes()).isNull();
        assertThat(loaded.getDeadline()).isNull();
        assertThat(loaded.getScheduledFor()).isNull();
        assertThat(loaded.getCompletedAt()).isNull();
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.ACTIVE);
    }

    @Test
    void completedTaskPersistsCompletedAt() {
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        task.complete(CLOCK);

        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task loaded = taskRepository.findById(task.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(loaded.getCompletedAt()).isEqualTo(task.getCompletedAt());
    }

    @Test
    void versionIncrementsOnUpdateAndDetectsStaleWrites() {
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        // Two independently-detached readers, simulating the worker/API race described in §42.
        Task copy1 = taskRepository.findById(task.getId()).orElseThrow();
        entityManager.clear();
        Task copy2 = taskRepository.findById(task.getId()).orElseThrow();
        entityManager.clear();

        copy1.rename("Updated by session A", CLOCK);
        Task saved1 = taskRepository.saveAndFlush(copy1);
        assertThat(saved1.getVersion()).isEqualTo(1L);

        copy2.rename("Updated by session B", CLOCK);
        assertThatThrownBy(() -> taskRepository.saveAndFlush(copy2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void deletingAStaleVersionedEntityIsDetectedAsAnOptimisticLockConflict() {
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        // Mirrors TaskService.delete: entity loaded in this transaction, then another writer
        // commits a change (bumping version) before our DELETE ... WHERE version = ? runs.
        Task loaded = taskRepository.findById(task.getId()).orElseThrow();
        jdbcTemplate.update("update tasks set version = version + 1 where id = ?", task.getId());

        taskRepository.delete(loaded);
        assertThatThrownBy(() -> taskRepository.flush())
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- Agenda query (§30). Bounds are what AgendaService derives for noon Aug 25 in Jerusalem (UTC+3).

    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");
    private static final Clock JERUSALEM_NOON = Clock.fixed(Instant.parse("2026-08-25T09:00:00Z"), JERUSALEM);
    private static final OffsetDateTime START_OF_TOMORROW_IN_JERUSALEM = OffsetDateTime.parse("2026-08-26T00:00:00+03:00");

    @Test
    void agendaIncludesUnfinishedTasksScheduledOrDueByTodayAndNothingElse() {
        OffsetDateTime yesterdayMorning = OffsetDateTime.parse("2026-08-24T10:00:00+03:00");
        OffsetDateTime todayEvening = OffsetDateTime.parse("2026-08-25T18:00:00+03:00");
        OffsetDateTime tomorrowMorning = OffsetDateTime.parse("2026-08-26T09:00:00+03:00");
        OffsetDateTime nextWeek = OffsetDateTime.parse("2026-09-01T09:00:00+03:00");

        // included
        saveTask("deadline today", TODAY, null);
        saveTask("deadline passed", TODAY.minusDays(3), null);
        saveTask("scheduled yesterday (rollover)", null, yesterdayMorning);
        saveTask("scheduled later today", null, todayEvening);
        saveTask("still SCHEDULED, deadline passed", TODAY.minusDays(1), nextWeek);
        // excluded
        saveTask("plain active", null, null);
        saveTask("scheduled tomorrow, deadline later", TODAY.plusDays(5), tomorrowMorning);
        saveCompletedTask("completed, scheduled today", null, todayEvening);
        saveCompletedTask("completed, deadline passed", TODAY.minusDays(3), null);
        entityManager.clear();

        assertThat(taskRepository.findAgendaTasks(TODAY, START_OF_TOMORROW_IN_JERUSALEM))
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder(
                        "deadline today",
                        "deadline passed",
                        "scheduled yesterday (rollover)",
                        "scheduled later today",
                        "still SCHEDULED, deadline passed");
    }

    @Test
    void agendaUsesTheConfiguredZoneLocalDateNotTheUtcDate() {
        // 20:59Z = 23:59 Aug 25 in Jerusalem -> today -> included.
        saveTask("last minute of today", null, OffsetDateTime.parse("2026-08-25T20:59:00Z"));
        // 21:00Z = local midnight Aug 26 -> tomorrow -> excluded (exclusive bound).
        saveTask("local midnight", null, OffsetDateTime.parse("2026-08-25T21:00:00Z"));
        // 22:00Z is still Aug 25 in UTC (UTC-date comparison would include it), but it is
        // already 01:00 Aug 26 in Jerusalem -> tomorrow -> excluded.
        saveTask("UTC today, Jerusalem tomorrow", null, OffsetDateTime.parse("2026-08-25T22:00:00Z"));
        entityManager.clear();

        assertThat(taskRepository.findAgendaTasks(TODAY, START_OF_TOMORROW_IN_JERUSALEM))
                .extracting(Task::getTitle)
                .containsExactly("last minute of today");
    }

    // --- Activation candidates (§41), same Jerusalem bounds as above. ---

    private static final Clock JERUSALEM_FIVE_DAYS_EARLIER = Clock.offset(JERUSALEM_NOON, Duration.ofDays(-5));

    @Test
    void dueScheduledTaskIdsAreScheduledTasksWhoseLocalDateIsTodayOrEarlier() {
        // Created on Aug 20, so all of these are SCHEDULED at creation.
        Task dueLaterToday = saveScheduledOnAug20(OffsetDateTime.parse("2026-08-25T18:00:00+03:00"));
        Task overdue = saveScheduledOnAug20(OffsetDateTime.parse("2026-08-22T09:00:00+03:00"));
        saveScheduledOnAug20(OffsetDateTime.parse("2026-08-26T09:00:00+03:00")); // future
        // Old scheduledFor but no longer SCHEDULED: never a candidate.
        Task active = Task.create("active", null, null, null, null, OffsetDateTime.parse("2026-08-22T09:00:00+03:00"), JERUSALEM_NOON);
        taskRepository.saveAndFlush(active);
        Task completed = Task.create("completed", null, null, null, null, OffsetDateTime.parse("2026-08-22T09:00:00+03:00"), JERUSALEM_NOON);
        completed.complete(JERUSALEM_NOON);
        taskRepository.saveAndFlush(completed);
        entityManager.clear();

        assertThat(taskRepository.findDueScheduledTaskIds(START_OF_TOMORROW_IN_JERUSALEM))
                .containsExactlyInAnyOrder(dueLaterToday.getId(), overdue.getId());
    }

    @Test
    void dueScheduledTaskIdsUseTheConfiguredZoneLocalDateNotTheUtcDate() {
        Task lastMinuteOfToday = saveScheduledOnAug20(OffsetDateTime.parse("2026-08-25T20:59:00Z")); // 23:59 local
        saveScheduledOnAug20(OffsetDateTime.parse("2026-08-25T21:00:00Z")); // local midnight Aug 26
        saveScheduledOnAug20(OffsetDateTime.parse("2026-08-25T22:00:00Z")); // UTC Aug 25, local Aug 26
        entityManager.clear();

        assertThat(taskRepository.findDueScheduledTaskIds(START_OF_TOMORROW_IN_JERUSALEM))
                .containsExactly(lastMinuteOfToday.getId());
    }

    private Task saveScheduledOnAug20(OffsetDateTime scheduledFor) {
        Task task = Task.create("scheduled", null, null, null, null, scheduledFor, JERUSALEM_FIVE_DAYS_EARLIER);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        return taskRepository.saveAndFlush(task);
    }

    private void saveTask(String title, LocalDate deadline, OffsetDateTime scheduledFor) {
        taskRepository.saveAndFlush(Task.create(title, null, null, null, deadline, scheduledFor, JERUSALEM_NOON));
    }

    private void saveCompletedTask(String title, LocalDate deadline, OffsetDateTime scheduledFor) {
        Task task = Task.create(title, null, null, null, deadline, scheduledFor, JERUSALEM_NOON);
        task.complete(JERUSALEM_NOON);
        taskRepository.saveAndFlush(task);
    }

    @Test
    void databaseRejectsImportanceOutsideValidRange() {
        assertThatThrownBy(() -> insertRawTask("ACTIVE", 6, null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsNonPositiveEstimatedDuration() {
        assertThatThrownBy(() -> insertRawTask("ACTIVE", 3, 0, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsNonPositiveMinimumSession() {
        Task task = taskRepository.saveAndFlush(Task.create("Task", null, null, null, null, null, CLOCK));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update tasks set min_session_minutes = 0 where id = ?", task.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsUnknownStatusValue() {
        assertThatThrownBy(() -> insertRawTask("BOGUS", 3, null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsScheduledStatusWithoutScheduledFor() {
        assertThatThrownBy(() -> insertRawTask("SCHEDULED", 3, null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsCompletedStatusWithoutCompletedAt() {
        assertThatThrownBy(() -> insertRawTask("COMPLETED", 3, null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    /**
     * Inserts directly via JDBC, bypassing {@link Task} entirely, so these tests prove the
     * database's own CHECK constraints -- not the domain object's validation (already covered
     * by {@link TaskTest}).
     */
    private void insertRawTask(String status, int importance, Integer durationMinutes,
                                OffsetDateTime scheduledFor, Instant completedAt) {
        jdbcTemplate.update(
                "insert into tasks (id, title, status, importance, estimated_duration_minutes, "
                        + "scheduled_for, created_at, updated_at, completed_at, version) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                "Raw task",
                status,
                importance,
                durationMinutes,
                scheduledFor == null ? null : Timestamp.from(scheduledFor.toInstant()),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                completedAt == null ? null : Timestamp.from(completedAt),
                0L);
    }
}
