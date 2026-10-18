package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public Task create(CreateTaskRequest request) {
        Task task = Task.create(
                request.title(),
                request.description(),
                request.importance(),
                request.estimatedDurationMinutes(),
                request.deadline(),
                request.scheduledFor(),
                clock);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Task get(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<Task> list(TaskStatus status) {
        return status == null ? taskRepository.findAll() : taskRepository.findByStatus(status);
    }

    @Transactional
    public Task complete(UUID id) {
        Task task = findOrThrow(id);
        // No explicit save(): the task is managed, so dirty checking flushes the change on
        // commit, and a stale @Version surfaces there as an optimistic-locking failure (§42).
        task.complete(clock);
        return task;
    }

    @Transactional
    public Task reopen(UUID id) {
        Task task = findOrThrow(id);
        task.reopen(clock);
        return task;
    }

    private Task findOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
