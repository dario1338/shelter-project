import { Box, Button, chakra, Field, Flex, Heading, HStack, Link, Skeleton, Stack, Text, Textarea } from '@chakra-ui/react'
import { useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import type { Application, Decision } from '../api/sponsorships'
import { PageControls } from '../dogs/PageControls'
import { ApplicationStatusBadge } from '../sponsorships/ApplicationStatusBadge'
import { ApplicationStatusFilter } from '../sponsorships/ApplicationStatusFilter'
import { formatDate, NOTE_MAX, validateNote } from '../sponsorships/format'
import { readApplicationsView, writeApplicationsView, type ApplicationsView } from '../sponsorships/listView'
import { useApplications, useDecideApplication } from '../sponsorships/useSponsorships'
import { ErrorAlert } from '../ui/ErrorAlert'

function ReviewPanel({ application, onClose }: Readonly<{ application: Application; onClose: () => void }>) {
  const decide = useDecideApplication()
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string>()

  function submit(decision: Decision) {
    const found = validateNote(note)
    setNoteError(found)
    if (!found) {
      decide.mutate({ id: application.id, decision, adminNote: note }, { onSuccess: onClose })
    }
  }

  return (
    <chakra.form noValidate onSubmit={(event) => event.preventDefault()}>
      <Stack gap="3">
        {decide.isError && <ErrorAlert message={errorMessage(decide.error)} />}
        <Field.Root invalid={Boolean(noteError)}>
          <Field.Label>Note for the applicant (optional)</Field.Label>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} rows={3} />
          <Field.HelperText>
            {note.length} / {NOTE_MAX}. The applicant sees this note.
          </Field.HelperText>
          <Field.ErrorText>{noteError}</Field.ErrorText>
        </Field.Root>
        <Text fontSize="sm" color="fg.muted">
          Approving marks {application.dogName} as sponsored and rejects the other pending applications for this dog.
        </Text>
        <HStack gap="2" wrap="wrap">
          <Button colorPalette="green" loading={decide.isPending} onClick={() => submit('approve')}>
            Approve
          </Button>
          <Button colorPalette="red" variant="outline" loading={decide.isPending} onClick={() => submit('reject')}>
            Reject
          </Button>
          <Button variant="ghost" disabled={decide.isPending} onClick={onClose}>
            Close
          </Button>
        </HStack>
      </Stack>
    </chakra.form>
  )
}

function ApplicationRow({ application }: Readonly<{ application: Application }>) {
  const [reviewing, setReviewing] = useState(false)

  return (
    <Box as="li" listStyleType="none" borderWidth="1px" borderRadius="md" p="4">
      <Stack gap="2">
        <Flex justify="space-between" gap="3" wrap="wrap" align="center">
          <Link asChild variant="underline" fontWeight="semibold" fontSize="lg">
            <RouterLink to={`/dogs/${application.dogId}`}>{application.dogName}</RouterLink>
          </Link>
          <ApplicationStatusBadge status={application.status} />
        </Flex>
        <Text>
          {application.applicantName} ({application.applicantEmail})
        </Text>
        <Text color="fg.muted" fontSize="sm">
          Sent {formatDate(application.createdAt)}
        </Text>
        {application.userNote && <Text whiteSpace="pre-wrap">Applicant’s note: {application.userNote}</Text>}
        {application.adminNote && <Text whiteSpace="pre-wrap">Shelter’s note: {application.adminNote}</Text>}
        {application.status === 'PENDING' && !reviewing && (
          <Button size="sm" alignSelf="start" onClick={() => setReviewing(true)}>
            Review
          </Button>
        )}
        {application.status === 'PENDING' && reviewing && (
          <ReviewPanel application={application} onClose={() => setReviewing(false)} />
        )}
      </Stack>
    </Box>
  )
}

export default function AdminApplicationsPage() {
  const [search, setSearch] = useSearchParams()
  const view = readApplicationsView(search)
  const applications = useApplications({ page: view.page - 1, status: view.status })
  const content = applications.data?.content

  const show = (next: ApplicationsView) => setSearch(writeApplicationsView(next))

  return (
    <Stack gap="6">
      <Flex align="end" justify="space-between" gap="4" wrap="wrap">
        <Heading as="h1" size="2xl">
          Applications
        </Heading>
        <ApplicationStatusFilter value={view.status} onChange={(status) => show({ page: 1, status })} />
      </Flex>

      {applications.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(applications.error)} />
          <Button variant="outline" onClick={() => void applications.refetch()}>
            Try again
          </Button>
        </Stack>
      )}

      {applications.isPending && (
        <Stack gap="3" aria-busy="true">
          <Text role="status" srOnly>
            Loading applications…
          </Text>
          <Skeleton h="28" />
          <Skeleton h="28" />
        </Stack>
      )}

      {content && content.length > 0 && (
        <Stack as="ul" gap="3" m="0" p="0">
          {content.map((application) => (
            <ApplicationRow key={application.id} application={application} />
          ))}
        </Stack>
      )}

      {content?.length === 0 && (
        <Stack gap="3" align="start">
          <Text>{view.page > 1 ? 'This page does not exist.' : 'There are no applications here.'}</Text>
          {view.page > 1 && (
            <Button variant="outline" onClick={() => show({ ...view, page: 1 })}>
              Go to the first page
            </Button>
          )}
        </Stack>
      )}

      <PageControls
        page={view.page}
        totalPages={applications.data?.page.totalPages ?? 0}
        onChange={(page) => show({ ...view, page })}
      />
    </Stack>
  )
}
