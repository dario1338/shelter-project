import type { DogStatus } from '../api/dogs'

const STATUS_LABELS: Record<DogStatus, string> = {
  AVAILABLE: 'Available',
  SPONSORED: 'Sponsored',
  ADOPTED: 'Adopted',
}

export function statusLabel(status: DogStatus): string {
  return STATUS_LABELS[status]
}

// Only the year of birth is known, so the age is approximate.
export function dogAge(birthYear: number | null, currentYear = new Date().getFullYear()): string | null {
  if (birthYear === null) {
    return null
  }
  const years = currentYear - birthYear
  if (years <= 0) {
    return 'Under 1 year'
  }
  return years === 1 ? '1 year' : `${years} years`
}

// The id in the address is text. Only a positive whole number can be a dog's id.
export function parseDogId(text: string | undefined): number | null {
  if (text === undefined || !/^[1-9]\d{0,15}$/.test(text)) {
    return null
  }
  const id = Number(text)
  return Number.isSafeInteger(id) ? id : null
}

// What to say when a page of the list has no dogs.
export function emptyListMessage(page: number, status: DogStatus | undefined): string {
  if (page > 1) {
    return 'This page does not exist.'
  }
  if (status) {
    return `There are no ${statusLabel(status).toLowerCase()} dogs right now.`
  }
  return 'There are no dogs yet.'
}
