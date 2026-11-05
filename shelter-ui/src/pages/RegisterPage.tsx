import { Button, chakra, Heading, Link, Stack, Text } from '@chakra-ui/react'
import { useState, type SubmitEvent } from 'react'
import { Link as RouterLink, Navigate } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import { useAuth } from '../auth/useAuth'
import { validateEmail, validateName, validateNewPassword } from '../auth/validation'
import { ErrorAlert } from '../ui/ErrorAlert'
import { FormField } from '../ui/FormField'

interface FieldErrors {
  firstName?: string
  lastName?: string
  email?: string
  password?: string
}

export default function RegisterPage() {
  const { state, signUp } = useAuth()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  // The new account is signed in right after it is created, which leaves this page by itself.
  if (state.status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: FieldErrors = {
      firstName: validateName('First name', firstName),
      lastName: validateName('Last name', lastName),
      email: validateEmail(email),
      password: validateNewPassword(password),
    }
    setErrors(found)
    setSubmitError(undefined)
    if (Object.values(found).some(Boolean)) {
      return
    }
    setSubmitting(true)
    try {
      await signUp({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        password,
      })
    } catch (error) {
      setSubmitError(errorMessage(error))
      setSubmitting(false)
    }
  }

  return (
    <Stack gap="6" maxW="md">
      <Heading as="h1" size="2xl">
        Create account
      </Heading>
      <chakra.form noValidate onSubmit={handleSubmit}>
        <Stack gap="4">
          {submitError && <ErrorAlert message={submitError} />}
          <FormField
            label="First name"
            autoComplete="given-name"
            value={firstName}
            onChange={setFirstName}
            error={errors.firstName}
          />
          <FormField
            label="Last name"
            autoComplete="family-name"
            value={lastName}
            onChange={setLastName}
            error={errors.lastName}
          />
          <FormField
            label="Email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={setEmail}
            error={errors.email}
          />
          <FormField
            label="Password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={setPassword}
            error={errors.password}
          />
          <Text fontSize="sm" color="fg.muted">
            At least 8 characters.
          </Text>
          <Button type="submit" loading={submitting}>
            Create account
          </Button>
        </Stack>
      </chakra.form>
      <Text>
        Already have an account?{' '}
        <Link asChild variant="underline">
          <RouterLink to="/login">Sign in</RouterLink>
        </Link>
      </Text>
    </Stack>
  )
}
