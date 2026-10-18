package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import com.taskpilot.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests only the orchestration TaskService owns (repository interaction, Clock usage) --
 * status derivation and other business rules already belong to and are covered by
 * {@link TaskTest}, so they are not repeated here.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private TaskRepository taskRepository;

    @Test
    void createsAndSavesATaskUsingTheInjectedClock() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        CreateTaskRequest request = new CreateTaskRequest("Buy plant", null, null, null, null, null);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task result = taskService.create(request);

        ArgumentCaptor<Task> savedTaskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(savedTaskCaptor.capture());
        Task savedTask = savedTaskCaptor.getValue();

        assertThat(savedTask).isSameAs(result);
        assertThat(savedTask.getTitle()).isEqualTo("Buy plant");
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(savedTask.getImportance()).isEqualTo(Task.DEFAULT_IMPORTANCE);
        assertThat(savedTask.getCreatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void derivesScheduledStatusForAFutureScheduledForUsingTheInjectedClock() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        OffsetDateTime future = TODAY.plusDays(2).atTime(9, 0).atOffset(ZoneOffset.UTC);
        CreateTaskRequest request = new CreateTaskRequest("Dentist", null, null, null, null, future);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task result = taskService.create(request);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(result.getScheduledFor()).isEqualTo(future);
    }

    @Test
    void getReturnsTheTaskFoundByRepository() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        Task result = taskService.get(task.getId());

        assertThat(result).isSameAs(task);
    }

    @Test
    void getThrowsTaskNotFoundExceptionWhenRepositoryFindsNothing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.get(id)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void listWithNoStatusReturnsAllTasksNewestFirst() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        when(taskRepository.findAll(TaskService.NEWEST_FIRST)).thenReturn(List.of(task));

        List<Task> result = taskService.list(null);

        assertThat(result).containsExactly(task);
        verify(taskRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listWithAnOpenStatusDelegatesToFindByStatusNewestFirst() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        when(taskRepository.findByStatus(TaskStatus.ACTIVE, TaskService.NEWEST_FIRST)).thenReturn(List.of(task));

        List<Task> result = taskService.list(TaskStatus.ACTIVE);

        assertThat(result).containsExactly(task);
    }

    @Test
    void listOfCompletedTasksIsOrderedByCompletionTime() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        task.complete(CLOCK);
        when(taskRepository.findByStatus(TaskStatus.COMPLETED, TaskService.RECENTLY_COMPLETED_FIRST))
                .thenReturn(List.of(task));

        List<Task> result = taskService.list(TaskStatus.COMPLETED);

        assertThat(result).containsExactly(task);
    }

    @Test
    void completeLoadsTheTaskAndCompletesItUsingTheInjectedClock() {
        Clock creationClock = Clock.offset(CLOCK, Duration.ofDays(-1));
        Task task = Task.create("Task", null, null, null, null, null, creationClock);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        Task result = taskService.complete(task.getId());

        assertThat(result).isSameAs(task);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void completeThrowsTaskNotFoundExceptionWhenRepositoryFindsNothing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.complete(id)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void reopenLoadsTheTaskAndReopensItUsingTheInjectedClock() {
        Clock earlierClock = Clock.offset(CLOCK, Duration.ofDays(-1));
        Task task = Task.create("Task", null, null, null, null, null, earlierClock);
        task.complete(earlierClock);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        Task result = taskService.reopen(task.getId());

        assertThat(result).isSameAs(task);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(result.getUpdatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void reopenThrowsTaskNotFoundExceptionWhenRepositoryFindsNothing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.reopen(id)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyFieldsPresentInTheRequest() {
        Task task = Task.create("Old title", "Keep me", 2, 30, TODAY.plusDays(3), null,
                Clock.offset(CLOCK, Duration.ofDays(-1)));
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("New title");
        request.setDeadline(null);

        Task result = taskService.update(task.getId(), request, null);

        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getDeadline()).isNull();
        assertThat(result.getDescription()).isEqualTo("Keep me");
        assertThat(result.getImportance()).isEqualTo(2);
        assertThat(result.getEstimatedDurationMinutes()).isEqualTo(30);
        assertThat(result.getUpdatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void emptyUpdateLeavesTheTaskUntouched() {
        Clock creationClock = Clock.offset(CLOCK, Duration.ofDays(-1));
        Task task = Task.create("Task", "Notes", null, null, null, null, creationClock);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        Task result = taskService.update(task.getId(), new UpdateTaskRequest(), null);

        assertThat(result.getUpdatedAt()).isEqualTo(creationClock.instant());
        assertThat(result.getDescription()).isEqualTo("Notes");
    }

    @Test
    void updateRejectsExplicitNullImportance() {
        Task task = Task.create("Task", null, 4, null, null, null, CLOCK);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setImportance(null);

        assertThatThrownBy(() -> taskService.update(task.getId(), request, null))
                .isInstanceOf(TaskValidationException.class);
        assertThat(task.getImportance()).isEqualTo(4);
    }

    @Test
    void updateWithAStaleExpectedVersionThrowsBeforeApplyingAnyField() {
        Clock creationClock = Clock.offset(CLOCK, Duration.ofDays(-1));
        Task task = Task.create("Old title", null, null, null, null, null, creationClock);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("New title");

        assertThatThrownBy(() -> taskService.update(task.getId(), request, task.getVersion() + 1))
                .isInstanceOf(TaskVersionMismatchException.class);
        assertThat(task.getTitle()).isEqualTo("Old title");
        assertThat(task.getUpdatedAt()).isEqualTo(creationClock.instant());
    }

    @Test
    void updateWithTheCurrentExpectedVersionApplies() {
        Task task = Task.create("Old title", null, null, null, null, null, CLOCK);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("New title");

        Task result = taskService.update(task.getId(), request, task.getVersion());

        assertThat(result.getTitle()).isEqualTo("New title");
    }

    @Test
    void updateThrowsTaskNotFoundExceptionWhenRepositoryFindsNothing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(id, new UpdateTaskRequest(), null))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void activateIfDueActivatesACurrentlyDueScheduledTask() {
        Task task = scheduledOnAug20For(TODAY.minusDays(1));
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThat(taskService.activateIfDue(task.getId())).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(task.getUpdatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void activateIfDueSkipsACandidateRescheduledIntoTheFuture() {
        Task task = scheduledOnAug20For(TODAY.plusDays(3));
        Instant updatedAtBefore = task.getUpdatedAt();
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThat(taskService.activateIfDue(task.getId())).isFalse();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(task.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }

    @Test
    void activateIfDueLeavesACandidateThatIsNoLongerScheduledUntouched() {
        Task completed = scheduledOnAug20For(TODAY.minusDays(1));
        completed.complete(CLOCK);
        Instant updatedAtBefore = completed.getUpdatedAt();
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(completed.getId())).thenReturn(Optional.of(completed));

        assertThat(taskService.activateIfDue(completed.getId())).isFalse();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }

    @Test
    void activateIfDueSkipsADeletedCandidateWithoutThrowing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(taskService.activateIfDue(id)).isFalse();
    }

    private static Task scheduledOnAug20For(LocalDate scheduledDate) {
        Clock aug20 = Clock.offset(CLOCK, Duration.ofDays(-5));
        Task task = Task.create("Task", null, null, null, null,
                scheduledDate.atTime(9, 0).atOffset(ZoneOffset.UTC), aug20);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        return task;
    }

    @Test
    void deleteRemovesTheLoadedTask() {
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        taskService.delete(task.getId());

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteOfAMissingTaskThrowsTaskNotFoundExceptionAndDeletesNothing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(id)).isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void scheduleLoadsTheTaskAndSchedulesItUsingTheInjectedClock() {
        Clock earlierClock = Clock.offset(CLOCK, Duration.ofDays(-1));
        Task task = Task.create("Task", null, null, null, null, null, earlierClock);
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        OffsetDateTime future = TODAY.plusDays(2).atTime(9, 0).atOffset(ZoneOffset.UTC);

        Task result = taskService.schedule(task.getId(), future);

        assertThat(result).isSameAs(task);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(result.getScheduledFor()).isEqualTo(future);
        assertThat(result.getUpdatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void scheduleThrowsTaskNotFoundExceptionWhenRepositoryFindsNothing() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());
        OffsetDateTime future = TODAY.plusDays(2).atTime(9, 0).atOffset(ZoneOffset.UTC);

        assertThatThrownBy(() -> taskService.schedule(id, future)).isInstanceOf(TaskNotFoundException.class);
    }
}
