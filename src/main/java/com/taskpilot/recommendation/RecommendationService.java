package com.taskpilot.recommendation;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import com.taskpilot.task.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recommendations pipeline (§3.2, §31): actionability -> duration eligibility -> ranking.
 * Each stage is separate so the ranker never sees a task it shouldn't rank.
 */
@Service
public class RecommendationService {

    private final TaskRepository taskRepository;
    private final RecommendationRanker ranker;

    public RecommendationService(TaskRepository taskRepository, RecommendationRanker ranker) {
        this.taskRepository = taskRepository;
        this.ranker = ranker;
    }

    @Transactional(readOnly = true)
    public List<Task> recommend(Integer availableMinutes) {
        // V1 actionability: status == ACTIVE (§8), applied in the query.
        List<Task> eligible = taskRepository.findByStatus(TaskStatus.ACTIVE).stream()
                .filter(task -> fitsAvailableTime(task, availableMinutes))
                .toList();
        return ranker.rank(eligible);
    }

    /**
     * Without a time limit everything fits. With one, a task fits if the user can make meaningful
     * progress in that time (§3.2): its minimum session when it has one (work spread over
     * several sittings), otherwise its total estimate (work done in one go). With neither,
     * the time it needs is unknown, so it does not fit.
     */
    static boolean fitsAvailableTime(Task task, Integer availableMinutes) {
        if (availableMinutes == null) {
            return true;
        }
        Integer required = requiredMinutes(task);
        return required != null && required <= availableMinutes;
    }

    static Integer requiredMinutes(Task task) {
        return task.getMinSessionMinutes() != null ? task.getMinSessionMinutes() : task.getEstimatedDurationMinutes();
    }
}
