import { useState, type FormEvent } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import type { Task } from '@/api/types'
import { useAppTime } from '@/app/appTime'
import { InlineAlert } from '@/components/states'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { ApiError } from '@/api/errors'
import { actionsFor } from '@/lib/actions'
import { describeActionError } from '@/lib/errorMessages'
import { formatSchedule, formatShortDate, inSentence } from '@/lib/format'
import { scheduledForFromInput } from '@/lib/patch'
import { addDays, zonedParts, type IsoDate } from '@/lib/time'
import { useScheduleTask } from '@/queries/mutations'
import { StartAfterDueWarning } from './StartAfterDueWarning'

type DateChoice = 'today' | 'tomorrow' | 'monday' | 'date'

function nextMonday(today: IsoDate): IsoDate {
  const weekday = new Date(`${today}T00:00:00Z`).getUTCDay() // 0 = Sunday
  return addDays(today, ((8 - weekday) % 7) || 7)
}

/**
 * Schedule / Reschedule via POST /tasks/{id}/schedule — never through PATCH. The resulting
 * status comes from the response: scheduling for today keeps the task active.
 */
export function ScheduleDialog({
  task,
  open,
  onOpenChange,
  onScheduled,
}: {
  task: Task
  open: boolean
  onOpenChange: (open: boolean) => void
  onScheduled?: (task: Task) => void
}) {
  const { zone, today } = useAppTime()
  const schedule = useScheduleTask(task.id)
  const { scheduleLabel } = actionsFor(task)
  const current = task.scheduledFor ? zonedParts(task.scheduledFor, zone) : null

  const dates: Record<Exclude<DateChoice, 'date'>, IsoDate> = {
    today,
    tomorrow: addDays(today, 1),
    monday: nextMonday(today),
  }
  const [pick, setPick] = useState<DateChoice>('tomorrow')
  const [customDate, setCustomDate] = useState<string>(current && current.date >= today ? current.date : '')
  const [time, setTime] = useState('')
  const date = pick === 'date' ? customDate : dates[pick]

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (schedule.isPending || !date) return
    const scheduledFor = scheduledForFromInput({ date, time }, zone)!
    schedule.mutate(scheduledFor, {
      onSuccess: (updated) => {
        onScheduled?.(updated)
        onOpenChange(false)
        toast.success(
          updated.status === 'SCHEDULED'
            ? `Scheduled for ${inSentence(formatSchedule(updated.scheduledFor!, zone, today))}`
            : 'Scheduled for today — it stays active',
          { description: updated.title },
        )
      },
      onError: (error) => {
        // State changed elsewhere (e.g. completed): nothing to fix in this dialog.
        if (error instanceof ApiError && error.code !== 'VALIDATION_FAILED' && error.code !== 'NETWORK_ERROR') {
          onOpenChange(false)
          toast.error(describeActionError(error))
        }
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !schedule.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit} noValidate className="grid gap-5">
          <DialogHeader>
            <DialogTitle>{scheduleLabel}</DialogTitle>
            <DialogDescription>
              <span className="font-medium text-foreground">{task.title}</span>
              {task.scheduledFor && <> · now {inSentence(formatSchedule(task.scheduledFor, zone, today))}</>}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-3">
            <ToggleGroup
              type="single"
              variant="outline"
              spacing={1.5}
              value={pick}
              onValueChange={(value) => value && setPick(value as DateChoice)}
              aria-label="Start on"
              className="flex-wrap"
            >
              {(
                [
                  ['today', 'Today'],
                  ['tomorrow', 'Tomorrow'],
                  // Skipped when next Monday *is* tomorrow, to avoid two choices for one date.
                  ...(dates.monday === dates.tomorrow ? [] : [['monday', formatShortDate(dates.monday)]]),
                  ['date', 'Pick a date'],
                ] as [DateChoice, string][]
              ).map(([value, label]) => (
                <ToggleGroupItem
                  key={value}
                  value={value}
                  className="h-8 rounded-full px-3 text-[13px] data-[state=on]:border-foreground data-[state=on]:bg-foreground data-[state=on]:text-background"
                >
                  {label}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>

            <div className="flex gap-2">
              {pick === 'date' && (
                <div className="flex-1 space-y-1.5">
                  <Label htmlFor="schedule-date" className="text-[13px] text-foreground/80">
                    Date
                  </Label>
                  <Input
                    id="schedule-date"
                    type="date"
                    min={today}
                    value={customDate}
                    onChange={(event) => setCustomDate(event.target.value)}
                  />
                </div>
              )}
              <div className="space-y-1.5">
                <Label htmlFor="schedule-time" className="text-[13px] text-foreground/80">
                  Time <span className="font-normal text-muted-foreground">(optional)</span>
                </Label>
                <Input id="schedule-time" type="time" value={time} onChange={(event) => setTime(event.target.value)} className="w-32" />
              </div>
            </div>

            <p className="text-xs text-muted-foreground">
              {date === today
                ? 'It stays active and shows up in Today.'
                : 'Hidden until then, and shows up in Today on that day.'}
            </p>
            {task.deadline && <StartAfterDueWarning startDate={date} deadline={task.deadline} />}
            {schedule.isError && <InlineAlert title="Couldn't schedule">{describeActionError(schedule.error)}</InlineAlert>}
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={schedule.isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={schedule.isPending || !date} aria-busy={schedule.isPending}>
              {schedule.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
              {scheduleLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
