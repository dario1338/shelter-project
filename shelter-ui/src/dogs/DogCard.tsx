import { Card, Link, Text } from '@chakra-ui/react'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import type { Dog } from '../api/dogs'
import { DogImage } from './DogImage'
import { dogAge } from './format'
import { StatusBadge } from './StatusBadge'

// The whole card is one link (the overlay on the name), so there is a single tab stop per dog.
export function DogCard({ dog }: Readonly<{ dog: Dog }>) {
  // The filter and the page of the list travel with the link, so "back" on the details page can
  // return to the same place.
  const { search } = useLocation()
  const details = [dog.breed, dogAge(dog.birthYear)].filter(Boolean).join(' · ')
  return (
    <Card.Root position="relative" overflow="hidden" _hover={{ shadow: 'md' }}>
      <DogImage name={dog.name} src={dog.primaryImageUrl} h="40" />
      <Card.Body gap="2">
        <Card.Title>
          <Link
            asChild
            variant="plain"
            _after={{ content: '""', position: 'absolute', inset: '0' }}
          >
            <RouterLink to={`/dogs/${dog.id}`} state={{ listSearch: search }}>{dog.name}</RouterLink>
          </Link>
        </Card.Title>
        {details && (
          <Text color="fg.muted" fontSize="sm">
            {details}
          </Text>
        )}
        <div>
          <StatusBadge status={dog.status} />
        </div>
      </Card.Body>
    </Card.Root>
  )
}
