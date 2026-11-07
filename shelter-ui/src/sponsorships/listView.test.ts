import { describe, expect, it } from 'vitest'
import { readApplicationsView, writeApplicationsView } from './listView'

describe('applications list view', () => {
  it('reads the status and the page from the address', () => {
    expect(readApplicationsView(new URLSearchParams('status=APPROVED&page=3'))).toEqual({ page: 3, status: 'APPROVED' })
  })

  it.each(['status=FLYING&page=0', 'page=-1', 'page=abc', 'page=1.5', ''])('falls back to the first page for "%s"', (text) => {
    expect(readApplicationsView(new URLSearchParams(text))).toEqual({ page: 1, status: undefined })
  })

  it('writes only what differs from the default', () => {
    expect(writeApplicationsView({ page: 1, status: undefined }).toString()).toBe('')
    expect(writeApplicationsView({ page: 2, status: 'PENDING' }).toString()).toBe('status=PENDING&page=2')
  })
})
