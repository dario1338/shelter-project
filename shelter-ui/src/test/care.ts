import type { Care } from '../api/care'

export function care(overrides: Partial<Care> = {}): Care {
  return {
    sterilized: true,
    microchipNumber: '900123',
    healthNotes: 'Healthy and strong.',
    internalNote: null,
    vaccinations: [{ id: 1, name: 'Rabies', vaccinatedOn: '2026-01-05', nextDueOn: '2027-01-05' }],
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  }
}

export const noCare: Care = {
  sterilized: null,
  microchipNumber: null,
  healthNotes: null,
  internalNote: null,
  vaccinations: [],
  updatedAt: null,
}
