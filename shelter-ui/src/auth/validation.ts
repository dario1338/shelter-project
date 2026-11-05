// Client-side checks that mirror the constraints of the API (RegisterRequest and LoginRequest).
// The API does not return per-field messages for invalid input, so the form has to explain them.
// The API stays the authority: these checks only save a round trip and say what is wrong.

export const EMAIL_MAX = 100
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 72
export const NAME_MAX = 50

function isPlausibleEmail(email: string): boolean {
  const at = email.indexOf('@')
  const domain = email.slice(at + 1)
  return (
    at > 0 &&
    at === email.lastIndexOf('@') &&
    !/\s/.test(email) &&
    domain.includes('.') &&
    !domain.startsWith('.') &&
    !domain.endsWith('.')
  )
}

export function validateEmail(value: string): string | undefined {
  const email = value.trim()
  if (!email) return 'Email is required'
  if (email.length > EMAIL_MAX) return `Email must be at most ${EMAIL_MAX} characters`
  if (!isPlausibleEmail(email)) return 'Enter a valid email address'
  return undefined
}

// Used on sign in: the existing password is not judged, only that something was typed.
export function validateCurrentPassword(value: string): string | undefined {
  if (!value) return 'Password is required'
  if (value.length > PASSWORD_MAX) return `Password must be at most ${PASSWORD_MAX} characters`
  return undefined
}

export function validateNewPassword(value: string): string | undefined {
  if (!value) return 'Password is required'
  if (value.length < PASSWORD_MIN) return `Password must be at least ${PASSWORD_MIN} characters`
  if (value.length > PASSWORD_MAX) return `Password must be at most ${PASSWORD_MAX} characters`
  return undefined
}

export function validateName(label: string, value: string): string | undefined {
  const name = value.trim()
  if (!name) return `${label} is required`
  if (name.length > NAME_MAX) return `${label} must be at most ${NAME_MAX} characters`
  return undefined
}
