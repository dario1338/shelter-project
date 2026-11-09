import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchDog, fetchDogs, fetchLatestAvailableDogs, type DogListParams } from '../api/dogs'

export function useDogs(params: DogListParams) {
  return useQuery({
    queryKey: ['dogs', params],
    queryFn: ({ signal }) => fetchDogs(params, signal),
    // The previous page stays on the screen while the next one loads, so the list does not flash.
    placeholderData: keepPreviousData,
  })
}

export function useDog(id: number | null) {
  return useQuery({
    queryKey: ['dog', id],
    queryFn: ({ signal }) => fetchDog(id as number, signal),
    enabled: id !== null,
  })
}

// Under 'dogs', so that a change of a dog fetches the home page's list again as well.
export function useLatestAvailableDogs() {
  return useQuery({
    queryKey: ['dogs', 'latest'],
    queryFn: ({ signal }) => fetchLatestAvailableDogs(signal),
  })
}
