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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for
 * POST /tasks/{id}/reopen (§27, §43). The transition rules live in {@link TaskTest};
 * these prove the wiring and the 404/409 mappings.
 *
 * Intentionally not @Transactional, for the same reason as {@link TaskCompletionApiTest}:
 * the service's transaction must really commit for the re-read to prove persistence.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TaskReopenApiTest {

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
    void reopeningACompletedTaskReturns200AndPersistsActiveStateWithSchedulingContextPreserved() throws Exception {
        OffsetDateTime pastScheduledFor = inDaysAt(-2, 9);
        LocalDate deadline = LocalDate.now(clock).plusDays(3);
        Task task = Task.create("Follow up", null, null, null, deadline, pastScheduledFor, clock);
        task.complete(clock);
        task = taskRepository.save(task);

        mockMvc.perform(post("/tasks/{id}/reopen", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt", nullValue()))
                .andExpect(jsonPath("$.scheduledFor", notNullValue()))
                .andExpect(jsonPath("$.deadline").value(deadline.toString()));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(persisted.getCompletedAt()).isNull();
        assertThat(persisted.getUpdatedAt()).isAfterOrEqualTo(task.getUpdatedAt());
        assertThat(persisted.getScheduledFor().toInstant()).isEqualTo(pastScheduledFor.toInstant());
        assertThat(persisted.getDeadline()).isEqualTo(deadline);
        assertThat(persisted.getVersion()).isEqualTo(task.getVersion() + 1);
    }

    @Test
    void reopeningAnActiveTaskReturns409() throws Exception {
        Task task = taskRepository.save(Task.create("Active task", null, null, null, null, null, clock));

        mockMvc.perform(post("/tasks/{id}/reopen", task.getId()))
                .andExpect(status().isConflict());

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(persisted.getVersion()).isEqualTo(task.getVersion());
    }

    @Test
    void reopeningAScheduledTaskReturns409() throws Exception {
        Task task = taskRepository.save(Task.create("Scheduled task", null, null, null, null, inDaysAt(3, 9), clock));

        mockMvc.perform(post("/tasks/{id}/reopen", task.getId()))
                .andExpect(status().isConflict());

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(persisted.getVersion()).isEqualTo(task.getVersion());
    }

    @Test
    void reopeningAMissingTaskReturns404() throws Exception {
        mockMvc.perform(post("/tasks/{id}/reopen", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private OffsetDateTime inDaysAt(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
