// The limits of the API. The API checks them again and has the last word; checking here only spares
// the administrator an upload that is bound to fail.
export const MAX_PHOTOS = 6
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024

const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png'])

function photos(count: number): string {
  return count === 1 ? '1 more photo' : `${count} more photos`
}

// Returns what is wrong with the chosen files, or null when they can all be uploaded.
export function checkPhotoFiles(files: File[], existing: number): string | null {
  const room = MAX_PHOTOS - existing
  if (files.length > room) {
    return room > 0
      ? `Only ${photos(room)} can be added (at most ${MAX_PHOTOS} per dog).`
      : `A dog can have at most ${MAX_PHOTOS} photos. Delete one to add another.`
  }
  const wrongType = files.find((file) => !ALLOWED_TYPES.has(file.type))
  if (wrongType) {
    return `${wrongType.name} is not a JPEG or PNG image.`
  }
  const tooBig = files.find((file) => file.size > MAX_PHOTO_BYTES)
  if (tooBig) {
    return `${tooBig.name} is too large (at most 5 MB).`
  }
  return null
}
