package com.taskpilot.recommendation;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskRepository;
import com.taskpilot.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration only: ACTIVE-only fetch, duration eligibility before ranking, and the ranker's
 * order returned as-is. Scoring is covered by RecommendationRankerTest.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RecommendationRanker ranker;

    private final Task tenMinutes = task(10);
    private final Task thirtyMinutes = task(30);
    private final Task unknownDuration = task(null);

    // --- duration eligibility (pure) ---

    @Test
    void withAvailableMinutesOnlyKnownDurationsWithinTheLimitFit() {
        assertThat(RecommendationService.fitsAvailableTime(tenMinutes, 20)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(task(20), 20)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(thirtyMinutes, 20)).isFalse();
        assertThat(RecommendationService.fitsAvailableTime(unknownDuration, 20)).isFalse();
    }

    @Test
    void withAvailableMinutesTheMinimumSessionDecidesWhenPresent() {
        // Ongoing work: progress is possible in one session even if the whole task is far longer.
        assertThat(RecommendationService.fitsAvailableTime(task(600, 10), 15)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(task(600, 15), 15)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(task(600, 20), 15)).isFalse();
        // The session wins over the total in both directions, and works without a total.
        assertThat(RecommendationService.fitsAvailableTime(task(10, 20), 15)).isFalse();
        assertThat(RecommendationService.fitsAvailableTime(task(null, 10), 15)).isTrue();
    }

    @Test
    void withAvailableMinutesTheTotalEstimateIsTheFallbackAndNeitherMeansUnknown() {
        assertThat(RecommendationService.fitsAvailableTime(task(10, null), 15)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(task(null, null), 15)).isFalse();
    }

    @Test
    void withoutAvailableMinutesEveryDurationFitsIncludingUnknown() {
        assertThat(RecommendationService.fitsAvailableTime(tenMinutes, null)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(thirtyMinutes, null)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(unknownDuration, null)).isTrue();
        assertThat(RecommendationService.fitsAvailableTime(task(600, 20), null)).isTrue();
    }

    // --- orchestration ---

    @Test
    void ranksOnlyActiveTasksThatFitAndReturnsTheRankersOrder() {
        RecommendationService service = new RecommendationService(taskRepository, ranker);
        when(taskRepository.findByStatus(TaskStatus.ACTIVE))
                .thenReturn(List.of(tenMinutes, thirtyMinutes, unknownDuration));
        when(ranker.rank(List.of(tenMinutes))).thenReturn(List.of(tenMinutes));

        List<Task> result = service.recommend(20);

        verify(taskRepository).findByStatus(TaskStatus.ACTIVE);
        verify(ranker).rank(List.of(tenMinutes));
        assertThat(result).containsExactly(tenMinutes);
    }

    @Test
    void withoutAvailableMinutesAllActiveTasksAreRankedInTheRankersOrder() {
        RecommendationService service = new RecommendationService(taskRepository, ranker);
        List<Task> active = List.of(tenMinutes, thirtyMinutes, unknownDuration);
        when(taskRepository.findByStatus(TaskStatus.ACTIVE)).thenReturn(active);
        when(ranker.rank(active)).thenReturn(List.of(unknownDuration, tenMinutes, thirtyMinutes));

        assertThat(service.recommend(null)).containsExactly(unknownDuration, tenMinutes, thirtyMinutes);
    }

    private static Task task(Integer durationMinutes) {
        return Task.create("Task", null, null, durationMinutes, null, null, CLOCK);
    }

    private static Task task(Integer durationMinutes, Integer minSessionMinutes) {
        return Task.create("Task", null, null, durationMinutes, minSessionMinutes, null, null, CLOCK);
    }
}
