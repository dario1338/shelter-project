import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteDogPhoto, uploadDogPhoto } from '../api/dogPhotos'

// The photos are part of the dog, so the dog and the list are fetched again after a change.
function useRefreshDogs() {
  const client = useQueryClient()
  return () => Promise.all([client.invalidateQueries({ queryKey: ['dogs'] }), client.invalidateQueries({ queryKey: ['dog'] })])
}

export function useUploadPhoto(dogId: number) {
  const refresh = useRefreshDogs()
  return useMutation({ mutationFn: (file: File) => uploadDogPhoto(dogId, file), onSettled: refresh })
}

export function useDeletePhoto(dogId: number) {
  const refresh = useRefreshDogs()
  return useMutation({ mutationFn: (photoId: number) => deleteDogPhoto(dogId, photoId), onSettled: refresh })
}
