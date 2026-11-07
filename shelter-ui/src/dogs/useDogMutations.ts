import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createDog, deleteDog, updateDog, type DogInput } from '../api/dogs'

// A change of a dog is visible in the list and on its page, so both are fetched again.
function useRefreshDogs() {
  const client = useQueryClient()
  return () => Promise.all([client.invalidateQueries({ queryKey: ['dogs'] }), client.invalidateQueries({ queryKey: ['dog'] })])
}

export function useCreateDog() {
  const refresh = useRefreshDogs()
  return useMutation({ mutationFn: (input: DogInput) => createDog(input), onSettled: refresh })
}

export function useUpdateDog(id: number) {
  const refresh = useRefreshDogs()
  return useMutation({ mutationFn: (input: DogInput) => updateDog(id, input), onSettled: refresh })
}

export function useDeleteDog(id: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => deleteDog(id),
    onSuccess: async () => {
      // The dog is gone with its applications and care details.
      client.removeQueries({ queryKey: ['dog', id] })
      client.removeQueries({ queryKey: ['care', id] })
      await Promise.all([
        client.invalidateQueries({ queryKey: ['dogs'] }),
        client.invalidateQueries({ queryKey: ['sponsorships'] }),
      ])
    },
  })
}
