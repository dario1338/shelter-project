import type { Dog, DogPage } from '../api/dogs'

export function dog(overrides: Partial<Dog> = {}): Dog {
  return {
    id: 1,
    name: 'Rex',
    breed: 'Labrador',
    birthYear: 2020,
    description: 'A calm dog who likes long walks.',
    status: 'AVAILABLE',
    ...overrides,
  }
}

export function dogPage(content: Dog[], page: { number?: number; totalPages?: number } = {}): DogPage {
  return {
    content,
    page: {
      size: 12,
      number: page.number ?? 0,
      totalElements: content.length,
      totalPages: page.totalPages ?? 1,
    },
  }
}
