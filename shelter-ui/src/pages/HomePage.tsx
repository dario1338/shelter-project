import { Box, Button, Heading, SimpleGrid, Skeleton, Stack, Text } from '@chakra-ui/react'
import { HOME_DOG_COUNT } from '../api/dogs'
import { errorMessage } from '../api/ApiError'
import { DogCard } from '../dogs/DogCard'
import { useLatestAvailableDogs } from '../dogs/useDogs'
import { ErrorAlert } from '../ui/ErrorAlert'
import { PawIcon } from '../ui/PawIcon'

// The same columns as the list of dogs.
const GRID_COLUMNS = { base: 1, sm: 2, md: 3, xl: 4, '2xl': 5 }

// The home page shows one row of dogs, whatever the width of the screen. The API sends one dog more than
// the narrowest row needs, and the ones that would start a second row are hidden: two on a phone, then
// as many as there are columns. Hidden cards are not in the tab order either.
function rowOnly(index: number) {
  const shown = 'block'
  const hidden = 'none'
  return {
    base: index < 2 ? shown : hidden,
    md: index < 3 ? shown : hidden,
    xl: index < 4 ? shown : hidden,
    '2xl': shown,
  }
}

// Fixed names, so that the placeholders have stable keys.
const SKELETON_KEYS = ['a', 'b', 'c', 'd', 'e'].slice(0, HOME_DOG_COUNT)

function LatestDogs() {
  const dogs = useLatestAvailableDogs()

  if (dogs.isPending) {
    return (
      <SimpleGrid columns={GRID_COLUMNS} gap="6" aria-busy="true">
        <Text role="status" srOnly>
          Loading dogs…
        </Text>
        {SKELETON_KEYS.map((key, index) => (
          <Skeleton key={key} h="64" borderRadius="md" display={rowOnly(index)} />
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
      {dogs.data.content.slice(0, HOME_DOG_COUNT).map((dog, index) => (
        <Box key={dog.id} display={rowOnly(index)}>
          <DogCard dog={dog} />
        </Box>
      ))}
    </SimpleGrid>
  )
}

// The welcome and the dogs share the height of the screen, so that a large screen does not show the
// text at the top and a lot of empty space below it. On a small screen the page is as high as its content.
export default function HomePage() {
  return (
    <Stack gap="10" justify="space-evenly" minH={{ base: 'auto', md: 'calc(100dvh - 9rem)' }}>
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
      </Stack>
    </Stack>
  )
}
