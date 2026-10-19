import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { json, renderWithApp, requestedUrls, stubFetch, task } from '@/test/render'
import { FocusPanel } from './FocusPanel'

afterEach(() => vi.unstubAllGlobals())

describe('FocusPanel', () => {
  it('features the first recommendation and keeps the backend order for the rest', async () => {
    const ranked = [task({ title: 'Finish report' }), task({ title: 'Call bank' }), task({ title: 'Wash dishes' })]
    stubFetch(() => json(ranked))

    renderWithApp(<FocusPanel />)

    const featured = await screen.findByRole('article')
    expect(within(featured).getByRole('heading')).toHaveTextContent('Finish report')
    const others = within(screen.getByRole('list', { name: 'Other suggestions' })).getAllByRole('listitem')
    expect(others.map((item) => item.textContent)).toEqual(['Call bank', 'Wash dishes'])
  })

  it('shows a few alternatives and reveals the rest on request', async () => {
    stubFetch(() => json(Array.from({ length: 8 }, (_, index) => task({ title: `Ranked ${index + 1}` }))))
    renderWithApp(<FocusPanel />)

    const list = await screen.findByRole('list', { name: 'Other suggestions' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(4)
    await userEvent.click(screen.getByRole('button', { name: 'Show 3 more' }))
    expect(within(list).getAllByRole('listitem')).toHaveLength(7)
  })

  it('selecting a preset puts it in the URL and asks the backend for that limit', async () => {
    const fetchMock = stubFetch(() => json([task()]))
    const { router } = renderWithApp(<FocusPanel />)
    await screen.findByRole('article')

    await userEvent.click(screen.getByRole('radio', { name: '30m' }))

    await waitFor(() => expect(router.state.location.search).toBe('?minutes=30'))
    await waitFor(() => expect(requestedUrls(fetchMock)).toContain('/api/recommendations?availableMinutes=30'))
    expect(requestedUrls(fetchMock)[0]).toBe('/api/recommendations')
  })

  it('restores a custom limit from the URL, and an empty result offers to go back to Any time', async () => {
    const fetchMock = stubFetch((url) =>
      json(url.searchParams.has('availableMinutes') ? [] : [task({ title: 'Long task' })]),
    )
    const { router } = renderWithApp(<FocusPanel />, { route: '/?minutes=45' })

    expect(await screen.findByText('Nothing fits in 45 minutes.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '45 min' })).toHaveAttribute('aria-pressed', 'true')
    // The backend decides eligibility; the hint only explains which duration it looks at.
    expect(screen.getByText(/minimum session — or estimated duration, if there's none — fits/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Show all (Any time)' }))

    expect(await screen.findByText('Long task')).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
    expect(requestedUrls(fetchMock)).toEqual(['/api/recommendations?availableMinutes=45', '/api/recommendations'])
  })

  it('applies a custom number of minutes and rejects invalid input', async () => {
    const fetchMock = stubFetch(() => json([task()]))
    const { router } = renderWithApp(<FocusPanel />)
    await screen.findByRole('article')

    await userEvent.click(screen.getByRole('button', { name: 'Custom' }))
    const input = await screen.findByLabelText('Minutes available')
    await userEvent.type(input, '0')
    await userEvent.click(screen.getByRole('button', { name: 'Show tasks' }))
    expect(screen.getByText('Enter a whole number of minutes, 1 or more.')).toBeInTheDocument()

    await userEvent.clear(input)
    await userEvent.type(input, '45')
    await userEvent.click(screen.getByRole('button', { name: 'Show tasks' }))

    await waitFor(() => expect(router.state.location.search).toBe('?minutes=45'))
    await waitFor(() => expect(requestedUrls(fetchMock)).toContain('/api/recommendations?availableMinutes=45'))
  })

  it('treats an invalid minutes value as Any time and cleans the URL', async () => {
    const fetchMock = stubFetch(() => json([task()]))
    const { router } = renderWithApp(<FocusPanel />, { route: '/?minutes=abc' })

    await screen.findByRole('article')
    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(requestedUrls(fetchMock)).toEqual(['/api/recommendations'])
  })
})
