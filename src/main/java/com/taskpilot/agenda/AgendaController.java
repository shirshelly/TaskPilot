package com.taskpilot.agenda;

import com.taskpilot.task.dto.TaskResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/agenda")
public class AgendaController {

    private final AgendaService agendaService;
    private final Clock clock;

    public AgendaController(AgendaService agendaService, Clock clock) {
        this.agendaService = agendaService;
        this.clock = clock;
    }

    @GetMapping("/today")
    public List<TaskResponse> today() {
        return agendaService.today().stream()
                .map(task -> TaskResponse.from(task, clock))
                .toList();
    }
}
