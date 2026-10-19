import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import type { Task } from '@/api/types'
import { SessionCompletionDialog } from '@/task/SessionCompletionDialog'
import { DeleteTaskDialog } from '@/task/DeleteTaskDialog'
import { EditTaskSheet } from '@/task/EditTaskSheet'
import { QuickCreateDialog } from '@/task/QuickCreateDialog'
import { ScheduleDialog } from '@/task/ScheduleDialog'

interface TaskOverlays {
  openCreate: () => void
  openEdit: (task: Task) => void
  openSchedule: (task: Task) => void
  openDelete: (task: Task) => void
  /** Only via useCompletionFlow: the "done for now / finished" choice for session-based tasks. */
  openCompletionChoice: (task: Task) => void
}

const TaskOverlaysContext = createContext<TaskOverlays | null>(null)

/**
 * Remembers the last task even after closing, so the closing animation still has content;
 * `session` changes on every open so each dialog starts from fresh local state.
 */
interface Overlay {
  task: Task | null
  open: boolean
  session: number
  /** Where keyboard focus goes back to when the dialog closes. */
  returnFocus: (event: Event) => void
}

const closed: Overlay = { task: null, open: false, session: 0, returnFocus: () => {} }

/**
 * These dialogs open programmatically (from rows and menus), so Radix has no trigger to return
 * focus to and it would fall to <body>. Remember what was focused when the dialog opened — for a
 * menu item, the menu's ⋯ button — and go back there; if it has disappeared (e.g. the task was
 * completed or deleted), fall back to the main region.
 */
function focusRestorer(): (event: Event) => void {
  let origin = document.activeElement instanceof HTMLElement ? document.activeElement : null
  const menu = origin?.closest('[role="menu"]')
  if (menu?.id) origin = document.querySelector<HTMLElement>(`[aria-controls="${menu.id}"]`)
  return (event) => {
    event.preventDefault()
    const target = origin?.isConnected ? origin : document.getElementById('main')
    target?.focus()
  }
}

function useOverlay() {
  const [state, setState] = useState<Overlay>(closed)
  const open = useCallback((task: Task | null) => {
    const returnFocus = focusRestorer()
    setState((s) => ({ task, open: true, session: s.session + 1, returnFocus }))
  }, [])
  const setOpen = useCallback((next: boolean) => setState((s) => ({ ...s, open: next })), [])
  return [state, open, setOpen] as const
}

/** Hosts the single instance of each task dialog; rows and pages open them through context. */
export function TaskOverlaysProvider({ children }: { children: ReactNode }) {
  const [create, openCreate, setCreateOpen] = useOverlay()
  const [edit, openEdit, setEditOpen] = useOverlay()
  const [schedule, openSchedule, setScheduleOpen] = useOverlay()
  const [remove, openDelete, setDeleteOpen] = useOverlay()
  const [completion, openCompletionChoice, setCompletionOpen] = useOverlay()

  const value = useMemo<TaskOverlays>(
    () => ({
      openCreate: () => openCreate(null),
      openEdit,
      openSchedule,
      openDelete,
      openCompletionChoice,
    }),
    [openCreate, openEdit, openSchedule, openDelete, openCompletionChoice],
  )

  return (
    <TaskOverlaysContext.Provider value={value}>
      {children}
      <QuickCreateDialog
        key={`create-${create.session}`}
        open={create.open}
        onOpenChange={setCreateOpen}
        onCloseAutoFocus={create.returnFocus}
      />
      {edit.task && (
        <EditTaskSheet
          key={`edit-${edit.session}`}
          task={edit.task}
          open={edit.open}
          onOpenChange={setEditOpen}
          onCloseAutoFocus={edit.returnFocus}
        />
      )}
      {schedule.task && (
        <ScheduleDialog
          key={`schedule-${schedule.session}`}
          task={schedule.task}
          open={schedule.open}
          onOpenChange={setScheduleOpen}
          onCloseAutoFocus={schedule.returnFocus}
        />
      )}
      {completion.task && (
        <SessionCompletionDialog
          key={`complete-${completion.session}`}
          task={completion.task}
          open={completion.open}
          onOpenChange={setCompletionOpen}
          onCloseAutoFocus={completion.returnFocus}
        />
      )}
      {remove.task && (
        <DeleteTaskDialog
          key={`delete-${remove.session}`}
          task={remove.task}
          open={remove.open}
          onOpenChange={setDeleteOpen}
          onCloseAutoFocus={remove.returnFocus}
        />
      )}
    </TaskOverlaysContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTaskOverlays(): TaskOverlays {
  const value = useContext(TaskOverlaysContext)
  if (!value) throw new Error('useTaskOverlays must be used inside <TaskOverlaysProvider>')
  return value
}
