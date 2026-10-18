package com.taskpilot.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP -> Controller -> Service -> Repository -> PostgreSQL tests for
 * DELETE /tasks/{id} (§16, §28): hard delete, 204 with no body, 404 when missing.
 *
 * Intentionally not @Transactional, for the same reason as {@link TaskCompletionApiTest}:
 * the service's transaction must really commit for the existence check to prove removal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TaskDeleteApiTest {

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
    void deletingAnActiveTaskReturns204WithNoBodyAndRemovesTheRow() throws Exception {
        Task task = taskRepository.save(Task.create("Buy plant", null, null, null, null, null, clock));
        Task untouched = taskRepository.save(Task.create("Other task", null, null, null, null, null, clock));

        mockMvc.perform(delete("/tasks/{id}", task.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(taskRepository.existsById(task.getId())).isFalse();
        assertThat(taskRepository.existsById(untouched.getId())).isTrue();
    }

    @Test
    void deletionIsNotRestrictedByLifecycleStatus() throws Exception {
        Task completed = Task.create("Done", null, null, null, null, null, clock);
        completed.complete(clock);
        completed = taskRepository.save(completed);

        mockMvc.perform(delete("/tasks/{id}", completed.getId()))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.existsById(completed.getId())).isFalse();
    }

    @Test
    void deletingAMissingTaskReturns404() throws Exception {
        mockMvc.perform(delete("/tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
