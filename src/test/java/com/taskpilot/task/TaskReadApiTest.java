package com.taskpilot.task;

import com.taskpilot.FixedClockTestConfiguration;
import com.taskpilot.task.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for the
 * read endpoints (§43). Fixtures are seeded directly via TaskRepository rather than
 * through POST /tasks, since creation behavior is already covered by TaskCreationApiTest.
 * No test asserts on list ordering: the design doesn't define one (see TaskController).
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class TaskReadApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getExistingTaskReturns200WithPersistedData() throws Exception {
        Task task = taskRepository.save(Task.create("Buy plant", "For the desk", 4, 20, null, null, clock));

        mockMvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.title").value("Buy plant"))
                .andExpect(jsonPath("$.description").value("For the desk"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.importance").value(4));
    }

    @Test
    void getMissingTaskReturns404() throws Exception {
        mockMvc.perform(get("/tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsAllPersistedTasks() throws Exception {
        Task active = taskRepository.save(Task.create("Active task", null, null, null, null, null, clock));
        Task scheduled = taskRepository.save(
                Task.create("Scheduled task", null, null, null, null, inDaysAt(3, 9), clock));

        List<TaskResponse> tasks = performListAndParse(get("/tasks"));

        assertThat(tasks).extracting(TaskResponse::id)
                .containsExactlyInAnyOrder(active.getId(), scheduled.getId());
    }

    @Test
    void listFilteredByActiveStatusExcludesOtherStatuses() throws Exception {
        Task active = taskRepository.save(Task.create("Active task", null, null, null, null, null, clock));
        taskRepository.save(Task.create("Scheduled task", null, null, null, null, inDaysAt(3, 9), clock));

        List<TaskResponse> tasks = performListAndParse(get("/tasks").param("status", "ACTIVE"));

        assertThat(tasks).extracting(TaskResponse::id).containsExactly(active.getId());
    }

    @Test
    void listFilteredByScheduledStatusReturnsOnlyScheduledTasks() throws Exception {
        taskRepository.save(Task.create("Active task", null, null, null, null, null, clock));
        Task scheduled = taskRepository.save(
                Task.create("Scheduled task", null, null, null, null, inDaysAt(3, 9), clock));

        List<TaskResponse> tasks = performListAndParse(get("/tasks").param("status", "SCHEDULED"));

        assertThat(tasks).extracting(TaskResponse::id).containsExactly(scheduled.getId());
    }

    @Test
    void listFilteredByCompletedStatusReturnsOnlyCompletedTasks() throws Exception {
        taskRepository.save(Task.create("Active task", null, null, null, null, null, clock));
        Task completed = Task.create("Completed task", null, null, null, null, null, clock);
        completed.complete(clock);
        taskRepository.save(completed);

        List<TaskResponse> tasks = performListAndParse(get("/tasks").param("status", "COMPLETED"));

        assertThat(tasks).extracting(TaskResponse::id).containsExactly(completed.getId());
    }

    @Test
    void listWithInvalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/tasks").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isOverdueIsComputedUsingTheInjectedClockOnRead() throws Exception {
        LocalDate pastDeadline = LocalDate.now(clock).minusDays(3);
        Task task = taskRepository.save(Task.create("Overdue task", null, null, null, pastDeadline, null, clock));

        mockMvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOverdue").value(true));
    }

    private List<TaskResponse> performListAndParse(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TaskResponse.class));
    }

    private OffsetDateTime inDaysAt(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
