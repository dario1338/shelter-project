import type { DogStatus } from '../api/dogs'

const STATUSES: readonly DogStatus[] = ['AVAILABLE', 'SPONSORED', 'ADOPTED']

export interface ListView {
  // One-based, as shown to the user.
  page: number
  status: DogStatus | undefined
}

// The list is described by the address (?status=AVAILABLE&page=2), so it can be shared and the back
// button works. Anything that is not understood falls back to the first page of all dogs.
export function readListView(search: URLSearchParams): ListView {
  const status = STATUSES.find((candidate) => candidate === search.get('status'))
  const pageText = search.get('page') ?? ''
  const page = /^[1-9]\d{0,5}$/.test(pageText) ? Number(pageText) : 1
  return { page, status }
}

export function writeListView({ page, status }: ListView): URLSearchParams {
  const search = new URLSearchParams()
  if (status) {
    search.set('status', status)
  }
  if (page > 1) {
    search.set('page', String(page))
  }
  return search
}

export function isDogStatus(value: string): value is DogStatus {
  return STATUSES.some((candidate) => candidate === value)
}

// The query string the list had when the user opened a dog, or '' when they came another way.
export function listSearchFrom(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'listSearch' in state) {
    const value = (state as { listSearch: unknown }).listSearch
    if (typeof value === 'string' && (value === '' || value.startsWith('?'))) {
      return value
    }
  }
  return ''
}
