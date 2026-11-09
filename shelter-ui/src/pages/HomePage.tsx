import { Button, Heading, SimpleGrid, Skeleton, Stack, Text } from '@chakra-ui/react'
import { Link as RouterLink } from 'react-router-dom'
import { HOME_DOG_COUNT } from '../api/dogs'
import { errorMessage } from '../api/ApiError'
import { DogCard } from '../dogs/DogCard'
import { useLatestAvailableDogs } from '../dogs/useDogs'
import { ErrorAlert } from '../ui/ErrorAlert'
import { PawIcon } from '../ui/PawIcon'

const GRID_COLUMNS = { base: 1, sm: 2, md: 3 }

// Fixed names, so that the placeholders have stable keys.
const SKELETON_KEYS = ['a', 'b', 'c', 'd', 'e', 'f'].slice(0, HOME_DOG_COUNT)

function LatestDogs() {
  const dogs = useLatestAvailableDogs()

  if (dogs.isPending) {
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
  if (dogs.isError) {
    return (
      <Stack gap="3" align="start">
        <ErrorAlert message={errorMessage(dogs.error)} />
        <Button variant="outline" onClick={() => void dogs.refetch()}>
          Try again
        </Button>
      </Stack>
    )
  }
  if (dogs.data.content.length === 0) {
    return <Text color="fg.muted">No dog is looking for a sponsor right now. Please come back soon.</Text>
  }
  return (
    <SimpleGrid columns={GRID_COLUMNS} gap="6">
      {dogs.data.content.map((dog) => (
        <DogCard key={dog.id} dog={dog} />
      ))}
    </SimpleGrid>
  )
}

export default function HomePage() {
  return (
    <Stack gap="10">
      <Stack gap="3" align="center" textAlign="center">
        <Heading as="h1" size="4xl" display="flex" flexDirection={{ base: 'column', sm: 'row' }} alignItems="center" justifyContent="center" gap="3">
          <PawIcon size={40} />
          <span>
            Welcome to{' '}
            <Text as="span" color="purple.fg">
              Shelter
            </Text>
          </span>
        </Heading>
        <Text fontSize="lg" color="fg.muted" maxW="2xl">
          Help dogs find their forever home. Become a sponsor and follow your companion's progress.
        </Text>
      </Stack>

      <Stack gap="6">
        <Heading as="h2" size="xl">
          Meet our dogs
        </Heading>
        <LatestDogs />
        <Button asChild alignSelf="start">
          <RouterLink to="/dogs">See all dogs</RouterLink>
        </Button>
      </Stack>
    </Stack>
  )
}
