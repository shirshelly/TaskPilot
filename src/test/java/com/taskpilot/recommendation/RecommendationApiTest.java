package com.taskpilot.recommendation;

import com.taskpilot.FixedClockTestConfiguration;
import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import com.taskpilot.task.dto.TaskResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> RecommendationController -> RecommendationService -> TaskRepository ->
 * PostgreSQL tests for GET /recommendations (§31). Scoring details live in
 * RecommendationRankerTest; these prove wiring, eligibility, ordering and validation.
 *
 * Not @Transactional, like the other API tests, so the re-read sees committed state.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class RecommendationApiTest {

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
    void ranksActiveTasksByImportanceUrgencyAndAgeAndLeavesThemUnchanged() throws Exception {
        LocalDate today = LocalDate.now(clock);
        save(activeTask("T1", 5, null, null, 0));                    // 50
        save(activeTask("T2", 3, today.plusDays(1), null, 2));       // 47
        Task t3 = save(activeTask("T3", 2, today.minusDays(1), null, 8)); // 55
        save(activeTask("T4", 3, null, null, 30));                   // 35
        save(activeTask("T5", 1, today.minusDays(9), null, 60));     // 45
        // Not actionable: must never be ranked, however important or urgent.
        save(Task.create("Scheduled, importance 5", null, 5, null, today.minusDays(1), inDaysAt(3), clock));

        List<TaskResponse> result = recommendations(get("/recommendations"));

        assertThat(result).extracting(TaskResponse::title).containsExactly("T3", "T1", "T2", "T5", "T4");

        Task t3After = taskRepository.findById(t3.getId()).orElseThrow();
        assertThat(t3After.getUpdatedAt()).isEqualTo(t3.getUpdatedAt());
        assertThat(t3After.getVersion()).isEqualTo(t3.getVersion());
    }

    @Test
    void scheduledHighImportanceTaskIsAbsentWhileActiveLowImportanceTaskIsPresent() throws Exception {
        save(Task.create("Scheduled, importance 5", null, 5, null, null, inDaysAt(2), clock));
        save(activeTask("Active, importance 2", 2, null, null, 0));

        assertThat(recommendations(get("/recommendations")))
                .extracting(TaskResponse::title)
                .containsExactly("Active, importance 2");
    }

    @Test
    void availableMinutesExcludesLongerAndUnknownDurations() throws Exception {
        save(activeTask("A: 10 min", 3, null, 10, 0));
        save(activeTask("B: 30 min", 3, null, 30, 0));
        save(activeTask("C: unknown", 3, null, null, 0));
        save(Task.create("D: scheduled, 10 min", null, 3, 10, null, inDaysAt(2), clock));

        assertThat(recommendations(get("/recommendations").param("availableMinutes", "20")))
                .extracting(TaskResponse::title)
                .containsExactly("A: 10 min");
    }

    @Test
    void nonPositiveOrNonNumericAvailableMinutesReturns400() throws Exception {
        for (String invalid : List.of("0", "-5", "abc")) {
            mockMvc.perform(get("/recommendations").param("availableMinutes", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void noEligibleTasksReturnsAnEmptyArray() throws Exception {
        save(activeTask("Too long", 3, null, 90, 0));

        String body = mockMvc.perform(get("/recommendations").param("availableMinutes", "20"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("[]");
    }

    private List<TaskResponse> recommendations(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TaskResponse.class));
    }

    private Task save(Task task) {
        return taskRepository.save(task);
    }

    /** ACTIVE task created at local noon ageDays ago, so its local-date age is exact. */
    private Task activeTask(String title, int importance, LocalDate deadline, Integer duration, int ageDays) {
        LocalDate createdOn = LocalDate.now(clock).minusDays(ageDays);
        Clock createdAt = Clock.fixed(createdOn.atTime(12, 0).atZone(clock.getZone()).toInstant(), clock.getZone());
        return Task.create(title, null, importance, duration, deadline, null, createdAt);
    }

    private OffsetDateTime inDaysAt(int days) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(9, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
