import { Box, Button, Flex, Heading, HStack, Link, Skeleton, Stack, Text } from '@chakra-ui/react'
import { useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import type { Application } from '../api/sponsorships'
import { PageControls } from '../dogs/PageControls'
import { ApplicationStatusBadge } from '../sponsorships/ApplicationStatusBadge'
import { formatDate } from '../sponsorships/format'
import { useCancelApplication, useMyApplications } from '../sponsorships/useSponsorships'
import { ErrorAlert } from '../ui/ErrorAlert'

function readPage(search: URLSearchParams): number {
  const text = search.get('page') ?? ''
  return /^[1-9]\d{0,5}$/.test(text) ? Number(text) : 1
}

function ApplicationRow({ application }: Readonly<{ application: Application }>) {
  const cancel = useCancelApplication()
  const [confirming, setConfirming] = useState(false)

  return (
    <Box as="li" listStyleType="none" borderWidth="1px" borderRadius="md" p="4">
      <Stack gap="2">
        <Flex justify="space-between" gap="3" wrap="wrap" align="center">
          <Link asChild variant="underline" fontWeight="semibold" fontSize="lg">
            <RouterLink to={`/dogs/${application.dogId}`}>{application.dogName}</RouterLink>
          </Link>
          <ApplicationStatusBadge status={application.status} />
        </Flex>
        <Text color="fg.muted" fontSize="sm">
          Sent {formatDate(application.createdAt)}
        </Text>
        {application.userNote && <Text whiteSpace="pre-wrap">Your note: {application.userNote}</Text>}
        {application.adminNote && <Text whiteSpace="pre-wrap">Shelter’s note: {application.adminNote}</Text>}
        {cancel.isError && <ErrorAlert message={errorMessage(cancel.error)} />}
        {application.status === 'PENDING' && !confirming && (
          <Button variant="outline" size="sm" alignSelf="start" onClick={() => setConfirming(true)}>
            Cancel application
          </Button>
        )}
        {application.status === 'PENDING' && confirming && (
          <HStack gap="2" wrap="wrap">
            <Text>Cancel this application?</Text>
            <Button
              size="sm"
              colorPalette="red"
              loading={cancel.isPending}
              onClick={() => cancel.mutate(application.id, { onSettled: () => setConfirming(false) })}
            >
              Yes, cancel it
            </Button>
            <Button size="sm" variant="outline" disabled={cancel.isPending} onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </HStack>
        )}
      </Stack>
    </Box>
  )
}

export default function MyApplicationsPage() {
  const [search, setSearch] = useSearchParams()
  const page = readPage(search)
  const applications = useMyApplications(page - 1, true)
  const content = applications.data?.content

  return (
    <Stack gap="6">
      <Heading as="h1" size="2xl">
        My applications
      </Heading>

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
            Loading your applications…
          </Text>
          <Skeleton h="24" />
          <Skeleton h="24" />
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
          <Text>{page > 1 ? 'This page does not exist.' : 'You have not applied for any dog yet.'}</Text>
          {page > 1 ? (
            <Button variant="outline" onClick={() => setSearch({})}>
              Go to the first page
            </Button>
          ) : (
            <Link asChild variant="underline">
              <RouterLink to="/dogs?status=AVAILABLE">Browse available dogs</RouterLink>
            </Link>
          )}
        </Stack>
      )}

      <PageControls
        page={page}
        totalPages={applications.data?.page.totalPages ?? 0}
        onChange={(next) => setSearch(next > 1 ? { page: String(next) } : {})}
      />
    </Stack>
  )
}
