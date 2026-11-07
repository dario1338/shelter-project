import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCare, saveCare, type CareInput } from '../api/care'

export function useCare(dogId: number) {
  return useQuery({
    queryKey: ['care', dogId],
    queryFn: ({ signal }) => fetchCare(dogId, signal),
  })
}

export function useSaveCare(dogId: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CareInput) => saveCare(dogId, input),
    onSuccess: (care) => client.setQueryData(['care', dogId], care),
  })
}
