import { describe, expect, it } from 'vitest'
import { dog } from '../test/dogs'
import { dogToForm, emptyDogForm, hasErrors, toDogInput, validateDogForm } from './dogFormValues'

describe('dog form', () => {
  it('accepts a dog with only a name', () => {
    expect(hasErrors(validateDogForm({ ...emptyDogForm, name: 'Rex' }))).toBe(false)
  })

  it('requires the name and limits the lengths', () => {
    expect(validateDogForm({ ...emptyDogForm, name: '   ' }).name).toBe('Enter the name')
    expect(validateDogForm({ ...emptyDogForm, name: 'a'.repeat(51) }).name).toMatch(/at most 50/)
    expect(validateDogForm({ ...emptyDogForm, name: 'Rex', breed: 'a'.repeat(51) }).breed).toMatch(/at most 50/)
    expect(validateDogForm({ ...emptyDogForm, name: 'Rex', description: 'a'.repeat(2001) }).description).toMatch(/at most 2000/)
  })

  it.each(['1989', '2101', 'abc', '20', '2020.5', '-2020'])('rejects the year "%s"', (year) => {
    expect(validateDogForm({ ...emptyDogForm, name: 'Rex', birthYear: year }).birthYear).toMatch(/from 1990 to 2100/)
  })

  it.each(['1990', '2100', '2020', ' 2020 '])('accepts the year "%s"', (year) => {
    expect(validateDogForm({ ...emptyDogForm, name: 'Rex', birthYear: year }).birthYear).toBeUndefined()
  })

  it('sends missing values as null and trims the text', () => {
    expect(toDogInput({ name: ' Rex ', breed: '  ', birthYear: ' ', description: ' Calm ', status: 'ADOPTED' })).toEqual({
      name: 'Rex',
      breed: null,
      birthYear: null,
      description: 'Calm',
      status: 'ADOPTED',
    })
  })

  it('fills the form from a dog, also one with missing values', () => {
    expect(dogToForm(dog({ breed: null, birthYear: null, description: null }))).toEqual({
      name: 'Rex',
      breed: '',
      birthYear: '',
      description: '',
      status: 'AVAILABLE',
    })
    expect(dogToForm(dog()).birthYear).toBe('2020')
  })
})
