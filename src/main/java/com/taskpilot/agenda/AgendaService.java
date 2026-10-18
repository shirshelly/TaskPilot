package com.taskpilot.agenda;

import com.taskpilot.common.DayBoundaries;
import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Today's Agenda (§3.1, §30): a computed read-only view, not an entity. It observes current
 * task state and never repairs it; activating due SCHEDULED tasks is the worker's job.
 */
@Service
public class AgendaService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    public AgendaService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Task> today() {
        return taskRepository.findAgendaTasks(LocalDate.now(clock), DayBoundaries.startOfTomorrow(clock));
    }
}
