package com.taskpilot.task;

import com.taskpilot.FixedClockTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for
 * POST /tasks/{id}/schedule (§25, §43). The transition/date matrix lives in {@link TaskTest};
 * these prove the wiring, the configured-zone date handling, and the 400/404/409 mappings.
 *
 * Intentionally not @Transactional, for the same reason as {@link TaskCompletionApiTest}:
 * the service's transaction must really commit for the re-read to prove persistence.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TaskScheduleApiTest {

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
    void schedulingAnActiveTaskForAFutureDateReturns200AndPersistsScheduledState() throws Exception {
        LocalDate deadline = LocalDate.now(clock).plusDays(5);
        Task task = taskRepository.save(Task.create("Dentist", null, null, null, deadline, null, clock));
        OffsetDateTime future = inDaysAt(2, 16);

        mockMvc.perform(schedule(task.getId(), future))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.completedAt", nullValue()));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(persisted.getScheduledFor().toInstant()).isEqualTo(future.toInstant());
        assertThat(persisted.getDeadline()).isEqualTo(deadline);
        assertThat(persisted.getVersion()).isEqualTo(task.getVersion() + 1);
    }

    @Test
    void reschedulingAScheduledTaskToAnotherFutureDatePersistsTheNewTimestamp() throws Exception {
        Task task = taskRepository.save(Task.create("Dentist", null, null, null, null, inDaysAt(2, 16), clock));
        OffsetDateTime later = inDaysAt(7, 10);

        mockMvc.perform(schedule(task.getId(), later))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(persisted.getScheduledFor().toInstant()).isEqualTo(later.toInstant());
    }

    @Test
    void reschedulingAScheduledTaskToTodayActivatesItAndKeepsTheSuppliedTimestamp() throws Exception {
        Task task = taskRepository.save(Task.create("Dentist", null, null, null, null, inDaysAt(2, 16), clock));
        // 01:00 today in the configured zone, sent in UTC: its UTC date is yesterday, so this
        // only succeeds as ACTIVE if the configured zone, not UTC, decides the local date.
        OffsetDateTime todayEarlyAsUtc = inDaysAt(0, 1).withOffsetSameInstant(ZoneOffset.UTC);

        mockMvc.perform(schedule(task.getId(), todayEarlyAsUtc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(persisted.getScheduledFor().toInstant()).isEqualTo(todayEarlyAsUtc.toInstant());
    }

    @Test
    void schedulingACompletedTaskReturns409AndLeavesItUnchanged() throws Exception {
        Task task = Task.create("Done", null, null, null, null, null, clock);
        task.complete(clock);
        task = taskRepository.save(task);

        mockMvc.perform(schedule(task.getId(), inDaysAt(2, 9)))
                .andExpect(status().isConflict());

        assertUnchanged(task);
    }

    @Test
    void schedulingIntoThePastReturns400AndLeavesTaskUnchanged() throws Exception {
        Task task = taskRepository.save(Task.create("Dentist", null, null, null, null, inDaysAt(2, 16), clock));

        // Domain validation (Task.schedule), not bean validation: our own message is the detail.
        mockMvc.perform(schedule(task.getId(), inDaysAt(-1, 9)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("scheduledFor must not be in the past"));

        assertUnchanged(task);
    }

    @Test
    void missingScheduledForReturns400() throws Exception {
        Task task = taskRepository.save(Task.create("Task", null, null, null, null, null, clock));

        mockMvc.perform(scheduleWithBody(task.getId(), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedScheduledForReturns400() throws Exception {
        Task task = taskRepository.save(Task.create("Task", null, null, null, null, null, clock));

        mockMvc.perform(scheduleWithBody(task.getId(), """
                        {"scheduledFor": "next tuesday"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void schedulingAMissingTaskReturns404() throws Exception {
        mockMvc.perform(schedule(UUID.randomUUID(), inDaysAt(2, 9)))
                .andExpect(status().isNotFound());
    }

    private void assertUnchanged(Task before) {
        Task persisted = taskRepository.findById(before.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(before.getStatus());
        assertThat(persisted.getVersion()).isEqualTo(before.getVersion());
        assertThat(persisted.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    private MockHttpServletRequestBuilder schedule(UUID id, OffsetDateTime scheduledFor) {
        return scheduleWithBody(id, """
                {"scheduledFor": "%s"}
                """.formatted(scheduledFor));
    }

    private MockHttpServletRequestBuilder scheduleWithBody(UUID id, String body) {
        return post("/tasks/{id}/schedule", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private OffsetDateTime inDaysAt(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
