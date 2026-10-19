package com.taskpilot.recommendation;

import com.taskpilot.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the §31 formula and ordering. No Spring, no DB: the ranker only ever
 * sees already-eligible tasks, so actionability/availableMinutes are tested elsewhere.
 */
class RecommendationRankerTest {

    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);
    // Noon Aug 25 in Jerusalem.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T09:00:00Z"), JERUSALEM);

    private final RecommendationRanker ranker = new RecommendationRanker(CLOCK);

    // --- score components ---

    @Test
    void importanceContributesTenPointsPerLevel() {
        assertThat(ranker.score(task(1, null, null, 0), TODAY)).isEqualTo(10);
        assertThat(ranker.score(task(5, null, null, 0), TODAY)).isEqualTo(50);
    }

    @ParameterizedTest(name = "deadline in {0} days -> {1} points")
    @CsvSource({
            "-30, 30", // overdue: flat bonus regardless of how late
            "-1,  30",
            "0,   25",
            "1,   15",
            "3,   15",
            "4,   5",
            "7,   5",
            "8,   0",
    })
    void deadlineBands(int daysUntilDeadline, int expectedPoints) {
        Task task = task(1, TODAY.plusDays(daysUntilDeadline), null, 0);

        assertThat(ranker.score(task, TODAY)).isEqualTo(10 + expectedPoints);
    }

    @Test
    void noDeadlineScoresOnImportanceAndAgeAlone() {
        assertThat(ranker.score(task(3, null, null, 2), TODAY)).isEqualTo(32);
    }

    @ParameterizedTest(name = "created {0} days ago -> {1} points")
    @CsvSource({
            "0,  0",
            "1,  1",
            "5,  5",
            "20, 5",  // capped
            "-1, 0",  // never negative
    })
    void agePointsAreCappedAtFive(int ageDays, int expectedPoints) {
        assertThat(ranker.score(task(1, null, null, ageDays), TODAY)).isEqualTo(10 + expectedPoints);
    }

    @Test
    void ageUsesTheConfiguredZoneLocalDate() {
        // 22:00Z Aug 24 is already 01:00 Aug 25 in Jerusalem: created "today", so age 0
        // (by UTC date it would be 1 day old).
        Clock lateUtc = Clock.fixed(Instant.parse("2026-08-24T22:00:00Z"), JERUSALEM);
        Task task = Task.create("Task", null, 1, null, null, null, lateUtc);

        assertThat(ranker.score(task, TODAY)).isEqualTo(10);
    }

    // --- ordering ---

    @Test
    void higherImportanceRanksFirstWhenOtherFactorsAreEqual() {
        Task low = task(2, null, null, 0);
        Task high = task(4, null, null, 0);

        assertThat(ranker.rank(List.of(low, high))).containsExactly(high, low);
    }

    @Test
    void importanceUrgencyAndAgeCombineAsDocumented() {
        Task t1 = named("T1", 5, null, null, 0);              // 50
        Task t2 = named("T2", 3, TODAY.plusDays(1), null, 2); // 30 + 15 + 2 = 47
        Task t3 = named("T3", 2, TODAY.minusDays(1), null, 8); // 20 + 30 + 5 = 55
        Task t4 = named("T4", 3, null, null, 30);             // 30 + 0 + 5 = 35
        Task t5 = named("T5", 1, TODAY.minusDays(9), null, 60); // 10 + 30 + 5 = 45

        assertThat(ranker.rank(List.of(t1, t2, t3, t4, t5)))
                .extracting(Task::getTitle)
                .containsExactly("T3", "T1", "T2", "T5", "T4");
    }

    @Test
    void equalScoresAreBrokenByEarlierDeadlineWithNoDeadlineLast() {
        // All score 10: 8+ days and no deadline are both worth 0 points.
        Task inTwelveDays = task(1, TODAY.plusDays(12), null, 0);
        Task inNineDays = task(1, TODAY.plusDays(9), null, 0);
        Task noDeadline = task(1, null, null, 0);

        assertThat(ranker.rank(List.of(noDeadline, inTwelveDays, inNineDays)))
                .containsExactly(inNineDays, inTwelveDays, noDeadline);
    }

    @Test
    void thenByShorterDurationWithUnknownDurationLast() {
        Task unknown = task(3, null, null, 0);
        Task longTask = task(3, null, 60, 0);
        Task shortTask = task(3, null, 10, 0);

        assertThat(ranker.rank(List.of(unknown, longTask, shortTask))).containsExactly(shortTask, longTask, unknown);
    }

    @Test
    void minimumSessionNeitherScoresNorBreaksTiesTheTotalEstimateDoes() {
        // Shorter session but longer total vs. longer session but shorter total: the total decides.
        Task longTotalShortSessions = withMinSession(task(3, null, 600, 0), 10);
        Task shortTotalLongSessions = withMinSession(task(3, null, 60, 0), 45);

        assertThat(ranker.score(longTotalShortSessions, TODAY)).isEqualTo(ranker.score(shortTotalLongSessions, TODAY));
        assertThat(ranker.rank(List.of(longTotalShortSessions, shortTotalLongSessions)))
                .containsExactly(shortTotalLongSessions, longTotalShortSessions);
    }

    @Test
    void thenByOlderCreatedAt() {
        // Both are past the age cap, so they score the same; only createdAt separates them.
        Task tenDaysOld = task(3, null, null, 10);
        Task twentyDaysOld = task(3, null, null, 20);

        assertThat(ranker.rank(List.of(tenDaysOld, twentyDaysOld))).containsExactly(twentyDaysOld, tenDaysOld);
    }

    @Test
    void finallyById() {
        Task a = task(3, null, null, 0);
        Task b = task(3, null, null, 0); // identical data and createdAt; only the id differs
        List<Task> byId = new ArrayList<>(List.of(a, b));
        byId.sort(Comparator.comparing(Task::getId));

        assertThat(ranker.rank(List.of(b, a))).containsExactlyElementsOf(byId);
        assertThat(ranker.rank(List.of(a, b))).containsExactlyElementsOf(byId);
    }

    @Test
    void inputOrderNeverAffectsOutputOrder() {
        List<Task> tasks = List.of(
                task(5, null, null, 0), task(3, TODAY.plusDays(1), 30, 2), task(2, TODAY.minusDays(1), null, 8),
                task(3, null, 15, 30), task(1, TODAY, 5, 1), task(3, null, 15, 30), task(4, TODAY.plusDays(6), null, 3));
        List<Task> expected = ranker.rank(tasks);

        for (long seed = 0; seed < 20; seed++) {
            List<Task> shuffled = new ArrayList<>(tasks);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(ranker.rank(shuffled)).containsExactlyElementsOf(expected);
        }
    }

    @Test
    void emptyInputRanksToEmptyOutput() {
        assertThat(ranker.rank(List.of())).isEmpty();
    }

    private static Task task(int importance, LocalDate deadline, Integer durationMinutes, int ageDays) {
        return named("Task", importance, deadline, durationMinutes, ageDays);
    }

    private static Task withMinSession(Task task, int minSessionMinutes) {
        task.updateMinSessionMinutes(minSessionMinutes, CLOCK);
        return task;
    }

    private static Task named(String title, int importance, LocalDate deadline, Integer durationMinutes, int ageDays) {
        // Created at local noon ageDays before today, so the local-date age is exact.
        Clock createdAt = Clock.fixed(
                TODAY.minusDays(ageDays).atTime(12, 0).atZone(JERUSALEM).toInstant(), JERUSALEM);
        return Task.create(title, null, importance, durationMinutes, deadline, null, createdAt);
    }
}
