import { describe, expect, it } from 'vitest'
import { editPath, tabFrom } from './editTab'

describe('tabFrom', () => {
  it.each(['dog', 'photos', 'care'])('knows the tab "%s"', (tab) => {
    expect(tabFrom(tab)).toBe(tab)
  })

  it.each([null, '', 'Photos', 'other', 'dog,care'])('opens the first tab for %j', (value) => {
    expect(tabFrom(value)).toBe('dog')
  })
})

describe('editPath', () => {
  it('has no tab for the first part', () => {
    expect(editPath(4)).toBe('/admin/dogs/4/edit')
    expect(editPath(4, 'dog')).toBe('/admin/dogs/4/edit')
  })

  it('names the tab for the other parts', () => {
    expect(editPath(4, 'photos')).toBe('/admin/dogs/4/edit?tab=photos')
    expect(editPath(4, 'care')).toBe('/admin/dogs/4/edit?tab=care')
  })
})
