import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  applyForSponsorship,
  cancelApplication,
  decideApplication,
  fetchApplications,
  fetchMyApplications,
  type ApplicationListParams,
  type Decision,
  type MyApplicationsParams,
} from '../api/sponsorships'

export function useMyApplications(params: MyApplicationsParams) {
  return useQuery({
    queryKey: ['sponsorships', 'me', params],
    queryFn: ({ signal }) => fetchMyApplications(params, signal),
    placeholderData: keepPreviousData,
  })
}

export function useApplications(params: ApplicationListParams) {
  return useQuery({
    queryKey: ['sponsorships', 'all', params],
    queryFn: ({ signal }) => fetchApplications(params, signal),
    placeholderData: keepPreviousData,
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

export function useDecideApplication() {
  const refresh = useRefreshAfterChange()
  return useMutation({
    mutationFn: ({ id, decision, adminNote }: { id: number; decision: Decision; adminNote: string }) =>
      decideApplication(id, decision, adminNote),
    onSettled: refresh,
  })
}
