import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import type { Task } from '@/api/types'
import { InlineAlert } from '@/components/states'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { describeActionError } from '@/lib/errorMessages'
import { useDeleteTask } from '@/queries/mutations'

/** Hard delete (§16) — there is no undo, so it always asks first. */
export function DeleteTaskDialog({
  task,
  open,
  onOpenChange,
  onDeleted,
}: {
  task: Task
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeleted?: () => void
}) {
  const remove = useDeleteTask(task.id)

  const confirm = () => {
    if (remove.isPending) return
    remove.mutate(undefined, {
      onSuccess: ({ alreadyGone }) => {
        onOpenChange(false)
        onDeleted?.()
        toast.success(alreadyGone ? 'That task was already deleted.' : `Deleted “${task.title}”`)
      },
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={(next) => !remove.isPending && onOpenChange(next)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete “{task.title}”?</AlertDialogTitle>
          <AlertDialogDescription>This permanently removes the task. It can't be undone.</AlertDialogDescription>
        </AlertDialogHeader>
        {remove.isError && <InlineAlert title="Couldn't delete">{describeActionError(remove.error)}</InlineAlert>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={remove.isPending}>Cancel</AlertDialogCancel>
          {/* A plain button: AlertDialogAction would close the dialog before the server answers. */}
          <Button variant="destructive" onClick={confirm} disabled={remove.isPending} aria-busy={remove.isPending}>
            {remove.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
            Delete
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
