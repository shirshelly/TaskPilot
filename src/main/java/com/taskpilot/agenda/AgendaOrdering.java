package com.taskpilot.agenda;

import com.taskpilot.task.Task;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Agenda order (§30): one list, most time-critical first. Only dates and the overdue rules decide
 * the order -- never importance, duration, or age -- so this stays a view of "what is on today's
 * plate" rather than a second recommendation ranking (§31).
 *
 * Each task falls into the first matching tier and is then ordered by the date that put it there:
 *
 * <pre>
 * DEADLINE_OVERDUE  deadline < today                     by deadline
 * SCHEDULE_OVERDUE  ACTIVE, schedule date < today        by schedule date
 * DUE_TODAY         deadline == today                    (by time of day below)
 * DUE_SOON          deadline in today+1 .. today+3       by deadline
 * PLANNED           everything else (scheduled <= today) by schedule date
 * </pre>
 *
 * Tie-breakers: scheduledFor on the timeline (time of day), deadline, createdAt, id. Sorted in
 * memory rather than in SQL so the overdue rules stay defined once, in {@link Task}.
 */
final class AgendaOrdering {

    static final int DUE_SOON_DAYS = 3;

    enum Tier { DEADLINE_OVERDUE, SCHEDULE_OVERDUE, DUE_TODAY, DUE_SOON, PLANNED }

    private record Keyed(Task task, Tier tier, LocalDate reasonDate) {
    }

    private static final Comparator<Keyed> ORDER = Comparator
            .comparing(Keyed::tier)
            .thenComparing(Keyed::reasonDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing((Keyed keyed) -> keyed.task().getScheduledFor(),
                    Comparator.nullsLast(OffsetDateTime.timeLineOrder()))
            .thenComparing((Keyed keyed) -> keyed.task().getDeadline(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing((Keyed keyed) -> keyed.task().getCreatedAt())
            .thenComparing((Keyed keyed) -> keyed.task().getId());

    private AgendaOrdering() {
    }

    static List<Task> sort(List<Task> tasks, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        return tasks.stream()
                .map(task -> key(task, today, clock))
                .sorted(ORDER)
                .map(Keyed::task)
                .toList();
    }

    private static Keyed key(Task task, LocalDate today, Clock clock) {
        LocalDate deadline = task.getDeadline();
        if (task.isDeadlineOverdue(clock)) {
            return new Keyed(task, Tier.DEADLINE_OVERDUE, deadline);
        }
        if (task.isScheduleOverdue(clock)) {
            return new Keyed(task, Tier.SCHEDULE_OVERDUE, scheduledDate(task, clock));
        }
        if (today.equals(deadline)) {
            return new Keyed(task, Tier.DUE_TODAY, deadline);
        }
        if (deadline != null && !deadline.isAfter(today.plusDays(DUE_SOON_DAYS))) {
            return new Keyed(task, Tier.DUE_SOON, deadline);
        }
        return new Keyed(task, Tier.PLANNED, scheduledDate(task, clock));
    }

    private static LocalDate scheduledDate(Task task, Clock clock) {
        OffsetDateTime scheduledFor = task.getScheduledFor();
        return scheduledFor == null ? null : scheduledFor.atZoneSameInstant(clock.getZone()).toLocalDate();
    }
}
