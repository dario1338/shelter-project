import { describe, expect, it } from 'vitest'
import { isDogStatus, listSearchFrom, readListView, writeListView } from './listSearch'

describe('readListView', () => {
  it('reads the status and the page from the address', () => {
    expect(readListView(new URLSearchParams('status=ADOPTED&page=3'))).toEqual({ page: 3, status: 'ADOPTED' })
  })

  it('shows the first page of all dogs by default', () => {
    expect(readListView(new URLSearchParams(''))).toEqual({ page: 1, status: undefined })
  })

  it.each(['0', '-2', 'abc', '1.5', '9999999'])('ignores the page %j', (page) => {
    expect(readListView(new URLSearchParams({ page })).page).toBe(1)
  })

  it('ignores a status that does not exist', () => {
    expect(readListView(new URLSearchParams('status=LOST')).status).toBeUndefined()
  })
})

describe('writeListView', () => {
  it('writes only what differs from the default', () => {
    expect(writeListView({ page: 1, status: undefined }).toString()).toBe('')
    expect(writeListView({ page: 2, status: undefined }).toString()).toBe('page=2')
    expect(writeListView({ page: 1, status: 'SPONSORED' }).toString()).toBe('status=SPONSORED')
    expect(writeListView({ page: 4, status: 'ADOPTED' }).toString()).toBe('status=ADOPTED&page=4')
  })
})

describe('isDogStatus', () => {
  it('knows the three statuses', () => {
    expect(isDogStatus('AVAILABLE')).toBe(true)
    expect(isDogStatus('')).toBe(false)
    expect(isDogStatus('available')).toBe(false)
  })
})

describe('listSearchFrom', () => {
  it('returns the query string that came with the link', () => {
    expect(listSearchFrom({ listSearch: '?status=ADOPTED' })).toBe('?status=ADOPTED')
    expect(listSearchFrom({ listSearch: '' })).toBe('')
  })

  it.each([null, undefined, 'text', 5, {}, { listSearch: 5 }, { listSearch: 'status=ADOPTED' }, { listSearch: 'http://evil.example' }])(
    'returns nothing for %j',
    (state) => {
      expect(listSearchFrom(state)).toBe('')
    },
  )
})
