import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { isPreset, MINUTE_PRESETS, parseAvailableMinutes, presetLabel } from '@/lib/minutes'
import { cn } from '@/lib/utils'

const chip =
  'h-8 rounded-full border border-border bg-card px-3 text-[13px] font-medium text-foreground/80 shadow-none hover:bg-muted ' +
  'data-[state=on]:border-foreground data-[state=on]:bg-foreground data-[state=on]:text-background'

/** "I have [15m] [30m] [1h] [2h] [Any time] [Custom]". null means Any time. */
export function TimeFilter({ minutes, onChange }: { minutes: number | null; onChange: (minutes: number | null) => void }) {
  const custom = minutes !== null && !isPreset(minutes)
  const groupValue = minutes === null ? 'any' : custom ? '' : String(minutes)

  return (
    <div className="flex items-center gap-2">
      <span className="shrink-0 text-sm text-muted-foreground">I have</span>
      <div className="no-scrollbar -mx-1 flex min-w-0 items-center gap-1.5 overflow-x-auto px-1 py-1">
        <ToggleGroup
          type="single"
          spacing={1.5}
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
  const [draft, setDraft] = useState('')
  const [invalid, setInvalid] = useState(false)
  const active = value !== null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const minutes = parseAvailableMinutes(draft)
    if (minutes === null) {
      setInvalid(true)
      return
    }
    onApply(minutes)
    setOpen(false)
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          setDraft(value !== null ? String(value) : '')
          setInvalid(false)
        }
      }}
    >
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-pressed={active}
          data-state={active ? 'on' : 'off'}
          className={cn(chip, 'inline-flex shrink-0 items-center whitespace-nowrap transition-all outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50')}
        >
          {active ? `${value} min` : 'Custom'}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-60">
        <form onSubmit={submit} noValidate className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="custom-minutes">Minutes available</Label>
            <Input
              id="custom-minutes"
              type="number"
              inputMode="numeric"
              min={1}
              step={1}
              autoFocus
              value={draft}
              aria-invalid={invalid || undefined}
              aria-describedby={invalid ? 'custom-minutes-error' : undefined}
              onChange={(event) => {
                setDraft(event.target.value)
                setInvalid(false)
              }}
            />
            {invalid && (
              <p id="custom-minutes-error" className="text-xs text-destructive">
                Enter a whole number of minutes, 1 or more.
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
