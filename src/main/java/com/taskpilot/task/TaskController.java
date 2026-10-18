package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import com.taskpilot.task.dto.ScheduleTaskRequest;
import com.taskpilot.task.dto.TaskResponse;
import com.taskpilot.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

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

    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable UUID id) {
        Task task = taskService.get(id);
        return TaskResponse.from(task, clock);
    }

    @GetMapping
    public List<TaskResponse> listTasks(@RequestParam(required = false) TaskStatus status) {
        return taskService.list(status).stream()
                .map(task -> TaskResponse.from(task, clock))
                .toList();
    }

    @PatchMapping("/{id}")
    public TaskResponse updateTask(@PathVariable UUID id, @RequestBody UpdateTaskRequest request) {
        Task task = taskService.update(id, request);
        return TaskResponse.from(task, clock);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable UUID id) {
        taskService.delete(id);
    }

    @PostMapping("/{id}/complete")
    public TaskResponse completeTask(@PathVariable UUID id) {
        Task task = taskService.complete(id);
        return TaskResponse.from(task, clock);
    }

    @PostMapping("/{id}/reopen")
    public TaskResponse reopenTask(@PathVariable UUID id) {
        Task task = taskService.reopen(id);
        return TaskResponse.from(task, clock);
    }

    @PostMapping("/{id}/schedule")
    public TaskResponse scheduleTask(@PathVariable UUID id, @Valid @RequestBody ScheduleTaskRequest request) {
        Task task = taskService.schedule(id, request.scheduledFor());
        return TaskResponse.from(task, clock);
    }
}
