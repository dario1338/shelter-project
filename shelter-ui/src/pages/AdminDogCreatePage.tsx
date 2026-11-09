import { Heading, Link, Stack, Tabs, Text } from '@chakra-ui/react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import { emptyDogForm } from '../dogs/dogFormValues'
import { DogForm } from '../dogs/DogForm'
import { DogTabList } from '../dogs/DogTabList'
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
      <Tabs.Root value="dog">
        <DogTabList disabled />
        <Tabs.Content value="dog" pt="6">
          <Stack gap="4">
            <Text color="fg.muted">Photos and care details can be added as soon as the dog is saved.</Text>
            <DogForm
              initial={emptyDogForm}
              submitLabel="Add dog"
              pending={create.isPending}
              error={create.isError ? errorMessage(create.error) : undefined}
              // The photos are added on the next page, where the dog already exists.
              onSubmit={(input) => create.mutate(input, { onSuccess: (dog) => navigate(editPath(dog.id, 'photos')) })}
            />
          </Stack>
        </Tabs.Content>
      </Tabs.Root>
    </Stack>
  )
}
