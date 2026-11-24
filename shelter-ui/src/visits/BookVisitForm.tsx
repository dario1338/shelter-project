import { Button, chakra, Field, Heading, Link, NativeSelect, Stack, Text } from '@chakra-ui/react'
import { useState, type SubmitEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import type { Dog } from '../api/dogs'
import { useAuth } from '../auth/useAuth'
import { ErrorAlert } from '../ui/ErrorAlert'
import { FormField } from '../ui/FormField'
import { formatVisitTime, hourLabel, validateVisitDate, VISIT_HOURS } from './format'
import { useBookVisit } from './useVisits'

// Today in the form of the date field (yyyy-mm-dd). The API checks the time as well.
function today(): string {
  return new Date().toLocaleDateString('en-CA')
}

function BookVisitFormBody({ dog }: Readonly<{ dog: Dog }>) {
  const book = useBookVisit(dog.id)
  const [date, setDate] = useState('')
  const [hour, setHour] = useState(VISIT_HOURS[0])
  const [dateError, setDateError] = useState<string>()

  if (book.isSuccess) {
    return (
      <Stack gap="2" role="status">
        <Text fontWeight="semibold">Your visit is booked for {formatVisitTime(book.data.visitAt)}.</Text>
        <Link asChild variant="underline">
          <RouterLink to="/visits">My visits</RouterLink>
        </Link>
      </Stack>
    )
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateVisitDate(date, today())
    setDateError(found)
    if (!found) {
      book.mutate({ date, hour })
    }
  }

  return (
    <chakra.form noValidate onSubmit={handleSubmit} w="full" maxW="lg">
      <Stack gap="3">
        <Heading as="h2" size="lg">
          Visit {dog.name}
        </Heading>
        <Text color="fg.muted">Visits take one hour, from 10:00 to 16:00.</Text>
        {book.isError && <ErrorAlert message={errorMessage(book.error)} />}
        <FormField label="Day" type="date" value={date} onChange={setDate} error={dateError} min={today()} />
        <Field.Root>
          <Field.Label>Time</Field.Label>
          <NativeSelect.Root>
            <NativeSelect.Field value={hour} onChange={(event) => setHour(Number(event.target.value))}>
              {VISIT_HOURS.map((option) => (
                <option key={option} value={option}>
                  {hourLabel(option)}
                </option>
              ))}
            </NativeSelect.Field>
            <NativeSelect.Indicator />
          </NativeSelect.Root>
        </Field.Root>
        <Button type="submit" alignSelf="start" loading={book.isPending}>
          Book a visit
        </Button>
      </Stack>
    </chakra.form>
  )
}

// Signed-in users can book a visit to a dog that is still in the shelter.
export function BookVisitSection({ dog }: Readonly<{ dog: Dog }>) {
  const { state } = useAuth()
  if (state.status !== 'authenticated' || state.user.role !== 'USER' || dog.status === 'ADOPTED') {
    return null
  }
  return <BookVisitFormBody dog={dog} />
}
