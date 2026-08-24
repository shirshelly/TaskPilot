package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
}
