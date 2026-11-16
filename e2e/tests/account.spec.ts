import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { openHome, signIn, signOut } from './helpers'

// Registration, sign out and sign in with the same account. It writes one user to the staging schema on every run
// (the API has no way to delete a user), so each run has its own address; example.com never delivers mail.
const FIRST_NAME = 'Playwright'

test('a new user can register, sign out and sign in again', async ({ page }) => {
  const email = `e2e-${randomUUID()}@example.com`
  const password = `E2e-${randomUUID()}`

  await openHome(page)

  // The new account is signed in right after it is created.
  await page.goto('/register')
  await page.getByLabel('First name').fill(FIRST_NAME)
  await page.getByLabel('Last name').fill('Test')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.locator('form').getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  await expect(page.getByText(FIRST_NAME, { exact: true })).toBeVisible()

  await signOut(page)

  await signIn(page, email, password)
  await expect(page.getByText(FIRST_NAME, { exact: true })).toBeVisible()

  await signOut(page)
})
