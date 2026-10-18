package com.taskpilot.common;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskController;
import com.taskpilot.task.TaskService;
import com.taskpilot.task.TaskVersionMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mappings that can't be triggered deterministically through the real stack (a stale write, an
 * unexpected exception), plus the code assigned to Spring MVC's own errors. Domain exception
 * mappings are proven end-to-end in the API tests. A real stale write is covered by
 * TaskRepositoryTest, so only its mapping is tested here.
 */
class ApiExceptionHandlerTest {

    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskService, Clock.systemUTC()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void staleWriteMapsTo409VersionConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(taskService.complete(id)).thenThrow(new ObjectOptimisticLockingFailureException(Task.class, id));

        mockMvc.perform(post("/tasks/{id}/complete", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void failedIfMatchPreconditionMapsTo412VersionConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(taskService.update(eq(id), any(), eq(3L))).thenThrow(new TaskVersionMismatchException(id, 3));

        mockMvc.perform(patch("/tasks/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"New\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingItsMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(taskService.complete(id)).thenThrow(new IllegalStateException("connection to db-internal:5432 refused"));

        String body = mockMvc.perform(post("/tasks/{id}/complete", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("db-internal").doesNotContain("IllegalStateException");
    }

    @Test
    void frameworkBadRequestCarriesValidationFailed() throws Exception {
        mockMvc.perform(get("/tasks/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void otherFrameworkClientErrorsCarryRequestNotSupported() throws Exception {
        mockMvc.perform(put("/tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_SUPPORTED"));
    }
}
