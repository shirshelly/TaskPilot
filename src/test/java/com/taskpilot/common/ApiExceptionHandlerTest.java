package com.taskpilot.common;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskController;
import com.taskpilot.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the stale-write -> 409 mapping (§42). A real stale write can't be triggered
 * deterministically through one HTTP call, and TaskRepositoryTest already proves the stack
 * raises ObjectOptimisticLockingFailureException, so only the mapping is tested here.
 */
class ApiExceptionHandlerTest {

    @Test
    void staleWriteMapsTo409() throws Exception {
        TaskService taskService = mock(TaskService.class);
        UUID id = UUID.randomUUID();
        when(taskService.complete(id))
                .thenThrow(new ObjectOptimisticLockingFailureException(Task.class, id));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskService, Clock.systemUTC()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/tasks/{id}/complete", id))
                .andExpect(status().isConflict());
    }
}
