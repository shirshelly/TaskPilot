package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import com.taskpilot.task.dto.UpdateTaskRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskService {

    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    static final Sort RECENTLY_COMPLETED_FIRST = Sort.by(Sort.Order.desc("completedAt"), Sort.Order.asc("id"));

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

    /**
     * Deterministic list order (§29): newest first, except COMPLETED, which lists the most recently
     * completed first. The id tie-breaker only makes equal timestamps stable.
     */
    @Transactional(readOnly = true)
    public List<Task> list(TaskStatus status) {
        if (status == null) {
            return taskRepository.findAll(NEWEST_FIRST);
        }
        return taskRepository.findByStatus(status,
                status == TaskStatus.COMPLETED ? RECENTLY_COMPLETED_FIRST : NEWEST_FIRST);
    }

    /**
     * @param expectedVersion the version the client last read (If-Match), or null for no check.
     *        Compared against the task loaded in this transaction, before any field is applied, so
     *        a stale request changes nothing. A write committed by another transaction between this
     *        check and our commit is still caught by @Version at flush (§42).
     */
    @Transactional
    public Task update(UUID id, UpdateTaskRequest request, Long expectedVersion) {
        Task task = findOrThrow(id);
        if (expectedVersion != null && task.getVersion() != expectedVersion) {
            throw new TaskVersionMismatchException(id, expectedVersion);
        }
        // Only fields present in the request are applied; an empty PATCH touches nothing,
        // so updatedAt/version stay put. Any validation failure rolls back every field.
        if (request.hasTitle()) {
            task.rename(request.title(), clock);
        }
        if (request.hasDescription()) {
            task.updateDescription(request.description(), clock);
        }
        if (request.hasImportance()) {
            if (request.importance() == null) {
                throw new TaskValidationException("importance cannot be cleared");
            }
            task.updateImportance(request.importance(), clock);
        }
        if (request.hasEstimatedDurationMinutes()) {
            task.updateEstimatedDurationMinutes(request.estimatedDurationMinutes(), clock);
        }
        if (request.hasDeadline()) {
            task.updateDeadline(request.deadline(), clock);
        }
        return task;
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

    @Transactional
    public Task schedule(UUID id, OffsetDateTime scheduledFor) {
        Task task = findOrThrow(id);
        task.schedule(scheduledFor, clock);
        return task;
    }

    /**
     * Activation use case for the scheduled worker (§41), one transaction per task. Re-evaluates
     * the row's current state: a candidate deleted, completed, or rescheduled into the future since
     * the worker's query is skipped (false), never overwritten. A concurrent change after this
     * reload surfaces at commit as an optimistic-locking failure for the caller to handle.
     */
    @Transactional
    public boolean activateIfDue(UUID id) {
        Optional<Task> candidate = taskRepository.findById(id);
        if (candidate.isEmpty() || !candidate.get().isDueForActivation(clock)) {
            return false;
        }
        candidate.get().activate(clock);
        return true;
    }

    @Transactional
    public void delete(UUID id) {
        // Hard delete (§16). Not deleteById: it silently ignores a missing id, but a missing task
        // must be a 404. Deleting the loaded, versioned entity also makes a concurrent modification
        // fail as an optimistic-locking conflict instead of deleting a row we never saw.
        Task task = findOrThrow(id);
        taskRepository.delete(task);
    }

    private Task findOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
