import { useId, useState, type ReactNode } from 'react'
import { CalendarClock, Flag, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { switchUnit, type DurationDraft, type DurationUnit } from '@/lib/duration'
import type { StartOnInput } from '@/lib/patch'
import { cn } from '@/lib/utils'

// Form fields shared by Quick Create and the Edit sheet. Plain controlled inputs: the forms are
// small enough that a form library would add more than it saves.

function Field({
  label,
  htmlFor,
  hint,
  error,
  children,
  className,
}: {
  label: ReactNode
  htmlFor?: string
  hint?: ReactNode
  error?: string
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <Label htmlFor={htmlFor} className="text-[13px] text-foreground/80">
        {label}
      </Label>
      {children}
      {error ? (
        <p id={htmlFor ? `${htmlFor}-error` : undefined} className="text-xs text-destructive">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}

export function TitleField({
  value,
  onChange,
  error,
  autoFocus,
  className,
  inputClassName,
}: {
  value: string
  onChange: (value: string) => void
  error?: string
  autoFocus?: boolean
  className?: string
  inputClassName?: string
}) {
  const id = useId()
  return (
    <Field label="Title" htmlFor={id} error={error} className={className}>
      <Input
        id={id}
        value={value}
        autoFocus={autoFocus}
        autoComplete="off"
        maxLength={255}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
        className={inputClassName}
      />
    </Field>
  )
}

export function DescriptionField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const id = useId()
  return (
    <Field label="Description" htmlFor={id}>
      <Textarea id={id} value={value} rows={3} onChange={(event) => onChange(event.target.value)} />
    </Field>
  )
}

const IMPORTANCE_LABELS: Record<number, string> = { 1: 'Low', 2: 'Below normal', 3: 'Normal', 4: 'High', 5: 'Top' }

export function ImportanceField({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return (
    <Field
      label={
        <>
          Importance <span className="font-normal text-muted-foreground">· {IMPORTANCE_LABELS[value]}</span>
        </>
      }
    >
      <ToggleGroup
        type="single"
        variant="outline"
        spacing={0}
        value={String(value)}
        onValueChange={(next) => next && onChange(Number(next))}
        aria-label="Importance"
        className="w-full"
      >
        {[1, 2, 3, 4, 5].map((level) => (
          <ToggleGroupItem
            key={level}
            value={String(level)}
            aria-label={`${level} – ${IMPORTANCE_LABELS[level]}`}
            className="h-8 flex-1 text-[13px] data-[state=on]:bg-foreground data-[state=on]:text-background"
          >
            {level}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </Field>
  )
}

export function DurationField({
  value,
  onChange,
  error,
}: {
  value: DurationDraft
  onChange: (value: DurationDraft) => void
  error?: string
}) {
  const id = useId()
  return (
    <Field label="Estimated duration" htmlFor={id} error={error} hint="Total time to finish the whole task.">
      <DurationInput id={id} label="Estimated duration" placeholder="No estimate" value={value} onChange={onChange} error={error} />
    </Field>
  )
}

export function MinSessionField({
  value,
  onChange,
  error,
}: {
  value: DurationDraft
  onChange: (value: DurationDraft) => void
  error?: string
}) {
  const id = useId()
  return (
    <Field label="Minimum session" htmlFor={id} error={error} hint="Shortest useful amount of time you can spend on this task.">
      <DurationInput id={id} label="Minimum session" placeholder="Optional" value={value} onChange={onChange} error={error} />
    </Field>
  )
}

/**
 * A duration typed in minutes or hours (decimals allowed for hours). Switching the unit converts
 * the text so the underlying duration stays the same; if there's no exact way to write it in the
 * new unit, the unit stays put and a short note explains why.
 */
export function DurationInput({
  id,
  label,
  value,
  onChange,
  placeholder,
  error,
  autoFocus,
}: {
  id: string
  label: string
  value: DurationDraft
  onChange: (value: DurationDraft) => void
  placeholder?: string
  error?: string
  autoFocus?: boolean
}) {
  const [note, setNote] = useState<string | null>(null)
  const noteId = `${id}-unit-note`
  return (
    <div className="space-y-1.5">
      <div className="flex gap-2">
        <Input
          id={id}
          type="text"
          inputMode="decimal"
          autoComplete="off"
          autoFocus={autoFocus}
          placeholder={placeholder}
          value={value.text}
          aria-invalid={error ? true : undefined}
          aria-describedby={[error ? `${id}-error` : '', note ? noteId : ''].filter(Boolean).join(' ') || undefined}
          onChange={(event) => {
            setNote(null)
            onChange({ ...value, text: event.target.value })
          }}
          className="min-w-0 flex-1"
        />
        <ToggleGroup
          type="single"
          variant="outline"
          spacing={0}
          value={value.unit}
          aria-label={`${label} unit`}
          onValueChange={(unit) => {
            if (!unit) return
            const result = switchUnit(value, unit as DurationUnit)
            if (result.ok) {
              setNote(null)
              onChange(result.draft)
            } else {
              setNote(result.reason)
            }
          }}
          className="shrink-0 bg-card"
        >
          <ToggleGroupItem value="min" aria-label="Minutes" className={UNIT_ITEM}>
            min
          </ToggleGroupItem>
          <ToggleGroupItem value="h" aria-label="Hours" className={UNIT_ITEM}>
            h
          </ToggleGroupItem>
        </ToggleGroup>
      </div>
      {note && (
        <p id={noteId} role="status" className="text-xs text-muted-foreground">
          {note}
        </p>
      )}
    </div>
  )
}

const UNIT_ITEM = 'h-8 min-w-10 px-2.5 text-[13px] data-[state=on]:bg-foreground data-[state=on]:text-background'

export function DueByField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const id = useId()
  return (
    <Field
      label={
        <span className="inline-flex items-center gap-1.5">
          <Flag className="size-3.5" aria-hidden="true" /> Due by
        </span>
      }
      htmlFor={id}
      hint="The date it must be finished by. It doesn't hide the task."
    >
      <ClearableDate id={id} value={value} onChange={onChange} label="Clear due date" />
    </Field>
  )
}

export function StartOnField({
  value,
  onChange,
  minDate,
}: {
  value: StartOnInput
  onChange: (value: StartOnInput) => void
  minDate: string
}) {
  const id = useId()
  const timeId = useId()
  const showTime = value.time !== '' || value.date !== ''
  return (
    <Field
      label={
        <span className="inline-flex items-center gap-1.5">
          <CalendarClock className="size-3.5" aria-hidden="true" /> Start on
        </span>
      }
      htmlFor={id}
      hint="Hidden until this day, then it shows up in Today. A time is just for your reference."
    >
      <div className="flex gap-2">
        <ClearableDate
          id={id}
          value={value.date}
          min={minDate}
          label="Clear start date"
          onChange={(date) => onChange({ date, time: date === '' ? '' : value.time })}
          className="flex-1"
        />
        {showTime && (
          <Input
            id={timeId}
            type="time"
            aria-label="Start time (optional)"
            value={value.time}
            onChange={(event) => onChange({ ...value, time: event.target.value })}
            className="w-28"
          />
        )}
      </div>
    </Field>
  )
}

function ClearableDate({
  id,
  value,
  onChange,
  min,
  label,
  className,
}: {
  id: string
  value: string
  onChange: (value: string) => void
  min?: string
  label: string
  className?: string
}) {
  return (
    <div className={cn('relative', className)}>
      <Input id={id} type="date" min={min} value={value} onChange={(event) => onChange(event.target.value)} className="pr-9" />
      {value && (
        <button
          type="button"
          aria-label={label}
          onClick={() => onChange('')}
          className="absolute inset-y-0 right-1.5 my-auto grid size-6 place-items-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <X className="size-3.5" aria-hidden="true" />
        </button>
      )}
    </div>
  )
}
