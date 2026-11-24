import { Badge, Box, Button, Flex, Heading, HStack, Link, Skeleton, Stack, Text } from '@chakra-ui/react'
import { useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import type { Visit } from '../api/visits'
import { PageControls } from '../dogs/PageControls'
import { ErrorAlert } from '../ui/ErrorAlert'
import { formatVisitTime, visitStatusLabel } from '../visits/format'
import { useCancelVisit, useMyVisits } from '../visits/useVisits'

function readPage(search: URLSearchParams): number {
  const text = search.get('page') ?? ''
  return /^[1-9]\d{0,5}$/.test(text) ? Number(text) : 1
}

// Only a booked visit that is still ahead can be cancelled; the API checks the same.
function canCancel(visit: Visit): boolean {
  return visit.status === 'BOOKED' && new Date(visit.visitAt).getTime() > Date.now()
}

function VisitRow({ visit }: Readonly<{ visit: Visit }>) {
  const cancel = useCancelVisit()
  const [confirming, setConfirming] = useState(false)

  return (
    <Box as="li" listStyleType="none" borderWidth="1px" borderRadius="md" p="4">
      <Stack gap="2">
        <Flex justify="space-between" gap="3" wrap="wrap" align="center">
          <Link asChild variant="underline" fontWeight="semibold" fontSize="lg">
            <RouterLink to={`/dogs/${visit.dogId}`}>{visit.dogName}</RouterLink>
          </Link>
          <Badge colorPalette={visit.status === 'BOOKED' ? 'green' : 'gray'}>{visitStatusLabel(visit.status)}</Badge>
        </Flex>
        <Text>{formatVisitTime(visit.visitAt)}</Text>
        {cancel.isError && <ErrorAlert message={errorMessage(cancel.error)} />}
        {canCancel(visit) && !confirming && (
          <Button variant="outline" size="sm" alignSelf="start" onClick={() => setConfirming(true)}>
            Cancel visit
          </Button>
        )}
        {canCancel(visit) && confirming && (
          <HStack gap="2" wrap="wrap">
            <Text>Cancel this visit?</Text>
            <Button
              size="sm"
              colorPalette="red"
              loading={cancel.isPending}
              onClick={() => cancel.mutate(visit.id, { onSettled: () => setConfirming(false) })}
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

export default function MyVisitsPage() {
  const [search, setSearch] = useSearchParams()
  const page = readPage(search)
  const visits = useMyVisits(page - 1)
  const content = visits.data?.content

  return (
    <Stack gap="6">
      <Heading as="h1" size="2xl">
        My visits
      </Heading>

      {visits.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(visits.error)} />
          <Button variant="outline" onClick={() => void visits.refetch()}>
            Try again
          </Button>
        </Stack>
      )}

      {visits.isPending && (
        <Stack gap="3" aria-busy="true">
          <Text role="status" srOnly>
            Loading your visits…
          </Text>
          <Skeleton h="20" />
        </Stack>
      )}

      {content && content.length > 0 && (
        <Stack as="ul" gap="3" m="0" p="0">
          {content.map((visit) => (
            <VisitRow key={visit.id} visit={visit} />
          ))}
        </Stack>
      )}

      {content?.length === 0 && (
        <Stack gap="3" align="start">
          <Text>You have not booked any visit yet.</Text>
          <Link asChild variant="underline">
            <RouterLink to="/dogs">Browse the dogs</RouterLink>
          </Link>
        </Stack>
      )}

      <PageControls
        page={page}
        totalPages={visits.data?.page.totalPages ?? 0}
        onChange={(next) => setSearch(next > 1 ? { page: String(next) } : {})}
      />
    </Stack>
  )
}
