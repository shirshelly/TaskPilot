package com.taskpilot.agenda;

import com.taskpilot.FixedClockTestConfiguration;
import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import com.taskpilot.task.TaskStatus;
import com.taskpilot.task.dto.TaskResponse;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> AgendaController -> AgendaService -> TaskRepository -> PostgreSQL test
 * for GET /agenda/today (§30). The membership/timezone matrix lives in TaskRepositoryTest;
 * this proves the wiring with the real configured Clock/zone, on one mixed dataset showing
 * Agenda is not "all ACTIVE tasks". The ordering rules are covered by AgendaOrderingTest; one
 * test here proves the endpoint returns that order.
 *
 * Not @Transactional, like the other API tests, so the re-read sees committed state.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AgendaApiTest {

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

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
    }

    @Test
    void returnsOnlyTasksAssignedToOrDueByTodayWithoutMutatingThem() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Task dueToday = save(Task.create("Due today", null, null, null, today, null, clock));
        Task rolledOver = save(Task.create("Scheduled yesterday", null, null, null, null, inDaysAt(-1, 9), clock));
        Task scheduledButOverdue = save(Task.create("Scheduled next week, deadline passed", null, null, null,
                today.minusDays(1), inDaysAt(7, 9), clock));
        save(Task.create("Plain active", null, null, null, null, null, clock));
        save(Task.create("Scheduled tomorrow", null, null, null, null, inDaysAt(1, 9), clock));
        Task completed = Task.create("Completed, due today", null, null, null, today, null, clock);
        completed.complete(clock);
        save(completed);

        String body = mockMvc.perform(get("/agenda/today"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<TaskResponse> agenda = objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TaskResponse.class));

        Map<String, TaskResponse> byTitle = agenda.stream()
                .collect(Collectors.toMap(TaskResponse::title, Function.identity()));
        assertThat(byTitle.keySet()).containsExactlyInAnyOrder(
                "Due today", "Scheduled yesterday", "Scheduled next week, deadline passed");

        // Agenda membership uses deadline <= today; isOverdue keeps its own deadline < today rule.
        assertThat(byTitle.get("Due today").isOverdue()).isFalse();
        // Observed as-is: Agenda doesn't activate or otherwise repair lifecycle state.
        assertThat(byTitle.get("Scheduled next week, deadline passed").status()).isEqualTo(TaskStatus.SCHEDULED);

        for (Task before : List.of(dueToday, rolledOver, scheduledButOverdue)) {
            Task after = taskRepository.findById(before.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(before.getStatus());
            assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
            assertThat(after.getVersion()).isEqualTo(before.getVersion());
        }
    }

    @Test
    void returnsTheAgendaMostTimeCriticalFirst() throws Exception {
        LocalDate today = LocalDate.now(clock);
        save(Task.create("Scheduled today, due in a month", null, null, null, today.plusDays(30), inDaysAt(0, 16), clock));
        save(Task.create("Scheduled today, no deadline", null, null, null, null, inDaysAt(0, 9), clock));
        save(Task.create("Due today", null, null, null, today, null, clock));
        save(Task.create("Overdue schedule", null, null, null, null, inDaysAt(-3, 9), clock));
        save(Task.create("Overdue deadline", null, null, null, today.minusDays(1), null, clock));

        String body = mockMvc.perform(get("/agenda/today"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<TaskResponse> agenda = objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TaskResponse.class));

        assertThat(agenda).extracting(TaskResponse::title).containsExactly(
                "Overdue deadline", "Overdue schedule", "Due today",
                "Scheduled today, no deadline", "Scheduled today, due in a month");
    }

    @Test
    void emptyAgendaReturnsAnEmptyArray() throws Exception {
        save(Task.create("Plain active", null, null, null, null, null, clock));

        String body = mockMvc.perform(get("/agenda/today"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("[]");
    }

    private Task save(Task task) {
        return taskRepository.save(task);
    }

    private OffsetDateTime inDaysAt(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
