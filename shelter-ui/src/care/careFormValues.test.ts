import { describe, expect, it } from 'vitest'
import { care, noCare } from '../test/care'
import { careHasErrors, careToForm, newRow, toCareInput, validateCareForm } from './careFormValues'

describe('care form', () => {
  it('starts empty for a dog without details', () => {
    const form = careToForm(noCare)

    expect(form.sterilized).toBe('unknown')
    expect(form.vaccinations).toEqual([])
    expect(careHasErrors(validateCareForm(form))).toBe(false)
  })

  it.each([
    [true, 'yes'],
    [false, 'no'],
    [null, 'unknown'],
  ] as const)('maps sterilized %s to the choice %s and back', (value, choice) => {
    const form = careToForm(care({ sterilized: value }))

    expect(form.sterilized).toBe(choice)
    expect(toCareInput(form).sterilized).toBe(value)
  })

  it('keeps the vaccinations of the details', () => {
    const input = toCareInput(careToForm(care()))

    expect(input.vaccinations).toEqual([{ name: 'Rabies', vaccinatedOn: '2026-01-05', nextDueOn: '2027-01-05' }])
    expect(input.microchipNumber).toBe('900123')
  })

  it('works without any details given (the form of a new dog)', () => {
    expect(careToForm(undefined).microchipNumber).toBe('')
  })

  it('requires the name and the date of a vaccination', () => {
    const row = newRow()
    const errors = validateCareForm({ ...careToForm(undefined), vaccinations: [row] })

    expect(errors.rows[row.key]).toEqual({ name: 'Enter the name of the vaccine', vaccinatedOn: 'Enter the date' })
    expect(careHasErrors(errors)).toBe(true)
  })

  it('does not allow the next dose before the vaccination', () => {
    const row = { ...newRow(), name: 'Rabies', vaccinatedOn: '2026-02-01', nextDueOn: '2026-01-01' }

    expect(validateCareForm({ ...careToForm(undefined), vaccinations: [row] }).rows[row.key].nextDueOn).toMatch(/before/)
  })

  it('limits the text and the number of vaccinations', () => {
    const rows = Array.from({ length: 31 }, () => ({ ...newRow(), name: 'A', vaccinatedOn: '2026-01-01' }))
    const errors = validateCareForm({
      ...careToForm(undefined),
      microchipNumber: '1'.repeat(31),
      healthNotes: 'a'.repeat(4001),
      internalNote: 'a'.repeat(4001),
      vaccinations: rows,
    })

    expect(errors.microchipNumber).toBeDefined()
    expect(errors.healthNotes).toBeDefined()
    expect(errors.internalNote).toBeDefined()
    expect(errors.vaccinations).toBeDefined()
  })

  it('sends empty text as null and an empty next dose as null', () => {
    const row = { ...newRow(), name: ' Rabies ', vaccinatedOn: '2026-01-01', nextDueOn: '' }

    expect(toCareInput({ ...careToForm(undefined), healthNotes: '  ', vaccinations: [row] })).toEqual({
      sterilized: null,
      microchipNumber: null,
      healthNotes: null,
      internalNote: null,
      vaccinations: [{ name: 'Rabies', vaccinatedOn: '2026-01-01', nextDueOn: null }],
    })
  })
})
