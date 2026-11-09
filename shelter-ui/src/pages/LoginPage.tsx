import { Button, chakra, Heading, Link, Stack, Text } from '@chakra-ui/react'
import { useState, type SubmitEvent } from 'react'
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom'
import { returnPathFrom } from '../auth/returnTo'
import { errorMessage } from '../api/ApiError'
import { useAuth } from '../auth/useAuth'
import { validateCurrentPassword, validateEmail } from '../auth/validation'
import { ErrorAlert } from '../ui/ErrorAlert'
import { FormField } from '../ui/FormField'

interface FieldErrors {
  email?: string
  password?: string
}

export default function LoginPage() {
  const location = useLocation()
  const { state, signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  // Signing in changes the auth state, which leaves this page by itself.
  if (state.status === 'authenticated') {
    return <Navigate to={returnPathFrom(location.state)} replace />
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = { email: validateEmail(email), password: validateCurrentPassword(password) }
    setErrors(found)
    setSubmitError(undefined)
    if (found.email || found.password) {
      return
    }
    setSubmitting(true)
    try {
      await signIn(email.trim(), password)
    } catch (error) {
      setSubmitError(errorMessage(error))
      setSubmitting(false)
    }
  }

  return (
    <Stack gap="6" maxW="md" w="full" mx="auto">
      <Heading as="h1" size="2xl" textAlign="center">
        Sign in
      </Heading>
      <chakra.form noValidate onSubmit={handleSubmit}>
        <Stack gap="4">
          {submitError && <ErrorAlert message={submitError} />}
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
            autoComplete="current-password"
            value={password}
            onChange={setPassword}
            error={errors.password}
          />
          <Button type="submit" loading={submitting}>
            Sign in
          </Button>
        </Stack>
      </chakra.form>
      <Text textAlign="center">
        No account yet?{' '}
        <Link asChild variant="underline">
          <RouterLink to="/register" state={location.state}>Create one</RouterLink>
        </Link>
      </Text>
    </Stack>
  )
}
