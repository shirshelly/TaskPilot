import { useState, type FormEvent } from 'react'
import { ChevronDown, Loader2 } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useAppTime } from '@/app/appTime'
import { InlineAlert } from '@/components/states'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { describeActionError } from '@/lib/errorMessages'
import { formatSchedule, inSentence } from '@/lib/format'
import { buildCreateRequest, EMPTY_DRAFT, validateDraft, type StartOnInput, type TaskDraft } from '@/lib/patch'
import { useCreateTask } from '@/queries/mutations'
import {
  DescriptionField,
  DueByField,
  DurationField,
  ImportanceField,
  MinSessionField,
  StartOnField,
  TitleField,
} from './fields'
import { SessionLongerWarning } from './SessionLongerWarning'
import { StartAfterDueWarning } from './StartAfterDueWarning'

/** Title + Enter is enough; everything else lives behind "More options". */
export function QuickCreateDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { zone, today } = useAppTime()
  const navigate = useNavigate()
  const create = useCreateTask()
  const [draft, setDraft] = useState<TaskDraft>(EMPTY_DRAFT)
  const [startOn, setStartOn] = useState<StartOnInput>({ date: '', time: '' })
  const [moreOpen, setMoreOpen] = useState(false)
  const [showErrors, setShowErrors] = useState(false)

  const errors = validateDraft(draft)
  const update = (changes: Partial<TaskDraft>) => setDraft((current) => ({ ...current, ...changes }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (create.isPending) return
    if (Object.keys(errors).length > 0) {
      setShowErrors(true)
      if (errors.duration || errors.minSession) setMoreOpen(true)
      return
    }
    create.mutate(buildCreateRequest(draft, startOn, zone), {
      onSuccess: (task) => {
        onOpenChange(false)
        // The outcome comes from the server: a future Start on makes it SCHEDULED, otherwise ACTIVE.
        if (task.status === 'SCHEDULED' && task.scheduledFor) {
          toast.success(`Scheduled “${task.title}”`, {
            description: `Starts ${inSentence(formatSchedule(task.scheduledFor, zone, today))} · hidden until then`,
            action: { label: 'View', onClick: () => navigate('/tasks?status=scheduled') },
          })
        } else {
          toast.success(`Added “${task.title}”`, {
            action: { label: 'View', onClick: () => navigate('/tasks') },
          })
        }
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !create.isPending && onOpenChange(next)}>
      <DialogContent className="top-[12vh] translate-y-0 gap-0 p-0 sm:max-w-lg">
        <form onSubmit={submit} noValidate>
          <DialogHeader className="px-5 pt-5">
            <DialogTitle className="font-serif text-2xl font-normal">New task</DialogTitle>
            <DialogDescription className="sr-only">Add a task. Only the title is required.</DialogDescription>
          </DialogHeader>

          <div className="max-h-[60vh] space-y-4 overflow-y-auto px-5 pt-4 pb-5">
            <TitleField
              value={draft.title}
              onChange={(title) => update({ title })}
              error={showErrors ? errors.title : undefined}
              autoFocus
              inputClassName="h-10 text-base"
            />

            <Collapsible open={moreOpen} onOpenChange={setMoreOpen}>
              <CollapsibleTrigger asChild>
                <button
                  type="button"
                  className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
                >
                  More options
                  <ChevronDown className={`size-4 transition-transform ${moreOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
                </button>
              </CollapsibleTrigger>
              <CollapsibleContent className="space-y-4 pt-4">
                <DescriptionField value={draft.description} onChange={(description) => update({ description })} />
                <ImportanceField value={draft.importance} onChange={(importance) => update({ importance })} />
                <div className="grid gap-4 sm:grid-cols-2">
                  <DurationField
                    value={draft.duration}
                    onChange={(duration) => update({ duration })}
                    error={showErrors ? errors.duration : undefined}
                  />
                  <MinSessionField
                    value={draft.minSession}
                    onChange={(minSession) => update({ minSession })}
                    error={showErrors ? errors.minSession : undefined}
                  />
                </div>
                <SessionLongerWarning draft={draft} />
                <StartOnField value={startOn} onChange={setStartOn} minDate={today} />
                <DueByField value={draft.deadline} onChange={(deadline) => update({ deadline })} />
                <StartAfterDueWarning startDate={startOn.date} deadline={draft.deadline} />
              </CollapsibleContent>
            </Collapsible>

            {create.isError && <InlineAlert title="Couldn't add the task">{describeActionError(create.error)}</InlineAlert>}
          </div>

          <div className="flex justify-end gap-2 border-t border-border bg-muted/40 px-5 py-3 rounded-b-xl">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={create.isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending} aria-busy={create.isPending}>
              {create.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
              Add task
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
