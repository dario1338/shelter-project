import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { applyForSponsorship, cancelApplication, fetchMyApplications } from '../api/sponsorships'

export function useMyApplications(page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['sponsorships', 'me', page],
    queryFn: ({ signal }) => fetchMyApplications(page, signal),
    placeholderData: keepPreviousData,
    enabled,
  })
}

// An application changes what the lists show: the user's own list, and the dog if it was approved
// meanwhile. The cached data is therefore marked stale, and what is on the screen is fetched again.
function useRefreshAfterChange() {
  const client = useQueryClient()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['sponsorships'] }),
      client.invalidateQueries({ queryKey: ['dog'] }),
      client.invalidateQueries({ queryKey: ['dogs'] }),
    ])
}

export function useApply(dogId: number) {
  const refresh = useRefreshAfterChange()
  return useMutation({
    mutationFn: (note: string) => applyForSponsorship(dogId, note),
    onSettled: refresh,
  })
}

export function useCancelApplication() {
  const refresh = useRefreshAfterChange()
  return useMutation({
    mutationFn: (id: number) => cancelApplication(id),
    onSettled: refresh,
  })
}
