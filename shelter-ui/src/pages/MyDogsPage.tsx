import { Box, Button, Flex, Heading, Link, Skeleton, Stack, Text } from '@chakra-ui/react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import { PageControls } from '../dogs/PageControls'
import { formatDate } from '../sponsorships/format'
import { useMyApplications } from '../sponsorships/useSponsorships'
import { ErrorAlert } from '../ui/ErrorAlert'

function readPage(search: URLSearchParams): number {
  const text = search.get('page') ?? ''
  return /^[1-9]\d{0,5}$/.test(text) ? Number(text) : 1
}

// The dogs the user sponsors are the dogs of the approved applications. The care details of a dog
// are on the page of the dog, which this list links to.
export default function MyDogsPage() {
  const [search, setSearch] = useSearchParams()
  const page = readPage(search)
  const approved = useMyApplications({ page: page - 1, status: 'APPROVED' })
  const content = approved.data?.content

  return (
    <Stack gap="6">
      <Heading as="h1" size="2xl">
        My dogs
      </Heading>

      {approved.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(approved.error)} />
          <Button variant="outline" onClick={() => void approved.refetch()}>
            Try again
          </Button>
        </Stack>
      )}

      {approved.isPending && (
        <Stack gap="3" aria-busy="true">
          <Text role="status" srOnly>
            Loading your dogs…
          </Text>
          <Skeleton h="20" />
          <Skeleton h="20" />
        </Stack>
      )}

      {content && content.length > 0 && (
        <Stack as="ul" gap="3" m="0" p="0">
          {content.map((application) => (
            <Box as="li" key={application.id} listStyleType="none" borderWidth="1px" borderRadius="md" p="4">
              <Flex justify="space-between" gap="3" wrap="wrap" align="center">
                <Stack gap="1">
                  <Link asChild variant="underline" fontWeight="semibold" fontSize="lg">
                    <RouterLink to={`/dogs/${application.dogId}`}>{application.dogName}</RouterLink>
                  </Link>
                  <Text color="fg.muted" fontSize="sm">
                    You sponsor this dog since {formatDate(application.updatedAt)}
                  </Text>
                </Stack>
                <Button asChild variant="outline" size="sm">
                  <RouterLink to={`/dogs/${application.dogId}`}>Care details</RouterLink>
                </Button>
              </Flex>
            </Box>
          ))}
        </Stack>
      )}

      {content?.length === 0 && (
        <Stack gap="3" align="start">
          <Text>{page > 1 ? 'This page does not exist.' : 'You do not sponsor any dog yet.'}</Text>
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
        totalPages={approved.data?.page.totalPages ?? 0}
        onChange={(next) => setSearch(next > 1 ? { page: String(next) } : {})}
      />
    </Stack>
  )
}
