import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import type { Task } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useCompleteTask } from '@/queries/mutations'
import { completionFeedback } from './useCompletionFlow'

/** "What did you finish?" for tasks worked on in sessions. */
export function SessionCompletionDialog({
  task,
  open,
  onOpenChange,
  onCloseAutoFocus,
}: {
  task: Task
  open: boolean
  onOpenChange: (open: boolean) => void
  onCloseAutoFocus?: (event: Event) => void
}) {
  const complete = useCompleteTask(task.id, completionFeedback)

  const doneForNow = () => {
    onOpenChange(false)
    toast('Task stays active. Pick it up whenever you’re ready.', { description: task.title })
  }

  const finishedEntirely = () => {
    if (complete.isPending) return
    // Server-confirmed: the dialog stays open, locked, until the backend answers.
    complete.mutate(undefined, { onSettled: () => onOpenChange(false) })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !complete.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md" aria-busy={complete.isPending} onCloseAutoFocus={onCloseAutoFocus}>
        <DialogHeader>
          <DialogTitle>What did you finish?</DialogTitle>
          <DialogDescription className="break-words">
            <span className="font-medium text-foreground">{task.title}</span> is worked on in sessions.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-2">
          <ChoiceButton
            title="I’m done for now"
            detail="The task stays active. Nothing is recorded."
            onClick={doneForNow}
            disabled={complete.isPending}
            variant="outline"
          />
          <ChoiceButton
            title="I finished the entire task"
            detail="Mark it as completed."
            onClick={finishedEntirely}
            disabled={complete.isPending}
            pending={complete.isPending}
          />
        </div>
      </DialogContent>
    </Dialog>
  )
}

function ChoiceButton({
  title,
  detail,
  onClick,
  disabled,
  pending,
  variant = 'default',
}: {
  title: string
  detail: string
  onClick: () => void
  disabled: boolean
  pending?: boolean
  variant?: 'default' | 'outline'
}) {
  return (
    <Button
      type="button"
      variant={variant}
      onClick={onClick}
      disabled={disabled}
      aria-busy={pending}
      className="h-auto flex-col items-start gap-0.5 px-4 py-3 text-left whitespace-normal"
    >
      <span className="flex items-center gap-2 text-sm font-medium">
        {pending && <Loader2 className="animate-spin" aria-hidden="true" />}
        {title}
      </span>
      <span className="text-xs font-normal opacity-80">{detail}</span>
    </Button>
  )
}
