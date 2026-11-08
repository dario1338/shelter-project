import { describe, expect, it } from 'vitest'
import { checkPhotoFiles, MAX_PHOTO_BYTES } from './photoFiles'

function file(name: string, type = 'image/jpeg', size = 100): File {
  const result = new File(['x'], name, { type })
  Object.defineProperty(result, 'size', { value: size })
  return result
}

describe('checkPhotoFiles', () => {
  it('accepts JPEG and PNG files within the limits', () => {
    expect(checkPhotoFiles([file('a.jpg'), file('b.png', 'image/png', MAX_PHOTO_BYTES)], 0)).toBeNull()
  })

  it('accepts nothing at all without complaint', () => {
    expect(checkPhotoFiles([], 6)).toBeNull()
  })

  it.each([
    ['text/plain'],
    ['image/gif'],
    ['image/heic'],
    [''],
  ])('refuses a file of type "%s"', (type) => {
    expect(checkPhotoFiles([file('notes.txt', type)], 0)).toBe('notes.txt is not a JPEG or PNG image.')
  })

  it('refuses a file over 5 MB', () => {
    expect(checkPhotoFiles([file('big.jpg', 'image/jpeg', MAX_PHOTO_BYTES + 1)], 0)).toBe('big.jpg is too large (at most 5 MB).')
  })

  it('says how many more photos fit', () => {
    expect(checkPhotoFiles([file('a.jpg'), file('b.jpg')], 5)).toBe('Only 1 more photo can be added (at most 6 per dog).')
    expect(checkPhotoFiles([file('a.jpg'), file('b.jpg'), file('c.jpg')], 4)).toBe('Only 2 more photos can be added (at most 6 per dog).')
  })

  it('says so when the dog has the most photos already', () => {
    expect(checkPhotoFiles([file('a.jpg')], 6)).toBe('A dog can have at most 6 photos. Delete one to add another.')
  })
})
