import type { Dog, DogInput, DogStatus } from '../api/dogs'

export const NAME_MAX = 50
export const BREED_MAX = 50
export const DESCRIPTION_MAX = 2000
export const BIRTH_YEAR_MIN = 1990
export const BIRTH_YEAR_MAX = 2100

// What the inputs hold: everything is text until it is checked.
export interface DogFormValues {
  name: string
  breed: string
  birthYear: string
  description: string
  status: DogStatus
}

export interface DogFormErrors {
  name?: string
  breed?: string
  birthYear?: string
  description?: string
}

export const emptyDogForm: DogFormValues = { name: '', breed: '', birthYear: '', description: '', status: 'AVAILABLE' }

export function dogToForm(dog: Dog): DogFormValues {
  return {
    name: dog.name,
    breed: dog.breed ?? '',
    birthYear: dog.birthYear === null ? '' : String(dog.birthYear),
    description: dog.description ?? '',
    status: dog.status,
  }
}

// The same limits as the API. The API checks them again, this only says it before the request is sent.
export function validateDogForm(values: DogFormValues): DogFormErrors {
  const errors: DogFormErrors = {}
  if (!values.name.trim()) {
    errors.name = 'Enter the name'
  } else if (values.name.trim().length > NAME_MAX) {
    errors.name = `The name must be at most ${NAME_MAX} characters`
  }
  if (values.breed.trim().length > BREED_MAX) {
    errors.breed = `The breed must be at most ${BREED_MAX} characters`
  }
  const year = values.birthYear.trim()
  if (year) {
    const parsed = /^\d{4}$/.test(year) ? Number(year) : Number.NaN
    if (Number.isNaN(parsed) || parsed < BIRTH_YEAR_MIN || parsed > BIRTH_YEAR_MAX) {
      errors.birthYear = `Enter a year from ${BIRTH_YEAR_MIN} to ${BIRTH_YEAR_MAX}`
    }
  }
  if (values.description.trim().length > DESCRIPTION_MAX) {
    errors.description = `The description must be at most ${DESCRIPTION_MAX} characters`
  }
  return errors
}

export function hasErrors(errors: object): boolean {
  return Object.keys(errors).length > 0
}

function textOrNull(text: string): string | null {
  const trimmed = text.trim()
  return trimmed ? trimmed : null
}

export function toDogInput(values: DogFormValues): DogInput {
  const year = values.birthYear.trim()
  return {
    name: values.name.trim(),
    breed: textOrNull(values.breed),
    birthYear: year ? Number(year) : null,
    description: textOrNull(values.description),
    status: values.status,
  }
}
