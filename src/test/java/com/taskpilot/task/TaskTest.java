package com.taskpilot.task;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    private static Clock clockOnToday() {
        return Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private static OffsetDateTime at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atOffset(ZoneOffset.UTC);
    }

    @Nested
    class Creation {

        @Test
        void becomesActiveWhenNoScheduleGiven() {
            Task task = Task.create("Buy plant", null, null, null, null, null, clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
            assertThat(task.getId()).isNotNull();
            assertThat(task.getCreatedAt()).isEqualTo(task.getUpdatedAt());
        }

        @Test
        void becomesScheduledWhenScheduledForIsInTheFuture() {
            OffsetDateTime tomorrow = at(TODAY.plusDays(1), 9);

            Task task = Task.create("Dentist", null, null, null, null, tomorrow, clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
            assertThat(task.getScheduledFor()).isEqualTo(tomorrow);
        }

        @Test
        void becomesActiveWhenScheduledForIsToday() {
            OffsetDateTime laterToday = at(TODAY, 18);

            Task task = Task.create("Dentist", null, null, null, null, laterToday, clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        }

        @Test
        void becomesActiveWhenScheduledForIsInThePastAndPreservesIt() {
            OffsetDateTime yesterday = at(TODAY.minusDays(1), 8);

            Task task = Task.create("Follow up", null, null, null, null, yesterday, clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
            assertThat(task.getScheduledFor()).isEqualTo(yesterday);
        }

        @Test
        void defaultsImportanceWhenNotProvided() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            assertThat(task.getImportance()).isEqualTo(Task.DEFAULT_IMPORTANCE);
        }

        @Test
        void rejectsBlankTitle() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Task.create("   ", null, null, null, null, null, clockOnToday()));
        }

        @Test
        void rejectsImportanceOutsideRange() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Task.create("Task", null, 6, null, null, null, clockOnToday()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Task.create("Task", null, 0, null, null, null, clockOnToday()));
        }

        @Test
        void rejectsNonPositiveEstimatedDuration() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Task.create("Task", null, null, 0, null, null, clockOnToday()));
        }

        @Test
        void allowsDeadlineInThePast() {
            LocalDate pastDeadline = TODAY.minusDays(5);

            Task task = Task.create("Overdue thing", null, null, null, pastDeadline, null, clockOnToday());

            assertThat(task.getDeadline()).isEqualTo(pastDeadline);
        }
    }

    @Nested
    class Completion {

        @Test
        void activeTaskCanComplete() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            task.complete(clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getCompletedAt()).isNotNull();
        }

        @Test
        void scheduledTaskCanCompleteDirectly() {
            OffsetDateTime tomorrow = at(TODAY.plusDays(1), 9);
            Task task = Task.create("Task", null, null, null, null, tomorrow, clockOnToday());
            assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);

            task.complete(clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void completingAnAlreadyCompletedTaskIsInvalid() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());
            task.complete(clockOnToday());

            assertThatThrownBy(() -> task.complete(clockOnToday()))
                    .isInstanceOf(TaskStateException.class);
        }
    }

    @Nested
    class Reopening {

        @Test
        void completedTaskCanReopenAndClearsCompletedAt() {
            OffsetDateTime scheduledFor = at(TODAY.minusDays(2), 8);
            Task task = Task.create("Task", null, null, null, null, scheduledFor, clockOnToday());
            task.complete(clockOnToday());

            task.reopen(clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
            assertThat(task.getCompletedAt()).isNull();
            assertThat(task.getScheduledFor()).isEqualTo(scheduledFor);
        }

        @Test
        void reopeningNonCompletedTaskIsInvalid() {
            Task active = Task.create("Task", null, null, null, null, null, clockOnToday());
            assertThatThrownBy(() -> active.reopen(clockOnToday())).isInstanceOf(TaskStateException.class);

            Task scheduled = Task.create("Task", null, null, null, null, at(TODAY.plusDays(1), 9), clockOnToday());
            assertThatThrownBy(() -> scheduled.reopen(clockOnToday())).isInstanceOf(TaskStateException.class);
        }
    }

    @Nested
    class Scheduling {

        @Test
        void activeTaskSchedulesToFutureDate() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            OffsetDateTime future = at(TODAY.plusDays(3), 10);
            task.schedule(future, clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
            assertThat(task.getScheduledFor()).isEqualTo(future);
        }

        @Test
        void scheduledTaskCanBeRescheduled() {
            Task task = Task.create("Task", null, null, null, null, at(TODAY.plusDays(1), 9), clockOnToday());

            OffsetDateTime laterFuture = at(TODAY.plusDays(5), 9);
            task.schedule(laterFuture, clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
            assertThat(task.getScheduledFor()).isEqualTo(laterFuture);
        }

        @Test
        void schedulingToTodayActivatesImmediately() {
            Task task = Task.create("Task", null, null, null, null, at(TODAY.plusDays(1), 9), clockOnToday());

            task.schedule(at(TODAY, 20), clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        }

        @Test
        void schedulingToPastDateIsRejected() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> task.schedule(at(TODAY.minusDays(1), 9), clockOnToday()));
        }

        @Test
        void schedulingWithoutADateIsRejected() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            assertThatIllegalArgumentException().isThrownBy(() -> task.schedule(null, clockOnToday()));
        }

        @Test
        void schedulingACompletedTaskDirectlyIsInvalid() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());
            task.complete(clockOnToday());

            assertThatThrownBy(() -> task.schedule(at(TODAY.plusDays(1), 9), clockOnToday()))
                    .isInstanceOf(TaskStateException.class);
        }
    }

    @Nested
    class Activation {

        @Test
        void scheduledTaskDueTodayActivates() {
            // Created a day before its due date so creation itself yields SCHEDULED,
            // then activated on the day it becomes due (§44 Task A scenario).
            OffsetDateTime dueToday = at(TODAY, 18);
            Clock yesterdayClock = Clock.fixed(TODAY.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
            Task task = Task.create("Task A", null, null, null, null, dueToday, yesterdayClock);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);

            task.activate(clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
            assertThat(task.getScheduledFor()).isEqualTo(dueToday);
        }

        @Test
        void scheduledTaskFromThePastActivates() {
            OffsetDateTime yesterday = at(TODAY.minusDays(1), 0);
            Task task = createScheduledTaskWithScheduledFor(yesterday);

            task.activate(clockOnToday());

            assertThat(task.getStatus()).isEqualTo(TaskStatus.ACTIVE);
        }

        @Test
        void scheduledTaskNotYetDueCannotActivate() {
            OffsetDateTime tomorrow = at(TODAY.plusDays(1), 0);
            Task task = Task.create("Task B", null, null, null, null, tomorrow, clockOnToday());
            assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);

            assertThatThrownBy(() -> task.activate(clockOnToday())).isInstanceOf(TaskStateException.class);
        }

        @Test
        void nonScheduledTaskCannotActivate() {
            Task active = Task.create("Task", null, null, null, null, null, clockOnToday());
            assertThatThrownBy(() -> active.activate(clockOnToday())).isInstanceOf(TaskStateException.class);

            Task completed = Task.create("Task", null, null, null, null, null, clockOnToday());
            completed.complete(clockOnToday());
            assertThatThrownBy(() -> completed.activate(clockOnToday())).isInstanceOf(TaskStateException.class);
        }

        private Task createScheduledTaskWithScheduledFor(OffsetDateTime scheduledFor) {
            // Build it as SCHEDULED via a clock in the past relative to scheduledFor, then
            // observe it from "today" to test activate() independently of creation-time status.
            Clock pastClock = Clock.fixed(
                    scheduledFor.minusDays(2).toInstant(), scheduledFor.getOffset());
            return Task.create("Task C", null, null, null, null, scheduledFor, pastClock);
        }
    }

    @Nested
    class NonLifecycleUpdates {

        @Test
        void renameUpdatesTitleAndTimestamp() {
            Task task = Task.create("Old title", null, null, null, null, null, clockOnToday());

            task.rename("New title", clockOnToday());

            assertThat(task.getTitle()).isEqualTo("New title");
        }

        @Test
        void renameRejectsBlankTitle() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            assertThatIllegalArgumentException().isThrownBy(() -> task.rename("  ", clockOnToday()));
        }

        @Test
        void updateImportanceRejectsOutOfRangeValue() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            assertThatIllegalArgumentException().isThrownBy(() -> task.updateImportance(9, clockOnToday()));
        }

        @Test
        void updateEstimatedDurationRejectsNonPositiveValue() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> task.updateEstimatedDurationMinutes(-5, clockOnToday()));
        }

        @Test
        void updateDeadlineAllowsPastDate() {
            Task task = Task.create("Task", null, null, null, null, null, clockOnToday());

            task.updateDeadline(TODAY.minusDays(10), clockOnToday());

            assertThat(task.getDeadline()).isEqualTo(TODAY.minusDays(10));
        }
    }
}
