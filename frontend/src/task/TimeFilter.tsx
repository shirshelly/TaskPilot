import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { durationDraftFromMinutes, EMPTY_DURATION, parseDuration, type DurationDraft } from '@/lib/duration'
import { formatDuration } from '@/lib/format'
import { isPreset, MINUTE_PRESETS, minutesPhrase, presetLabel } from '@/lib/minutes'
import { DurationInput } from './fields'
import { cn } from '@/lib/utils'

const chip =
  'h-8 rounded-full border border-border bg-card px-3 text-[13px] font-medium text-foreground/80 shadow-none hover:bg-muted ' +
  'data-[state=on]:border-foreground data-[state=on]:bg-foreground data-[state=on]:text-background'

/** "I have [15m] [30m] [1h] [2h] [Any time] [Custom]". null means Any time. */
export function TimeFilter({ minutes, onChange }: { minutes: number | null; onChange: (minutes: number | null) => void }) {
  const custom = minutes !== null && !isPreset(minutes)
  const groupValue = minutes === null ? 'any' : custom ? '' : String(minutes)

  return (
    // Wraps rather than scrolling sideways: every option stays visible on a phone.
    <div className="flex flex-wrap items-center gap-x-2 gap-y-2">
      <span className="text-sm text-muted-foreground">I have</span>
      <div className="flex flex-wrap items-center gap-1.5">
        <ToggleGroup
          type="single"
          spacing={1.5}
          className="flex-wrap"
          aria-label="Available time"
          value={groupValue}
          onValueChange={(value) => {
            // Radix reports '' when the active item is clicked again; keep the current choice.
            if (value === 'any') onChange(null)
            else if (value) onChange(Number(value))
          }}
        >
          {MINUTE_PRESETS.map((preset) => (
            <ToggleGroupItem key={preset} value={String(preset)} className={chip}>
              {presetLabel(preset)}
            </ToggleGroupItem>
          ))}
          <ToggleGroupItem value="any" className={chip}>
            Any time
          </ToggleGroupItem>
        </ToggleGroup>
        <CustomMinutes value={custom ? minutes : null} onApply={onChange} />
      </div>
    </div>
  )
}

function CustomMinutes({ value, onApply }: { value: number | null; onApply: (minutes: number) => void }) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<DurationDraft>(EMPTY_DURATION)
  const [error, setError] = useState<string | null>(null)
  const active = value !== null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const parsed = parseDuration(draft)
    // Invalid or empty input never reaches the URL or the API.
    if (!parsed.ok || parsed.minutes === null) {
      setError(parsed.ok ? 'Enter how much time you have.' : parsed.error)
      return
    }
    onApply(parsed.minutes)
    setOpen(false)
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          setDraft(durationDraftFromMinutes(value))
          setError(null)
        }
      }}
    >
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-pressed={active}
          aria-label={active ? `Custom time: ${minutesPhrase(value)}` : 'Custom time'}
          data-state={active ? 'on' : 'off'}
          className={cn(chip, 'inline-flex shrink-0 items-center whitespace-nowrap transition-all outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50')}
        >
          {active ? formatDuration(value) : 'Custom'}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64">
        <form onSubmit={submit} noValidate className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="custom-minutes">Time available</Label>
            <DurationInput
              id="custom-minutes"
              label="Time available"
              value={draft}
              autoFocus
              error={error ?? undefined}
              onChange={(next) => {
                setDraft(next)
                setError(null)
              }}
            />
            {error && (
              <p id="custom-minutes-error" className="text-xs text-destructive">
                {error}
              </p>
            )}
          </div>
          <Button type="submit" size="sm" className="w-full">
            Show tasks
          </Button>
        </form>
      </PopoverContent>
    </Popover>
  )
}
