package com.taskpilot.agenda;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * The service owns one thing: deriving "today" and the start-of-tomorrow bound from the
 * injected Clock's zone. Membership itself is proven against PostgreSQL in TaskRepositoryTest.
 */
@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Test
    void derivesTodayAndStartOfTomorrowInTheConfiguredZone() {
        // 22:00Z on Aug 25 is already 01:00 Aug 26 in Jerusalem: "today" must be Aug 26, not the UTC date.
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T22:00:00Z"), ZoneId.of("Asia/Jerusalem"));
        Task task = Task.create("Task", null, null, null, null, null, clock);
        when(taskRepository.findAgendaTasks(any(), any())).thenReturn(List.of(task));

        List<Task> result = new AgendaService(taskRepository, clock).today();

        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<OffsetDateTime> startOfTomorrow = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(taskRepository).findAgendaTasks(today.capture(), startOfTomorrow.capture());
        assertThat(today.getValue()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(startOfTomorrow.getValue().toInstant()).isEqualTo(Instant.parse("2026-08-26T21:00:00Z"));
        assertThat(result).containsExactly(task);
    }
}
