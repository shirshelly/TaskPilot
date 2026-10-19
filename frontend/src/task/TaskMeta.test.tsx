import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithApp, task } from '@/test/render'
import { TaskMeta } from './TaskMeta'

describe('TaskMeta durations', () => {
  it('shows the total and the minimum session as one compact item', () => {
    renderWithApp(<TaskMeta task={task({ estimatedDurationMinutes: 600, minSessionMinutes: 10 })} />)
    expect(screen.getByText(/10h · 10m min\. session/)).toBeInTheDocument()
  })

  it('shows only what is set', () => {
    renderWithApp(<TaskMeta task={task({ estimatedDurationMinutes: null, minSessionMinutes: 15 })} />)
    expect(screen.getByText('15m min. session')).toBeInTheDocument()
    expect(screen.queryByText(/Estimated duration/)).not.toBeInTheDocument()
  })

  it('keeps a plain total unchanged', () => {
    renderWithApp(<TaskMeta task={task({ estimatedDurationMinutes: 45 })} />)
    expect(screen.getByText('45m')).toBeInTheDocument()
    expect(screen.queryByText(/min\. session/)).not.toBeInTheDocument()
  })
})
