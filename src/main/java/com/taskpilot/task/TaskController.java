package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import com.taskpilot.task.dto.ScheduleTaskRequest;
import com.taskpilot.task.dto.TaskResponse;
import com.taskpilot.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private static final Pattern VERSION_TAG = Pattern.compile("\"(\\d{1,18})\"");

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

    /**
     * Optional If-Match makes the update conditional on the version the client read (§24, §42):
     * a mismatch is 412 with nothing applied. The body is bound before this method runs, so an
     * unreadable body (unknown property, wrong JSON type) is a 400 even when If-Match is stale;
     * value rules (blank title, importance range) are checked in the domain after the version.
     */
    @PatchMapping("/{id}")
    public TaskResponse updateTask(@PathVariable UUID id, @RequestBody UpdateTaskRequest request,
                                   @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Task task = taskService.update(id, request, parseExpectedVersion(ifMatch));
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

    // The task version is the entity tag. Only a single strong tag such as "3" is accepted;
    // "*", lists, and weak tags (W/"3") are deliberately unsupported in V1 and rejected with 400.
    private static Long parseExpectedVersion(String ifMatch) {
        if (ifMatch == null) {
            return null;
        }
        Matcher matcher = VERSION_TAG.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "If-Match must be a single quoted task version, e.g. \"3\"");
        }
        return Long.parseLong(matcher.group(1));
    }
}
