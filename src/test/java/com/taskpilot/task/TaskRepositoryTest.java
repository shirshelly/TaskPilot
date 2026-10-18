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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    void flywayHasAppliedV1MigrationToTheTestContainer() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true",
                Long.class);

        assertThat(count).isEqualTo(1L);
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
