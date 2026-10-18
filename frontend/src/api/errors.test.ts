import { describe, expect, it } from 'vitest'
import { errorFromResponse } from './errors'

const problem = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } })

describe('errorFromResponse', () => {
  it('uses the backend code, so two 409s stay distinguishable', async () => {
    const stale = await errorFromResponse(problem(409, { status: 409, code: 'VERSION_CONFLICT', detail: 'stale' }))
    const invalid = await errorFromResponse(
      problem(409, { status: 409, code: 'INVALID_STATE_TRANSITION', detail: 'Task is already completed' }),
    )
    expect(stale).toMatchObject({ status: 409, code: 'VERSION_CONFLICT', detail: 'stale' })
    expect(invalid).toMatchObject({ status: 409, code: 'INVALID_STATE_TRANSITION' })
  })

  it('keeps the code authoritative over the status (412 and 409 are both VERSION_CONFLICT)', async () => {
    const error = await errorFromResponse(problem(412, { code: 'VERSION_CONFLICT' }))
    expect(error.code).toBe('VERSION_CONFLICT')
  })

  it('falls back to the status when the body has no known code', async () => {
    expect((await errorFromResponse(problem(404, { title: 'Not Found' }))).code).toBe('TASK_NOT_FOUND')
    expect((await errorFromResponse(problem(400, { code: 'SOMETHING_NEW' }))).code).toBe('VALIDATION_FAILED')
  })

  it('classifies non-JSON responses by status', async () => {
    const proxyError = await errorFromResponse(new Response('Bad gateway', { status: 502 }))
    const serverError = await errorFromResponse(new Response('', { status: 500 }))
    expect(proxyError).toMatchObject({ code: 'NETWORK_ERROR', detail: null })
    expect(serverError.code).toBe('INTERNAL_ERROR')
  })

  it('survives a malformed JSON body', async () => {
    const response = new Response('{not json', { status: 500, headers: { 'Content-Type': 'application/json' } })
    expect((await errorFromResponse(response)).code).toBe('INTERNAL_ERROR')
  })
})
