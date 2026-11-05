import { describe, expect, it } from 'vitest'
import {
  validateCurrentPassword,
  validateEmail,
  validateName,
  validateNewPassword,
} from './validation'

describe('validateEmail', () => {
  it.each(['ana@example.com', ' ana@example.com ', 'a.b+c@mail.example.org'])('accepts %j', (value) => {
    expect(validateEmail(value)).toBeUndefined()
  })

  it.each(['', '   '])('requires a value (%j)', (value) => {
    expect(validateEmail(value)).toBe('Email is required')
  })

  it.each(['ana', 'ana@', '@example.com', 'ana@example', 'ana@@example.com', 'a b@example.com', 'ana@.com', 'ana@example.'])(
    'rejects %j',
    (value) => {
      expect(validateEmail(value)).toBe('Enter a valid email address')
    },
  )

  it('limits the length to what the API accepts', () => {
    const tooLong = `${'a'.repeat(95)}@x.com`
    expect(validateEmail(tooLong)).toBe('Email must be at most 100 characters')
  })
})

describe('validateCurrentPassword', () => {
  it('requires a value but does not judge its strength', () => {
    expect(validateCurrentPassword('')).toBe('Password is required')
    expect(validateCurrentPassword('x')).toBeUndefined()
  })

  it('limits the length', () => {
    expect(validateCurrentPassword('x'.repeat(73))).toBe('Password must be at most 72 characters')
    expect(validateCurrentPassword('x'.repeat(72))).toBeUndefined()
  })
})

describe('validateNewPassword', () => {
  it('requires 8 to 72 characters', () => {
    expect(validateNewPassword('')).toBe('Password is required')
    expect(validateNewPassword('1234567')).toBe('Password must be at least 8 characters')
    expect(validateNewPassword('12345678')).toBeUndefined()
    expect(validateNewPassword('x'.repeat(72))).toBeUndefined()
    expect(validateNewPassword('x'.repeat(73))).toBe('Password must be at most 72 characters')
  })
})

describe('validateName', () => {
  it('requires a value and uses the label in the message', () => {
    expect(validateName('First name', '  ')).toBe('First name is required')
    expect(validateName('First name', 'Ana')).toBeUndefined()
  })

  it('limits the length', () => {
    expect(validateName('Last name', 'x'.repeat(51))).toBe('Last name must be at most 50 characters')
  })
})
