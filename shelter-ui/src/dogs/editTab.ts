// The parts of the page where an administrator edits a dog. Each part has its own tab and its own save,
// and the tab is in the address (?tab=photos), so that a refresh or a shared link opens the same part.
export const DOG_TABS = ['dog', 'photos', 'care'] as const

export type DogTab = (typeof DOG_TABS)[number]

export const DEFAULT_TAB: DogTab = 'dog'

// An address without a tab, or with one that does not exist, opens the first part.
export function tabFrom(value: string | null): DogTab {
  return DOG_TABS.find((tab) => tab === value) ?? DEFAULT_TAB
}

export function editPath(dogId: number, tab: DogTab = DEFAULT_TAB): string {
  const path = `/admin/dogs/${dogId}/edit`
  return tab === DEFAULT_TAB ? path : `${path}?tab=${tab}`
}
