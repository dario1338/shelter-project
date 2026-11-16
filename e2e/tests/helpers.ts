import { expect, type Page } from '@playwright/test'

// A sleeping free instance needs up to two minutes to wake up (the application shows its own waiting screen
// meanwhile), so the first page is given more time than the others.
export const WAKE_UP_TIMEOUT = 180_000

// Opens the home page and waits until the application is really there: past the waiting screen, with its heading.
export async function openHome(page: Page): Promise<void> {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: /welcome to\s+shelter/i })).toBeVisible({
    timeout: WAKE_UP_TIMEOUT,
  })
}

// The administrator of the staging application. The pipeline gives the account in the environment; without it
// the test fails, it is not skipped, so a missing credential cannot pass for a green run.
export function adminAccount(): { email: string; password: string } {
  const email = process.env.E2E_ADMIN_EMAIL
  const password = process.env.E2E_ADMIN_PASSWORD
  if (!email || !password) {
    throw new Error('E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD are not set')
  }
  return { email, password }
}

export async function signIn(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()
}

// Deletes a dog on its edit page, with the confirmation. The application then returns to the list of dogs.
export async function deleteDog(page: Page, id: string): Promise<void> {
  await page.goto(`/admin/dogs/${id}/edit`)
  await page.getByRole('button', { name: 'Delete dog' }).click()
  await page.getByRole('button', { name: 'Yes, delete' }).click()
  await expect(page).toHaveURL(/\/dogs$/)
}
