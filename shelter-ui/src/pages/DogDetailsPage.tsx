import { Box, Button, Heading, Link, SimpleGrid, Skeleton, Stack, Text } from '@chakra-ui/react'
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/ApiError'
import { DogImage } from '../dogs/DogImage'
import { dogAge, parseDogId } from '../dogs/format'
import { listSearchFrom } from '../dogs/listSearch'
import { SponsorshipSection } from '../sponsorships/ApplyForm'
import { StatusBadge } from '../dogs/StatusBadge'
import { useDog } from '../dogs/useDogs'
import { ErrorAlert } from '../ui/ErrorAlert'

function NotFound({ backTo }: Readonly<{ backTo: string }>) {
  return (
    <Stack gap="4" align="start">
      <Heading as="h1" size="2xl">
        Dog not found
      </Heading>
      <Text>There is no dog with this address. It may have been removed.</Text>
      <Link asChild variant="underline">
        <RouterLink to={backTo}>Back to all dogs</RouterLink>
      </Link>
    </Stack>
  )
}

export default function DogDetailsPage() {
  const id = parseDogId(useParams().id)
  const backTo = `/dogs${listSearchFrom(useLocation().state)}`
  const dog = useDog(id)

  if (id === null || (dog.error instanceof ApiError && dog.error.status === 404)) {
    return <NotFound backTo={backTo} />
  }

  return (
    <Stack gap="6">
      <Link asChild variant="underline" alignSelf="start">
        <RouterLink to={backTo}>← All dogs</RouterLink>
      </Link>

      {dog.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(dog.error)} />
          <Button variant="outline" onClick={() => void dog.refetch()}>
            Try again
          </Button>
        </Stack>
      )}

      {dog.isPending && (
        <SimpleGrid columns={{ base: 1, md: 2 }} gap="8" aria-busy="true">
          <Text role="status" srOnly>
            Loading the dog…
          </Text>
          <Skeleton h="80" borderRadius="md" />
          <Stack gap="4">
            <Skeleton h="10" />
            <Skeleton h="6" />
            <Skeleton h="24" />
          </Stack>
        </SimpleGrid>
      )}

      {dog.data && (
        <SimpleGrid columns={{ base: 1, md: 2 }} gap="8">
          <DogImage name={dog.data.name} h="80" borderRadius="md" />
          <Stack gap="4" align="start">
            <Heading as="h1" size="2xl">
              {dog.data.name}
            </Heading>
            <StatusBadge status={dog.data.status} />
            <Box as="dl" display="grid" gridTemplateColumns="auto 1fr" columnGap="4" rowGap="1">
              {dog.data.breed && (
                <>
                  <Text as="dt" color="fg.muted">
                    Breed
                  </Text>
                  <Text as="dd">{dog.data.breed}</Text>
                </>
              )}
              {dog.data.birthYear !== null && (
                <>
                  <Text as="dt" color="fg.muted">
                    Age
                  </Text>
                  <Text as="dd">
                    {dogAge(dog.data.birthYear)} (born {dog.data.birthYear})
                  </Text>
                </>
              )}
            </Box>
            {dog.data.description && <Text whiteSpace="pre-wrap">{dog.data.description}</Text>}
            <SponsorshipSection dog={dog.data} />
          </Stack>
        </SimpleGrid>
      )}
    </Stack>
  )
}
