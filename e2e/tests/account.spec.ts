import { randomUUID } from 'node:crypto'
import { expect, test, type Response } from '@playwright/test'
import { openHome, signIn, signOut } from './helpers'

// Registration, a reload, sign out and sign in with the same account. It writes one user to the staging schema on
// every run (the API has no way to delete a user), so each run has its own address; example.com never delivers mail.
const FIRST_NAME = 'Playwright'

function isAuthCall(path: string) {
  return (response: Response) => response.url().endsWith(`/api/auth/${path}`) && response.request().method() === 'POST'
}

test('a session survives a reload and signing out really ends it', async ({ page }) => {
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

  // A reload drops the access token, which lives only in memory; the session comes back through the refresh cookie.
  const restored = page.waitForResponse(isAuthCall('refresh'))
  await page.reload()
  expect((await restored).status()).toBe(200)
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  // Signing out after the reload. Before the fix in PR #116 the API answered 403 here: the request that restored the
  // session dropped the CSRF token, so the browser showed the user as signed out while the refresh cookie stayed valid.
  const loggedOut = page.waitForResponse(isAuthCall('logout'))
  await signOut(page)
  expect((await loggedOut).status()).toBe(204)

  // The session really ended on the server: after another reload it cannot be restored.
  const notRestored = page.waitForResponse(isAuthCall('refresh'))
  await page.reload()
  expect((await notRestored).status()).not.toBe(200)
  await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()

  await signIn(page, email, password)
  await expect(page.getByText(FIRST_NAME, { exact: true })).toBeVisible()

  await signOut(page)
})
