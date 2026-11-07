import type { CurrentUser } from '../auth/currentUser'

export const ana: CurrentUser = {
  email: 'ana@example.com',
  firstName: 'Ana',
  lastName: 'Anić',
  role: 'USER',
}

export const admin: CurrentUser = {
  email: 'admin@example.com',
  firstName: 'Admin',
  lastName: 'Adminić',
  role: 'ADMIN',
}
