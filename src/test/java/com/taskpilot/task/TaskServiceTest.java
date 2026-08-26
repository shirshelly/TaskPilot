package com.taskpilot.task;

import com.taskpilot.task.dto.CreateTaskRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
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
    void listWithNoStatusReturnsAllTasks() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        when(taskRepository.findAll()).thenReturn(List.of(task));

        List<Task> result = taskService.list(null);

        assertThat(result).containsExactly(task);
        verify(taskRepository, never()).findByStatus(any());
    }

    @Test
    void listWithStatusDelegatesToFindByStatus() {
        TaskService taskService = new TaskService(taskRepository, CLOCK);
        Task task = Task.create("Task", null, null, null, null, null, CLOCK);
        when(taskRepository.findByStatus(TaskStatus.ACTIVE)).thenReturn(List.of(task));

        List<Task> result = taskService.list(TaskStatus.ACTIVE);

        assertThat(result).containsExactly(task);
        verify(taskRepository, never()).findAll();
    }
}
