package com.taskpilot.recommendation;

import com.taskpilot.task.Task;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic V1 ranking (§31). Ranks tasks that are already eligible; it never decides
 * actionability or applies availableMinutes, never queries the database, never mutates tasks.
 * Scores are internal and not exposed by the API.
 */
@Component
public class RecommendationRanker {

    private static final int IMPORTANCE_WEIGHT = 10;
    private static final int MAX_AGE_POINTS = 5;

    // §31 ordering contract. The final id comparison exists only for determinism.
    private static final Comparator<ScoredTask> ORDER = Comparator
            .comparingInt(ScoredTask::score).reversed()
            .thenComparing(scored -> scored.task().getDeadline(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(scored -> scored.task().getEstimatedDurationMinutes(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(scored -> scored.task().getCreatedAt())
            .thenComparing(scored -> scored.task().getId());

    private final Clock clock;

    public RecommendationRanker(Clock clock) {
        this.clock = clock;
    }

    public List<Task> rank(List<Task> eligibleTasks) {
        // Read "today" once so every task in one ranking is scored against the same date.
        LocalDate today = LocalDate.now(clock);
        return eligibleTasks.stream()
                .map(task -> new ScoredTask(task, score(task, today)))
                .sorted(ORDER)
                .map(ScoredTask::task)
                .toList();
    }

    int score(Task task, LocalDate today) {
        return task.getImportance() * IMPORTANCE_WEIGHT + deadlinePoints(task, today) + agePoints(task, today);
    }

    private static int deadlinePoints(Task task, LocalDate today) {
        LocalDate deadline = task.getDeadline();
        if (deadline == null) {
            return 0;
        }
        long daysUntil = ChronoUnit.DAYS.between(today, deadline);
        if (daysUntil < 0) {
            return 30; // flat: being further overdue does not keep raising the score
        }
        if (daysUntil == 0) {
            return 25;
        }
        if (daysUntil <= 3) {
            return 15;
        }
        if (daysUntil <= 7) {
            return 5;
        }
        return 0;
    }

    private int agePoints(Task task, LocalDate today) {
        LocalDate createdOn = task.getCreatedAt().atZone(clock.getZone()).toLocalDate();
        long ageDays = ChronoUnit.DAYS.between(createdOn, today);
        return (int) Math.min(Math.max(ageDays, 0), MAX_AGE_POINTS);
    }

    private record ScoredTask(Task task, int score) {
    }
}
