import { Button, Flex, Heading, HStack, SimpleGrid, Skeleton, Stack, Text } from '@chakra-ui/react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { errorMessage } from '../api/ApiError'
import { WIDE_DOG_PAGE_SIZE } from '../api/dogs'
import { DogCard } from '../dogs/DogCard'
import { emptyListMessage } from '../dogs/format'
import { readListView, writeListView, type ListView } from '../dogs/listSearch'
import { PageControls } from '../dogs/PageControls'
import { StatusFilter } from '../dogs/StatusFilter'
import { useDogs } from '../dogs/useDogs'
import { ErrorAlert } from '../ui/ErrorAlert'
import { useMediaQuery, VERY_WIDE } from '../ui/screens'

const GRID_COLUMNS = { base: 1, sm: 2, md: 3, xl: 4, '2xl': 5 }

// Fixed names, so that the placeholders have stable keys.
const SKELETON_KEYS = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l']

function DogSkeletons() {
  return (
    <SimpleGrid columns={GRID_COLUMNS} gap="6" aria-busy="true">
      <Text role="status" srOnly>
        Loading dogs…
      </Text>
      {SKELETON_KEYS.map((key) => (
        <Skeleton key={key} h="64" borderRadius="md" />
      ))}
    </SimpleGrid>
  )
}

export default function DogsPage() {
  const [search, setSearch] = useSearchParams()
  const { state } = useAuth()
  const view = readListView(search)
  // A wide screen shows five dogs in a row, and a page of fifteen fills three rows of them.
  const size = useMediaQuery(VERY_WIDE) ? WIDE_DOG_PAGE_SIZE : undefined
  const dogs = useDogs({ page: view.page - 1, status: view.status, size })

  const show = (next: ListView) => setSearch(writeListView(next))
  const content = dogs.data?.content

  return (
    <Stack gap="6">
      <Flex align="end" justify="space-between" gap="4" wrap="wrap">
        <Heading as="h1" size="2xl">
          Dogs
        </Heading>
        <HStack gap="4" align="end" wrap="wrap">
          {state.status === 'authenticated' && state.user.role === 'ADMIN' && (
            <Button asChild>
              <RouterLink to="/admin/dogs/new">Add dog</RouterLink>
            </Button>
          )}
          <StatusFilter value={view.status} onChange={(status) => show({ page: 1, status })} />
        </HStack>
      </Flex>

      {dogs.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(dogs.error)} />
          <Button variant="outline" onClick={() => void dogs.refetch()}>
            Try again
          </Button>
        </Stack>
      )}

      {dogs.isPending && <DogSkeletons />}

      {content && content.length > 0 && (
        <SimpleGrid columns={GRID_COLUMNS} gap="6">
          {content.map((dog) => (
            <DogCard key={dog.id} dog={dog} />
          ))}
        </SimpleGrid>
      )}

      {content?.length === 0 && (
        <Stack gap="3" align="start">
          <Text>{emptyListMessage(view.page, view.status)}</Text>
          {view.page > 1 && (
            <Button variant="outline" onClick={() => show({ ...view, page: 1 })}>
              Go to the first page
            </Button>
          )}
        </Stack>
      )}

      <PageControls
        page={view.page}
        totalPages={dogs.data?.page.totalPages ?? 0}
        onChange={(page) => show({ ...view, page })}
      />
    </Stack>
  )
}
