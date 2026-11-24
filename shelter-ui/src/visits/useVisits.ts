import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { bookVisit, cancelVisit, fetchMyVisits } from '../api/visits'

export function useMyVisits(page: number) {
  return useQuery({
    queryKey: ['visits', 'me', page],
    queryFn: ({ signal }) => fetchMyVisits(page, signal),
    placeholderData: keepPreviousData,
  })
}

export function useBookVisit(dogId: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ date, hour }: { date: string; hour: number }) => bookVisit(dogId, date, hour),
    onSettled: () => client.invalidateQueries({ queryKey: ['visits'] }),
  })
}

export function useCancelVisit() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => cancelVisit(id),
    onSettled: () => client.invalidateQueries({ queryKey: ['visits'] }),
  })
}
