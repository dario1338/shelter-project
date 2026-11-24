import type { Visit, VisitPage } from '../api/visits'

export function visit(overrides: Partial<Visit> = {}): Visit {
  return {
    id: 20,
    dogId: 7,
    dogName: 'Rex',
    visitAt: '2026-12-01T09:00:00Z',
    status: 'BOOKED',
    createdAt: '2026-11-24T12:00:00Z',
    ...overrides,
  }
}

export function visitPage(content: Visit[]): VisitPage {
  return { content, page: { size: 10, number: 0, totalElements: content.length, totalPages: content.length > 0 ? 1 : 0 } }
}
