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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
