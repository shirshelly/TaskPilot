package com.taskpilot.task;

import com.taskpilot.FixedClockTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for
 * PATCH /tasks/{id} (§24, §43). Focus is the JSON tri-state semantics (absent / null / value)
 * and that only non-lifecycle fields change; value rules live in {@link TaskTest}.
 *
 * Intentionally not @Transactional, for the same reason as {@link TaskCompletionApiTest}:
 * the service's transaction must really commit (or roll back) before the re-read.
 */
@Import(FixedClockTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TaskUpdateApiTest {

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
    void patchingTitleOnlyChangesTitleAndLeavesEverythingElseIntact() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), """
                        {"title": "New title"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("Old description"));

        Task persisted = reload(before);
        assertThat(persisted.getTitle()).isEqualTo("New title");
        assertThat(persisted.getDescription()).isEqualTo(before.getDescription());
        assertThat(persisted.getImportance()).isEqualTo(before.getImportance());
        assertThat(persisted.getEstimatedDurationMinutes()).isEqualTo(before.getEstimatedDurationMinutes());
        assertThat(persisted.getDeadline()).isEqualTo(before.getDeadline());
        assertLifecycleUnchanged(before, persisted);
        assertThat(persisted.getVersion()).isEqualTo(before.getVersion() + 1);
    }

    @Test
    void emptyPatchIsANoOpAndBumpsNeitherUpdatedAtNorVersion() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), "{}"))
                .andExpect(status().isOk());

        Task persisted = reload(before);
        assertThat(persisted.getDescription()).isEqualTo(before.getDescription());
        assertThat(persisted.getEstimatedDurationMinutes()).isEqualTo(before.getEstimatedDurationMinutes());
        assertThat(persisted.getDeadline()).isEqualTo(before.getDeadline());
        assertThat(persisted.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(persisted.getVersion()).isEqualTo(before.getVersion());
    }

    @Test
    void explicitNullsClearNullableFieldsAndLeaveOmittedOnesIntact() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), """
                        {"description": null, "estimatedDurationMinutes": null, "deadline": null}
                        """))
                .andExpect(status().isOk());

        Task persisted = reload(before);
        assertThat(persisted.getDescription()).isNull();
        assertThat(persisted.getEstimatedDurationMinutes()).isNull();
        assertThat(persisted.getDeadline()).isNull();
        assertThat(persisted.getTitle()).isEqualTo(before.getTitle());
        assertThat(persisted.getImportance()).isEqualTo(before.getImportance());
        assertLifecycleUnchanged(before, persisted);
    }

    @Test
    void multipleSuppliedFieldsAreAllUpdated() throws Exception {
        Task before = seedScheduledTaskWithAllFields();
        LocalDate pastDeadline = LocalDate.now(clock).minusDays(1);

        mockMvc.perform(patchTask(before.getId(), """
                        {"title": "Renamed", "description": "New notes", "importance": 5,
                         "estimatedDurationMinutes": 45, "deadline": "%s"}
                        """.formatted(pastDeadline)))
                .andExpect(status().isOk());

        Task persisted = reload(before);
        assertThat(persisted.getTitle()).isEqualTo("Renamed");
        assertThat(persisted.getDescription()).isEqualTo("New notes");
        assertThat(persisted.getImportance()).isEqualTo(5);
        assertThat(persisted.getEstimatedDurationMinutes()).isEqualTo(45);
        assertThat(persisted.getDeadline()).isEqualTo(pastDeadline);
        assertLifecycleUnchanged(before, persisted);
    }

    @Test
    void oneInvalidFieldRejectsTheWholePatchWith400() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        // title is valid and applied first in the transaction; importance then fails -> rollback.
        mockMvc.perform(patchTask(before.getId(), """
                        {"title": "Would be renamed", "importance": 9}
                        """))
                .andExpect(status().isBadRequest());

        assertRowUnchanged(before);
    }

    @Test
    void explicitNullForANonNullableFieldReturns400() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), """
                        {"title": null}
                        """))
                .andExpect(status().isBadRequest());

        assertRowUnchanged(before);
    }

    @Test
    void lifecycleFieldInTheBodyRejectsTheWholePatchWith400() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        // Lifecycle changes go through the explicit commands (§24). The valid title alongside
        // it must not be applied either.
        mockMvc.perform(patchTask(before.getId(), """
                        {"title": "Would be renamed", "status": "COMPLETED"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                // Body-parsing failures get Spring's generic detail, never the Jackson message.
                .andExpect(jsonPath("$.detail").value("Failed to read request"));

        assertRowUnchanged(before);
    }

    @Test
    void misspelledPropertyReturns400InsteadOfBeingSilentlyIgnored() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), """
                        {"descripton": null}
                        """))
                .andExpect(status().isBadRequest());

        assertThat(reload(before).getDescription()).isEqualTo(before.getDescription());
    }

    @Test
    void patchingAMissingTaskReturns404() throws Exception {
        mockMvc.perform(patchTask(UUID.randomUUID(), """
                        {"title": "Anything"}
                        """))
                .andExpect(status().isNotFound());
    }

    // --- conditional PATCH (If-Match, §24 / §42) ---

    @Test
    void ifMatchWithTheCurrentVersionAppliesAndReturnsTheNewVersion() throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), """
                        {"title": "New title"}
                        """).header(HttpHeaders.IF_MATCH, versionTag(before.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.version").value((int) before.getVersion() + 1));

        assertThat(reload(before).getVersion()).isEqualTo(before.getVersion() + 1);
    }

    @Test
    void staleIfMatchReturns412AndAppliesNothing() throws Exception {
        Task original = seedScheduledTaskWithAllFields();
        mockMvc.perform(patchTask(original.getId(), """
                        {"description": "Changed elsewhere"}
                        """))
                .andExpect(status().isOk());
        Task current = reload(original);

        mockMvc.perform(patchTask(original.getId(), """
                        {"title": "Based on a stale read"}
                        """).header(HttpHeaders.IF_MATCH, versionTag(original.getVersion())))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        assertRowUnchanged(current);
        assertThat(reload(original).getDescription()).isEqualTo("Changed elsewhere");
    }

    @Test
    void versionIsCheckedBeforeDomainValidation() throws Exception {
        Task original = seedScheduledTaskWithAllFields();
        bumpVersion(original);

        // Blank title is a domain rule (no @Valid on PATCH), evaluated after the version check.
        mockMvc.perform(patchTask(original.getId(), """
                        {"title": "   "}
                        """).header(HttpHeaders.IF_MATCH, versionTag(original.getVersion())))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void unreadableBodyIsRejectedBeforeTheVersionCheck() throws Exception {
        Task original = seedScheduledTaskWithAllFields();
        bumpVersion(original);

        // Body binding happens before the controller method runs, so this never reaches the check.
        mockMvc.perform(patchTask(original.getId(), """
                        {"status": "COMPLETED"}
                        """).header(HttpHeaders.IF_MATCH, versionTag(original.getVersion())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "W/\"0\"", "*", "\"0\", \"1\"", "\"abc\"", "\"\""})
    void malformedIfMatchReturns400AndAppliesNothing(String ifMatch) throws Exception {
        Task before = seedScheduledTaskWithAllFields();

        mockMvc.perform(patchTask(before.getId(), """
                        {"title": "Would be renamed"}
                        """).header(HttpHeaders.IF_MATCH, ifMatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertRowUnchanged(before);
    }

    @Test
    void ifMatchOnAMissingTaskReturns404() throws Exception {
        mockMvc.perform(patchTask(UUID.randomUUID(), """
                        {"title": "Anything"}
                        """).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    private void bumpVersion(Task task) throws Exception {
        mockMvc.perform(patchTask(task.getId(), """
                        {"importance": 4}
                        """))
                .andExpect(status().isOk());
    }

    private static String versionTag(long version) {
        return "\"" + version + "\"";
    }

    private Task seedScheduledTaskWithAllFields() {
        return taskRepository.save(Task.create("Old title", "Old description", 2, 30,
                LocalDate.now(clock).plusDays(5), inDaysAt(2, 9), clock));
    }

    private Task reload(Task task) {
        return taskRepository.findById(task.getId()).orElseThrow();
    }

    private void assertLifecycleUnchanged(Task before, Task persisted) {
        assertThat(persisted.getStatus()).isEqualTo(before.getStatus());
        assertThat(persisted.getScheduledFor().toInstant()).isEqualTo(before.getScheduledFor().toInstant());
        assertThat(persisted.getCompletedAt()).isEqualTo(before.getCompletedAt());
        assertThat(persisted.getCreatedAt()).isEqualTo(before.getCreatedAt());
    }

    private void assertRowUnchanged(Task before) {
        Task persisted = reload(before);
        assertThat(persisted.getTitle()).isEqualTo(before.getTitle());
        assertThat(persisted.getImportance()).isEqualTo(before.getImportance());
        assertThat(persisted.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(persisted.getVersion()).isEqualTo(before.getVersion());
        assertLifecycleUnchanged(before, persisted);
    }

    private MockHttpServletRequestBuilder patchTask(UUID id, String body) {
        return patch("/tasks/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private OffsetDateTime inDaysAt(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(clock).plusDays(days), LocalTime.of(hour, 0), clock.getZone())
                .toOffsetDateTime();
    }
}
