import { Button, HStack, Image, Stack } from '@chakra-ui/react'
import { useState } from 'react'
import type { Dog } from '../api/dogs'
import { DogImage } from './DogImage'

// The photo of the dog on its page: the chosen photo large, and a row of small ones to choose from
// when there are several. The first photo is shown until another one is chosen.
export function DogGallery({ dog }: Readonly<{ dog: Dog }>) {
  const [chosenId, setChosenId] = useState<number | null>(null)
  const photos = dog.images
  const shown = photos.find((photo) => photo.id === chosenId) ?? photos[0]

  return (
    <Stack gap="3">
      <DogImage name={dog.name} src={shown?.url} h="80" borderRadius="md" />
      {photos.length > 1 && (
        <HStack gap="2" wrap="wrap">
          {photos.map((photo, index) => (
            <Button
              key={photo.id}
              variant="plain"
              p="0"
              h="16"
              w="16"
              overflow="hidden"
              borderWidth="2px"
              borderColor={photo.id === shown?.id ? 'fg' : 'border'}
              aria-label={`Show photo ${index + 1} of ${photos.length}`}
              aria-pressed={photo.id === shown?.id}
              onClick={() => setChosenId(photo.id)}
            >
              <Image src={photo.url} alt="" w="full" h="full" objectFit="cover" />
            </Button>
          ))}
        </HStack>
      )}
    </Stack>
  )
}
