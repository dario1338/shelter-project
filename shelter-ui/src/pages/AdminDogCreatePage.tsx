import { Heading, Link, Stack } from '@chakra-ui/react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import { emptyDogForm } from '../dogs/dogFormValues'
import { DogForm } from '../dogs/DogForm'
import { editPath } from '../dogs/editTab'
import { useCreateDog } from '../dogs/useDogMutations'

export default function AdminDogCreatePage() {
  const navigate = useNavigate()
  const create = useCreateDog()

  return (
    <Stack gap="6">
      <Link asChild variant="underline" alignSelf="start">
        <RouterLink to="/dogs">← All dogs</RouterLink>
      </Link>
      <Heading as="h1" size="2xl">
        Add a dog
      </Heading>
      <DogForm
        initial={emptyDogForm}
        submitLabel="Add dog"
        pending={create.isPending}
        error={create.isError ? errorMessage(create.error) : undefined}
        // The photos and care details belong to the dog, so they are added on its edit page.
        onSubmit={(input) => create.mutate(input, { onSuccess: (dog) => navigate(editPath(dog.id, 'photos')) })}
      />
    </Stack>
  )
}
