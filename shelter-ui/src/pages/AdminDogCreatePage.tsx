import { Heading, Link, Stack } from '@chakra-ui/react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import { emptyDogForm } from '../dogs/dogFormValues'
import { DogForm } from '../dogs/DogForm'
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
        // The care details are added on the next page, where the dog already exists.
        onSubmit={(input) => create.mutate(input, { onSuccess: (dog) => navigate(`/admin/dogs/${dog.id}/edit`) })}
      />
    </Stack>
  )
}
