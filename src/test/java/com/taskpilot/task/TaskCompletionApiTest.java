package com.taskpilot.task;

import com.taskpilot.FixedClockTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for
 * POST /tasks/{id}/complete (§26, §43). The full transition matrix lives in {@link TaskTest};
 * these prove the wiring and the 404/409 mappings.
 *
 * Intentionally not @Transactional: the service's own transaction must really commit so the
 * re-read proves the dirty-checked change reached PostgreSQL, rather than a rolled-back test
 * transaction hiding a missing flush.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TaskCompletionApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private Clock clock;

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
    }

    @Test
    void completingAnActiveTaskReturns200AndPersistsCompletedState() throws Exception {
        LocalDate deadline = LocalDate.now(clock).plusDays(2);
        Task task = taskRepository.save(Task.create("Homework", "Chapter 3", 4, 90, deadline, null, clock));

        mockMvc.perform(post("/tasks/{id}/complete", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.title").value("Homework"))
                .andExpect(jsonPath("$.deadline").value(deadline.toString()));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(persisted.getCompletedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCompletedAt());
        assertThat(persisted.getCreatedAt()).isEqualTo(task.getCreatedAt());
        assertThat(persisted.getDeadline()).isEqualTo(deadline);
        assertThat(persisted.getDescription()).isEqualTo("Chapter 3");
        assertThat(persisted.getImportance()).isEqualTo(4);
        assertThat(persisted.getEstimatedDurationMinutes()).isEqualTo(90);
        assertThat(persisted.getVersion()).isEqualTo(task.getVersion() + 1);
    }

    @Test
    void completingAScheduledTaskReturns200AndPreservesScheduledFor() throws Exception {
        OffsetDateTime scheduledFor = inDaysAt(3, 16);
        Task task = taskRepository.save(Task.create("Dentist", null, null, null, null, scheduledFor, clock));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);

        mockMvc.perform(post("/tasks/{id}/complete", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.scheduledFor", notNullValue()));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(persisted.getCompletedAt()).isNotNull();
        assertThat(persisted.getScheduledFor().toInstant()).isEqualTo(scheduledFor.toInstant());
    }

    @Test
    void completingAnAlreadyCompletedTaskReturns409AndLeavesItUnchanged() throws Exception {
        Task task = Task.create("Done already", null, null, null, null, null, clock);
        task.complete(clock);
        task = taskRepository.save(task);

        mockMvc.perform(post("/tasks/{id}/complete", task.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getCompletedAt()).isEqualTo(task.getCompletedAt());
        assertThat(persisted.getVersion()).isEqualTo(task.getVersion());
    }

    @Test
    void completingAMissingTaskReturns404() throws Exception {
        mockMvc.perform(post("/tasks/{id}/complete", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private OffsetDateTime inDaysAt(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
