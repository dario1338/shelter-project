import type { Care, CareInput } from '../api/care'

export const MICROCHIP_MAX = 30
export const NOTES_MAX = 4000
export const VACCINATION_NAME_MAX = 100
export const VACCINATIONS_MAX = 30

export interface VaccinationRow {
  // Only for React: a row has no id until it is saved.
  key: number
  name: string
  vaccinatedOn: string
  nextDueOn: string
}

export type SterilizedChoice = 'unknown' | 'yes' | 'no'

export interface CareFormValues {
  sterilized: SterilizedChoice
  microchipNumber: string
  healthNotes: string
  internalNote: string
  vaccinations: VaccinationRow[]
}

export interface RowErrors {
  name?: string
  vaccinatedOn?: string
  nextDueOn?: string
}

export interface CareFormErrors {
  microchipNumber?: string
  healthNotes?: string
  internalNote?: string
  vaccinations?: string
  rows: Record<number, RowErrors>
}

let nextKey = 1

export function newRow(): VaccinationRow {
  return { key: nextKey++, name: '', vaccinatedOn: '', nextDueOn: '' }
}

function sterilizedChoice(value: boolean | null | undefined): SterilizedChoice {
  if (value === true) {
    return 'yes'
  }
  return value === false ? 'no' : 'unknown'
}

export function careToForm(care: Care | undefined): CareFormValues {
  return {
    sterilized: sterilizedChoice(care?.sterilized),
    microchipNumber: care?.microchipNumber ?? '',
    healthNotes: care?.healthNotes ?? '',
    internalNote: care?.internalNote ?? '',
    vaccinations: (care?.vaccinations ?? []).map((v) => ({
      key: nextKey++,
      name: v.name,
      vaccinatedOn: v.vaccinatedOn,
      nextDueOn: v.nextDueOn ?? '',
    })),
  }
}

function validateRow(row: VaccinationRow): RowErrors {
  const errors: RowErrors = {}
  if (!row.name.trim()) {
    errors.name = 'Enter the name of the vaccine'
  } else if (row.name.trim().length > VACCINATION_NAME_MAX) {
    errors.name = `The name must be at most ${VACCINATION_NAME_MAX} characters`
  }
  if (!row.vaccinatedOn) {
    errors.vaccinatedOn = 'Enter the date'
  }
  // Dates of the form 2026-01-31 compare correctly as text.
  if (row.nextDueOn && row.vaccinatedOn && row.nextDueOn < row.vaccinatedOn) {
    errors.nextDueOn = 'The next dose cannot be before the vaccination'
  }
  return errors
}

export function validateCareForm(values: CareFormValues): CareFormErrors {
  const errors: CareFormErrors = { rows: {} }
  if (values.microchipNumber.trim().length > MICROCHIP_MAX) {
    errors.microchipNumber = `The microchip number must be at most ${MICROCHIP_MAX} characters`
  }
  if (values.healthNotes.trim().length > NOTES_MAX) {
    errors.healthNotes = `The notes must be at most ${NOTES_MAX} characters`
  }
  if (values.internalNote.trim().length > NOTES_MAX) {
    errors.internalNote = `The internal note must be at most ${NOTES_MAX} characters`
  }
  if (values.vaccinations.length > VACCINATIONS_MAX) {
    errors.vaccinations = `At most ${VACCINATIONS_MAX} vaccinations`
  }
  for (const row of values.vaccinations) {
    const found = validateRow(row)
    if (Object.keys(found).length > 0) {
      errors.rows[row.key] = found
    }
  }
  return errors
}

export function careHasErrors(errors: CareFormErrors): boolean {
  const { rows, ...fields } = errors
  return Object.keys(fields).length > 0 || Object.keys(rows).length > 0
}

function textOrNull(text: string): string | null {
  const trimmed = text.trim()
  return trimmed ? trimmed : null
}

export function toCareInput(values: CareFormValues): CareInput {
  const sterilizedByChoice: Record<SterilizedChoice, boolean | null> = { unknown: null, yes: true, no: false }
  return {
    sterilized: sterilizedByChoice[values.sterilized],
    microchipNumber: textOrNull(values.microchipNumber),
    healthNotes: textOrNull(values.healthNotes),
    internalNote: textOrNull(values.internalNote),
    vaccinations: values.vaccinations.map((row) => ({
      name: row.name.trim(),
      vaccinatedOn: row.vaccinatedOn,
      nextDueOn: row.nextDueOn || null,
    })),
  }
}
