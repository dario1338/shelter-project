import { Button, chakra, Field, Heading, Link, Stack, Text, Textarea } from '@chakra-ui/react'
import { useState, type SubmitEvent } from 'react'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import type { Dog } from '../api/dogs'
import type { ReturnState } from '../auth/returnTo'
import { useAuth } from '../auth/useAuth'
import { ErrorAlert } from '../ui/ErrorAlert'
import { NOTE_MAX, validateNote } from './format'
import { useApply } from './useSponsorships'

function SignInPrompt() {
  const location = useLocation()
  const state: ReturnState = { from: `${location.pathname}${location.search}` }
  return (
    <Text>
      <Link asChild variant="underline">
        <RouterLink to="/login" state={state}>
          Sign in
        </RouterLink>
      </Link>{' '}
      to apply for sponsorship of this dog.
    </Text>
  )
}

function ApplyFormBody({ dog }: Readonly<{ dog: Dog }>) {
  const apply = useApply(dog.id)
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string>()

  if (apply.isSuccess) {
    return (
      <Stack gap="2" role="status">
        <Text fontWeight="semibold">Your application was sent.</Text>
        <Text>The shelter will review it. You can follow it in your applications.</Text>
        <Link asChild variant="underline">
          <RouterLink to="/applications">My applications</RouterLink>
        </Link>
      </Stack>
    )
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateNote(note)
    setNoteError(found)
    if (!found) {
      apply.mutate(note)
    }
  }

  return (
    <chakra.form noValidate onSubmit={handleSubmit} w="full" maxW="lg">
      <Stack gap="3">
        <Heading as="h2" size="lg">
          Sponsor {dog.name}
        </Heading>
        {apply.isError && <ErrorAlert message={errorMessage(apply.error)} />}
        <Field.Root invalid={Boolean(noteError)}>
          <Field.Label>Note for the shelter (optional)</Field.Label>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} rows={4} />
          <Field.HelperText>
            {note.length} / {NOTE_MAX}
          </Field.HelperText>
          <Field.ErrorText>{noteError}</Field.ErrorText>
        </Field.Root>
        <Button type="submit" alignSelf="start" loading={apply.isPending}>
          Apply for sponsorship
        </Button>
      </Stack>
    </chakra.form>
  )
}

// What a visitor can do about sponsoring this dog depends on who they are and on the dog's status.
export function SponsorshipSection({ dog }: Readonly<{ dog: Dog }>) {
  const { state } = useAuth()

  if (state.status === 'loading') {
    return null
  }
  if (dog.status !== 'AVAILABLE') {
    return <Text color="fg.muted">This dog is not available for sponsorship.</Text>
  }
  if (state.status === 'anonymous') {
    return <SignInPrompt />
  }
  // Only users apply. An administrator reviews applications, which is a separate page.
  if (state.user.role !== 'USER') {
    return null
  }
  return <ApplyFormBody dog={dog} />
}
