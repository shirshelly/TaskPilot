package com.taskpilot.task;

import com.taskpilot.FixedClockTestConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for
 * POST /tasks (§43). Status-derivation edge cases at exact day boundaries are already
 * covered by {@link TaskTest}; these prove the real wiring (validation, Clock/ZoneId,
 * persistence, response shape) produces the right outcome end to end.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class TaskCreationApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Test
    void unscheduledTaskIsCreatedActiveAndPersistedRowMatchesResponse() throws Exception {
        String requestBody = """
                {"title": "Buy plant"}
                """;

        String responseBody = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("Buy plant"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.importance").value(3))
                .andExpect(jsonPath("$.isOverdue").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        UUID id = UUID.fromString(json.get("id").asText());
        Task persisted = taskRepository.findById(id).orElseThrow();

        assertThat(persisted.getTitle()).isEqualTo(json.get("title").asText());
        assertThat(persisted.getStatus().name()).isEqualTo(json.get("status").asText());
        assertThat(persisted.getImportance()).isEqualTo(json.get("importance").asInt());
        assertThat(persisted.getId()).isEqualTo(id);
    }

    @Test
    void taskScheduledForTodayIsCreatedActive() throws Exception {
        OffsetDateTime scheduledForToday = todayAt(18, 0);
        String requestBody = """
                {"title": "Evening call", "scheduledFor": "%s"}
                """.formatted(scheduledForToday);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void taskScheduledForAFutureDateIsCreatedScheduled() throws Exception {
        OffsetDateTime future = inDaysAt(3, 9, 0);
        String requestBody = """
                {"title": "Dentist", "scheduledFor": "%s"}
                """.formatted(future);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void blankTitleIsRejectedWith400() throws Exception {
        String requestBody = """
                {"title": "   "}
                """;

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importanceOutsideValidRangeIsRejectedWith400() throws Exception {
        String requestBody = """
                {"title": "Task", "importance": 6}
                """;

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTitleIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private OffsetDateTime todayAt(int hour, int minute) {
        return java.time.ZonedDateTime.of(LocalDate.now(clock), LocalTime.of(hour, minute), clock.getZone())
                .toOffsetDateTime();
    }

    private OffsetDateTime inDaysAt(int days, int hour, int minute) {
        return java.time.ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, minute), clock.getZone())
                .toOffsetDateTime();
    }
}
