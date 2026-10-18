package com.taskpilot.agenda;

import com.taskpilot.task.Task;
import com.taskpilot.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure ordering rules (§30), no Spring or database. Membership is not this class's concern:
 * some inputs below would never be returned by the Agenda query, which is fine for a sort.
 */
class AgendaOrderingTest {

    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");
    // Wednesday 21 Oct 2026, 12:00 in Jerusalem.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), JERUSALEM);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    @Test
    void ordersTheRepresentativeCasesRegardlessOfInputOrder() {
        Task overdueDeadline = task("Overdue deadline", -2, null);
        Task overdueSchedule = task("Overdue schedule, no deadline", null, at(-5, 9));
        Task rolledOver = task("Scheduled yesterday, still active, due in 2 days", 2, at(-1, 10));
        Task dueToday = task("Deadline today", 0, null);
        Task dueTomorrow = task("Scheduled today, due tomorrow", 1, startOf(0));
        Task plannedMorning = task("Scheduled today 09:00, no deadline", null, at(0, 9));
        Task plannedWithDistantDeadline = task("Scheduled today 16:00, due in a month", 30, at(0, 16));
        List<Task> expected = List.of(overdueDeadline, overdueSchedule, rolledOver, dueToday, dueTomorrow,
                plannedMorning, plannedWithDistantDeadline);

        assertThat(AgendaOrdering.sort(reversed(expected), CLOCK)).containsExactlyElementsOf(expected);
        assertThat(AgendaOrdering.sort(shuffled(expected), CLOCK)).containsExactlyElementsOf(expected);
    }

    @Test
    void missedDeadlinesComeBeforeOlderMissedSchedules() {
        Task missedSchedule = task("Missed schedule a week ago", null, at(-7, 9));
        Task missedBoth = task("Missed deadline yesterday and schedule six days ago", -1, at(-6, 9));

        assertThat(AgendaOrdering.sort(List.of(missedSchedule, missedBoth), CLOCK))
                .containsExactly(missedBoth, missedSchedule);
    }

    @Test
    void dueSoonWindowEndsThreeDaysAhead() {
        Task dueInThreeDays = task("Due in 3 days, scheduled 18:00", 3, at(0, 18));
        Task dueInFourDays = task("Due in 4 days, scheduled 08:00", 4, at(0, 8));
        Task noDeadline = task("No deadline, scheduled 09:00", null, at(0, 9));

        // Past the window, the deadline stops outranking time of day.
        assertThat(AgendaOrdering.sort(List.of(noDeadline, dueInFourDays, dueInThreeDays), CLOCK))
                .containsExactly(dueInThreeDays, dueInFourDays, noDeadline);
    }

    @Test
    void tasksDueTodayFollowTheirScheduledTimeWithUnscheduledLast() {
        Task afternoon = task("Due today, scheduled 15:00", 0, at(0, 15));
        Task unscheduled = task("Due today, unscheduled", 0, null);
        Task morning = task("Due today, scheduled 10:00", 0, at(0, 10));

        assertThat(AgendaOrdering.sort(List.of(afternoon, unscheduled, morning), CLOCK))
                .containsExactly(morning, afternoon, unscheduled);
    }

    @Test
    void scheduledTaskAwaitingActivationIsNotOverdueAndLeadsThePlannedTier() {
        Task awaitingWorker = Task.create("Still SCHEDULED for yesterday", null, null, null, null, at(-1, 9),
                daysAgo(3));
        assertThat(awaitingWorker.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        Task plannedToday = task("Scheduled today 08:00", null, at(0, 8));
        Task dueTomorrow = task("Due tomorrow", 1, startOf(0));

        assertThat(AgendaOrdering.sort(List.of(plannedToday, awaitingWorker, dueTomorrow), CLOCK))
                .containsExactly(dueTomorrow, awaitingWorker, plannedToday);
    }

    @Test
    void equalDatesFallBackToCreatedAtThenId() {
        Task older = Task.create("Older", null, null, null, null, startOf(0), daysAgo(2));
        Task twinA = Task.create("Twin A", null, null, null, null, startOf(0), daysAgo(1));
        Task twinB = Task.create("Twin B", null, null, null, null, startOf(0), daysAgo(1));
        List<Task> twins = new ArrayList<>(List.of(twinA, twinB));
        twins.sort((a, b) -> a.getId().compareTo(b.getId()));

        assertThat(AgendaOrdering.sort(List.of(twinB, older, twinA), CLOCK))
                .containsExactly(older, twins.get(0), twins.get(1));
    }

    private static Task task(String title, Integer deadlineInDays, OffsetDateTime scheduledFor) {
        LocalDate deadline = deadlineInDays == null ? null : TODAY.plusDays(deadlineInDays);
        Task task = Task.create(title, null, null, null, deadline, scheduledFor, CLOCK);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        return task;
    }

    private static OffsetDateTime at(int days, int hour) {
        return TODAY.plusDays(days).atTime(hour, 0).atZone(JERUSALEM).toOffsetDateTime();
    }

    private static OffsetDateTime startOf(int days) {
        return TODAY.plusDays(days).atStartOfDay(JERUSALEM).toOffsetDateTime();
    }

    private static Clock daysAgo(int days) {
        return Clock.offset(CLOCK, Duration.ofDays(-days));
    }

    private static List<Task> reversed(List<Task> tasks) {
        List<Task> copy = new ArrayList<>(tasks);
        Collections.reverse(copy);
        return copy;
    }

    private static List<Task> shuffled(List<Task> tasks) {
        List<Task> copy = new ArrayList<>(tasks);
        Collections.shuffle(copy, new Random(42));
        return copy;
    }
}
