package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import com.taskpilot.task.dto.TaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final Clock clock;

    public TaskController(TaskService taskService, Clock clock) {
        this.taskService = taskService;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.create(request);
        return TaskResponse.from(task, clock);
    }
}
