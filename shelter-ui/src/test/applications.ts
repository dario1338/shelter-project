import type { Application, ApplicationPage } from '../api/sponsorships'

export function application(overrides: Partial<Application> = {}): Application {
  return {
    id: 1,
    dogId: 7,
    dogName: 'Rex',
    applicantEmail: 'ana@example.com',
    applicantName: 'Ana Anić',
    status: 'PENDING',
    userNote: null,
    adminNote: null,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  }
}

export function applicationPage(content: Application[], page: { number?: number; totalPages?: number } = {}): ApplicationPage {
  return {
    content,
    page: { size: 10, number: page.number ?? 0, totalElements: content.length, totalPages: page.totalPages ?? 1 },
  }
}
