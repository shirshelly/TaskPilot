package com.taskpilot.recommendation;

import com.taskpilot.task.dto.TaskResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final Clock clock;

    public RecommendationController(RecommendationService recommendationService, Clock clock) {
        this.recommendationService = recommendationService;
        this.clock = clock;
    }

    // @Positive is enforced by Spring MVC's built-in method validation (0/negative -> 400);
    // non-numeric input already fails type conversion with 400.
    @GetMapping
    public List<TaskResponse> recommendations(@RequestParam(required = false) @Positive Integer availableMinutes) {
        return recommendationService.recommend(availableMinutes).stream()
                .map(task -> TaskResponse.from(task, clock))
                .toList();
    }
}
