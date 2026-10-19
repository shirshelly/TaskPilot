import { useEffect, useState, type FormEvent } from 'react'
import { CalendarClock, Loader2, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import type { Task } from '@/api/types'
import { useAppTime } from '@/app/appTime'
import { InlineAlert } from '@/components/states'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { actionsFor } from '@/lib/actions'
import { describeActionError, isApiErrorCode } from '@/lib/errorMessages'
import { formatCompleted, formatSchedule } from '@/lib/format'
import {
  buildPatch,
  draftFromTask,
  FIELD_LABELS,
  rebaseDraft,
  validateDraft,
  type EditableField,
  type TaskDraft,
} from '@/lib/patch'
import { useIsTaskBusy, useReopenTask, useUpdateTask } from '@/queries/mutations'
import { useTask } from '@/queries/queries'
import { ConflictReview } from './ConflictReview'
import { DeleteTaskDialog } from './DeleteTaskDialog'
import { DescriptionField, DueByField, DurationField, ImportanceField, MinSessionField, TitleField } from './fields'
import { ScheduleDialog } from './ScheduleDialog'
import { SessionLongerWarning } from './SessionLongerWarning'

const STATUS_LABELS: Record<Task['status'], string> = { ACTIVE: 'Active', SCHEDULED: 'Scheduled', COMPLETED: 'Completed' }

/**
 * Edits the non-lifecycle fields of one task with a version-aware PATCH.
 *
 * `base` is the server version the edit started from; `draft` is the form. Saving sends only the
 * diff between them, with If-Match: base.version. If the server has moved on — detected on save
 * (412/409) or by a background refetch — the draft is kept and the user decides: discard it, or
 * save their own changes over the latest version (only the fields they changed; fields both
 * sides changed need explicit confirmation). Nothing is retried, merged or overwritten
 * automatically.
 */
export function EditTaskSheet({ task, open, onOpenChange }: { task: Task; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { zone, today } = useAppTime()
  const detail = useTask(task)
  const update = useUpdateTask(task.id)
  const reopen = useReopenTask(task.id)
  const busy = useIsTaskBusy(task.id)

  const [base, setBase] = useState<Task>(task)
  const [draft, setDraft] = useState<TaskDraft>(() => draftFromTask(task))
  const [showErrors, setShowErrors] = useState(false)
  const [reviewing, setReviewing] = useState(false)
  const [overlapsToConfirm, setOverlapsToConfirm] = useState<EditableField[] | null>(null)
  const [confirmDiscard, setConfirmDiscard] = useState(false)
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const patch = buildPatch(base, draft)
  const dirty = Object.keys(patch).length > 0
  const errors = validateDraft(draft)
  const latest = detail.data
  const deleted = isApiErrorCode(detail.error, 'TASK_NOT_FOUND') || isApiErrorCode(update.error, 'TASK_NOT_FOUND')

  // The server is ahead of the version this edit is based on. While one of our own mutations on
  // this task is in flight (or just succeeded and the sheet is closing), the newer version is ours.
  const behind = latest.version > base.version && !busy && !update.isSuccess
  const conflict = behind && dirty ? latest : null

  // Nothing typed yet: simply follow the server, there is nothing to lose.
  useEffect(() => {
    if (behind && !dirty) {
      setBase(latest)
      setDraft(draftFromTask(latest))
    }
  }, [behind, dirty, latest])

  const set = (changes: Partial<TaskDraft>) => {
    setNotice(null)
    setDraft((current) => ({ ...current, ...changes }))
  }

  const save = (from: Task, body: typeof patch) => {
    update.mutate(
      { patch: body, expectedVersion: from.version },
      {
        onSuccess: (saved) => {
          onOpenChange(false)
          toast.success('Changes saved', { description: saved.title })
        },
        onError: (error) => {
          // 412 (stale If-Match) or 409 (lost a race at commit): fetch the latest to compare.
          if (isApiErrorCode(error, 'VERSION_CONFLICT')) void detail.refetch()
        },
      },
    )
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (update.isPending || !dirty || deleted || conflict) return
    if (Object.keys(errors).length > 0) {
      setShowErrors(true)
      return
    }
    save(base, patch)
  }

  const discardMine = () => {
    setBase(latest)
    setDraft(draftFromTask(latest))
    setReviewing(false)
    update.reset()
  }

  const saveMineOverLatest = (confirmed: boolean) => {
    const rebase = rebaseDraft(base, draft, latest)
    if (rebase.overlaps.length > 0 && !confirmed) {
      setOverlapsToConfirm(rebase.overlaps)
      return
    }
    setOverlapsToConfirm(null)
    setReviewing(false)
    setBase(latest)
    setDraft(rebase.draft)
    update.reset()
    const body = buildPatch(latest, rebase.draft)
    if (Object.keys(body).length === 0) {
      setNotice('Your changes are already on the server — nothing else to save.')
      return
    }
    save(latest, body)
  }

  const requestClose = (next: boolean) => {
    if (next || update.isPending) return
    if (dirty && !deleted) setConfirmDiscard(true)
    else onOpenChange(false)
  }

  // Our own schedule/reopen moved the version: rebase onto it, keeping the form's edits
  // (those actions don't touch editable fields, so nothing can overlap).
  const adoptOwnChange = (changed: Task) => {
    setDraft((current) => rebaseDraft(base, current, changed).draft)
    setBase(changed)
  }

  const shown = deleted ? base : latest
  const actions = actionsFor(shown)
  const status =
    shown.status === 'COMPLETED' && shown.completedAt
      ? formatCompleted(shown.completedAt, zone, today)
      : STATUS_LABELS[shown.status]

  return (
    <>
      <Sheet open={open} onOpenChange={requestClose}>
        <SheetContent className="gap-0 p-0 data-[side=right]:w-full data-[side=right]:sm:max-w-md">
          <form onSubmit={submit} noValidate className="flex h-full flex-col">
            <SheetHeader className="border-b border-border px-5 py-4">
              <SheetTitle className="font-serif text-2xl font-normal">Edit task</SheetTitle>
              <SheetDescription className="text-xs">
                {status}
                {shown.isOverdue && shown.status !== 'COMPLETED' && <span className="text-overdue"> · Overdue</span>}
              </SheetDescription>
            </SheetHeader>

            <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
              {deleted && (
                <InlineAlert title="This task was deleted elsewhere.">
                  Your edits can't be saved. You can still copy anything you need before closing.
                </InlineAlert>
              )}

              {!deleted && conflict && (
                <InlineAlert
                  tone="warning"
                  title="This task changed since you started editing."
                  action={
                    reviewing ? (
                      <>
                        <Button type="button" size="sm" onClick={() => saveMineOverLatest(false)} disabled={update.isPending}>
                          Save mine over latest
                        </Button>
                        <Button type="button" size="sm" variant="outline" onClick={discardMine} disabled={update.isPending}>
                          Discard my changes
                        </Button>
                      </>
                    ) : (
                      <Button type="button" size="sm" variant="outline" onClick={() => setReviewing(true)}>
                        Review changes
                      </Button>
                    )
                  }
                >
                  Your edits are kept and nothing has been saved.
                  {reviewing && (
                    <div className="mt-3">
                      <ConflictReview base={base} latest={conflict} draft={draft} />
                    </div>
                  )}
                </InlineAlert>
              )}

              {!deleted && update.isError && !isApiErrorCode(update.error, 'VERSION_CONFLICT') && (
                <InlineAlert title="Couldn't save your changes">{describeActionError(update.error)}</InlineAlert>
              )}
              {!deleted && update.isError && isApiErrorCode(update.error, 'VERSION_CONFLICT') && !conflict && (
                <InlineAlert tone="warning" title="This task changed elsewhere.">
                  Checking the latest version…
                </InlineAlert>
              )}
              {notice && <InlineAlert tone="info" title={notice} />}

              <fieldset disabled={deleted} className="space-y-5">
                <TitleField value={draft.title} onChange={(title) => set({ title })} error={showErrors ? errors.title : undefined} />
                <DescriptionField value={draft.description} onChange={(description) => set({ description })} />
                <ImportanceField value={draft.importance} onChange={(importance) => set({ importance })} />
                <div className="grid gap-5 sm:grid-cols-2">
                  <DurationField
                    value={draft.duration}
                    onChange={(duration) => set({ duration })}
                    error={showErrors ? errors.duration : undefined}
                  />
                  <MinSessionField
                    value={draft.minSession}
                    onChange={(minSession) => set({ minSession })}
                    error={showErrors ? errors.minSession : undefined}
                  />
                </div>
                <SessionLongerWarning draft={draft} />
                <DueByField value={draft.deadline} onChange={(deadline) => set({ deadline })} />
              </fieldset>

              <section aria-labelledby="start-on-heading" className="rounded-lg border border-border px-3.5 py-3">
                <h3 id="start-on-heading" className="flex items-center gap-1.5 text-[13px] font-medium text-foreground/80">
                  <CalendarClock className="size-3.5" aria-hidden="true" /> Start on
                </h3>
                <div className="mt-1 flex items-center justify-between gap-3 text-sm">
                  <span className={shown.scheduledFor ? '' : 'text-muted-foreground'}>
                    {shown.scheduledFor ? formatSchedule(shown.scheduledFor, zone, today) : 'Not scheduled'}
                  </span>
                  {!deleted &&
                    (actions.canSchedule ? (
                      <Button type="button" size="sm" variant="outline" onClick={() => setScheduleOpen(true)} disabled={busy}>
                        {actions.scheduleLabel}…
                      </Button>
                    ) : (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={busy}
                        onClick={() =>
                          reopen.mutate(undefined, {
                            onSuccess: (reopened) => {
                              adoptOwnChange(reopened)
                              toast.success('Reopened', { description: reopened.title })
                            },
                            onError: (error) => toast.error(describeActionError(error)),
                          })
                        }
                      >
                        {reopen.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
                        Reopen
                      </Button>
                    ))}
                </div>
                <p className="mt-1.5 text-xs text-muted-foreground">
                  {actions.canSchedule
                    ? 'Scheduling is its own action, so it happens right away.'
                    : 'Completed tasks can’t be scheduled. Reopen it first.'}
                </p>
              </section>
            </div>

            <div className="flex items-center gap-2 border-t border-border bg-muted/40 px-5 py-3">
              {!deleted && (
                <Button
                  type="button"
                  variant="ghost"
                  className="mr-auto text-destructive hover:bg-destructive/10 hover:text-destructive"
                  onClick={() => setDeleteOpen(true)}
                  disabled={busy}
                >
                  <Trash2 aria-hidden="true" />
                  Delete
                </Button>
              )}
              <Button type="button" variant="ghost" className={deleted ? 'ml-auto' : ''} onClick={() => requestClose(false)} disabled={update.isPending}>
                {deleted ? 'Close' : 'Cancel'}
              </Button>
              {!deleted && (
                <Button type="submit" disabled={!dirty || update.isPending || conflict !== null} aria-busy={update.isPending}>
                  {update.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
                  Save
                </Button>
              )}
            </div>
          </form>
        </SheetContent>
      </Sheet>

      <AlertDialog open={confirmDiscard} onOpenChange={setConfirmDiscard}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Discard your changes?</AlertDialogTitle>
            <AlertDialogDescription>Your edits to this task haven't been saved.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep editing</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => onOpenChange(false)}>
              Discard
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={overlapsToConfirm !== null} onOpenChange={(next) => !next && setOverlapsToConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Replace changes made elsewhere?</AlertDialogTitle>
            <AlertDialogDescription>
              {(overlapsToConfirm ?? []).map((field) => FIELD_LABELS[field]).join(', ')}{' '}
              {overlapsToConfirm?.length === 1 ? 'was' : 'were'} also changed elsewhere. Saving replaces{' '}
              {overlapsToConfirm?.length === 1 ? 'that value' : 'those values'} with yours. Other changes made elsewhere are kept.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Go back</AlertDialogCancel>
            <AlertDialogAction onClick={() => saveMineOverLatest(true)}>Replace with mine</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <ScheduleDialog task={shown} open={scheduleOpen} onOpenChange={setScheduleOpen} onScheduled={adoptOwnChange} />
      <DeleteTaskDialog task={shown} open={deleteOpen} onOpenChange={setDeleteOpen} onDeleted={() => onOpenChange(false)} />
    </>
  )
}
