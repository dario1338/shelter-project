import { Button, Heading, HStack, Link, Separator, Skeleton, Stack, Text } from '@chakra-ui/react'
import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/ApiError'
import type { Dog } from '../api/dogs'
import { CareForm } from '../care/CareForm'
import { useCare, useSaveCare } from '../care/useCare'
import { dogToForm } from '../dogs/dogFormValues'
import { DogForm } from '../dogs/DogForm'
import { parseDogId } from '../dogs/format'
import { useDeleteDog, useUpdateDog } from '../dogs/useDogMutations'
import { useDog } from '../dogs/useDogs'
import { ErrorAlert } from '../ui/ErrorAlert'

function CareEditor({ dogId }: Readonly<{ dogId: number }>) {
  const care = useCare(dogId)
  const save = useSaveCare(dogId)

  if (care.isPending) {
    return <Skeleton h="40" />
  }
  // The form is not shown without the current values, because saving would replace them with an empty set.
  if (care.isError) {
    return (
      <Stack gap="3" align="start">
        <ErrorAlert message={errorMessage(care.error)} />
        <Button variant="outline" onClick={() => void care.refetch()}>
          Try again
        </Button>
      </Stack>
    )
  }
  return (
    <CareForm
      care={care.data}
      pending={save.isPending}
      saved={save.isSuccess}
      error={save.isError ? errorMessage(save.error) : undefined}
      onSubmit={(input) => save.mutate(input)}
    />
  )
}

function DeleteDog({ dog }: Readonly<{ dog: Dog }>) {
  const navigate = useNavigate()
  const remove = useDeleteDog(dog.id)
  const [confirming, setConfirming] = useState(false)

  return (
    <Stack gap="3" align="start">
      <Heading as="h2" size="lg">
        Delete this dog
      </Heading>
      {remove.isError && <ErrorAlert message={errorMessage(remove.error)} />}
      {confirming ? (
        <Stack gap="3" align="start">
          <Text>
            Delete {dog.name}? This also removes its applications and care details, and it cannot be undone.
          </Text>
          <HStack gap="2">
            <Button colorPalette="red" loading={remove.isPending} onClick={() => remove.mutate(undefined, { onSuccess: () => navigate('/dogs') })}>
              Yes, delete
            </Button>
            <Button variant="outline" disabled={remove.isPending} onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </HStack>
        </Stack>
      ) : (
        <Button colorPalette="red" variant="outline" onClick={() => setConfirming(true)}>
          Delete dog
        </Button>
      )}
    </Stack>
  )
}

function EditDog({ dog }: Readonly<{ dog: Dog }>) {
  const update = useUpdateDog(dog.id)

  return (
    <Stack gap="8">
      <Stack gap="4">
        <Heading as="h2" size="lg">
          Dog
        </Heading>
        <DogForm
          initial={dogToForm(dog)}
          submitLabel="Save dog"
          pending={update.isPending}
          error={update.isError ? errorMessage(update.error) : undefined}
          onSubmit={(input) => update.mutate(input)}
        />
        <Text role="status" aria-live="polite">
          {update.isSuccess && !update.isPending ? 'Dog saved.' : ''}
        </Text>
      </Stack>
      <Separator />
      <CareEditor dogId={dog.id} />
      <Separator />
      <DeleteDog dog={dog} />
    </Stack>
  )
}

export default function AdminDogEditPage() {
  const id = parseDogId(useParams().id)
  const dog = useDog(id)

  if (id === null || (dog.error instanceof ApiError && dog.error.status === 404)) {
    return (
      <Stack gap="4" align="start">
        <Heading as="h1" size="2xl">
          Dog not found
        </Heading>
        <Link asChild variant="underline">
          <RouterLink to="/dogs">Back to all dogs</RouterLink>
        </Link>
      </Stack>
    )
  }

  return (
    <Stack gap="6">
      <Link asChild variant="underline" alignSelf="start">
        <RouterLink to={`/dogs/${id}`}>← Back to the dog</RouterLink>
      </Link>
      <Heading as="h1" size="2xl">
        {dog.data ? `Edit ${dog.data.name}` : 'Edit dog'}
      </Heading>
      {dog.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(dog.error)} />
          <Button variant="outline" onClick={() => void dog.refetch()}>
            Try again
          </Button>
        </Stack>
      )}
      {dog.isPending && <Skeleton h="64" />}
      {dog.data && <EditDog dog={dog.data} />}
    </Stack>
  )
}
