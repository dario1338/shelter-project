import { Box, Button, Field, HStack, Input, SimpleGrid, Stack, Text } from '@chakra-ui/react'
import { type ChangeEvent, useState } from 'react'
import { errorMessage } from '../api/ApiError'
import type { Dog } from '../api/dogs'
import { ErrorAlert } from '../ui/ErrorAlert'
import { DogImage } from './DogImage'
import { checkPhotoFiles, MAX_PHOTOS } from './photoFiles'
import { useDeletePhoto, useUploadPhoto } from './usePhotoMutations'

function photosAdded(count: number): string {
  return count === 1 ? '1 photo added.' : `${count} photos added.`
}

// What the administrator sees on the edit page: the photos of the dog with a way to remove each,
// and a way to add more. Photos are shown in the order they were added, and the first is the main one.
export function PhotoEditor({ dog }: Readonly<{ dog: Dog }>) {
  const upload = useUploadPhoto(dog.id)
  const remove = useDeletePhoto(dog.id)
  const [problem, setProblem] = useState<string>()
  const [status, setStatus] = useState<string>()
  const [confirmingId, setConfirmingId] = useState<number | null>(null)
  const full = dog.images.length >= MAX_PHOTOS

  async function addPhotos(event: ChangeEvent<HTMLInputElement>) {
    // Copied first, because clearing the input empties the list; it is cleared so that the same file
    // can be chosen again.
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    setProblem(undefined)
    setStatus(undefined)
    if (files.length === 0) {
      return
    }
    const invalid = checkPhotoFiles(files, dog.images.length)
    if (invalid) {
      setProblem(invalid)
      return
    }

    // One after the other: the order of the photos is the order of the uploads.
    let added = 0
    try {
      for (const file of files) {
        setStatus(`Uploading ${added + 1} of ${files.length}…`)
        await upload.mutateAsync(file)
        added++
      }
      setStatus(photosAdded(added))
    } catch (error) {
      setStatus(undefined)
      const done = added > 0 ? `${photosAdded(added)} ` : ''
      setProblem(`${done}${errorMessage(error)}`)
    }
  }

  function removePhoto(photoId: number) {
    remove.mutate(photoId, { onSettled: () => setConfirmingId(null) })
  }

  return (
    <Stack gap="4">
      {dog.images.length === 0 ? (
        <Text color="fg.muted">This dog has no photos yet.</Text>
      ) : (
        <SimpleGrid columns={{ base: 2, md: 3 }} gap="4">
          {dog.images.map((photo, index) => (
            <Stack key={photo.id} gap="2">
              <DogImage name={dog.name} src={photo.url} alt={`Photo ${index + 1} of ${dog.name}`} h="32" borderRadius="md" />
              <Text fontSize="sm" color="fg.muted">
                {index === 0 ? 'Main photo' : `Photo ${index + 1}`}
              </Text>
              {confirmingId === photo.id ? (
                <Box>
                  <Text fontSize="sm">Delete this photo?</Text>
                  <HStack gap="2" mt="1">
                    <Button size="sm" colorPalette="red" loading={remove.isPending} onClick={() => removePhoto(photo.id)}>
                      Yes, delete
                    </Button>
                    <Button size="sm" variant="outline" disabled={remove.isPending} onClick={() => setConfirmingId(null)}>
                      Keep it
                    </Button>
                  </HStack>
                </Box>
              ) : (
                <Button
                  size="sm"
                  variant="outline"
                  alignSelf="start"
                  aria-label={`Delete photo ${index + 1}`}
                  onClick={() => {
                    remove.reset()
                    setConfirmingId(photo.id)
                  }}
                >
                  Delete
                </Button>
              )}
            </Stack>
          ))}
        </SimpleGrid>
      )}
      {remove.isError && <ErrorAlert message={errorMessage(remove.error)} />}

      {full ? (
        <Text color="fg.muted">This dog has the most photos it can have ({MAX_PHOTOS}). Delete one to add another.</Text>
      ) : (
        <Field.Root>
          <Field.Label>Add photos</Field.Label>
          <Input
            type="file"
            accept="image/jpeg,image/png"
            multiple
            disabled={upload.isPending}
            onChange={(event) => void addPhotos(event)}
          />
          <Field.HelperText>JPEG or PNG, up to 5 MB each, at most {MAX_PHOTOS} per dog.</Field.HelperText>
        </Field.Root>
      )}
      {problem && <ErrorAlert message={problem} />}
      <Text role="status" aria-live="polite">
        {status ?? ''}
      </Text>
    </Stack>
  )
}
