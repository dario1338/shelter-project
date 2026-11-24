import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { openHome } from './helpers'

// A new user books a visit to the first dog in the list and finds it among their visits.
test('a user books a visit to a dog and sees it among their visits', async ({ page }) => {
  const email = `e2e-${randomUUID()}@example.com`
  const password = `E2e-${randomUUID()}`

  await openHome(page)

  await page.goto('/register')
  await page.getByLabel('First name').fill('Playwright')
  await page.getByLabel('Last name').fill('Visit')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.locator('form').getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  await page.goto('/dogs')
  const firstDog = page.locator('a[href^="/dogs/"]').first()
  const name = (await firstDog.innerText()).trim()
  await firstDog.click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()

  await page.getByLabel('Day').fill('2027-06-01')
  await page.getByLabel('Time').selectOption('10')
  await page.getByRole('button', { name: 'Book a visit' }).click()
  await expect(page.getByText(/Your visit is booked for/)).toBeVisible()

  await page.getByRole('link', { name: 'My visits' }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: 'My visits' })).toBeVisible()
  await expect(page.getByRole('link', { name })).toBeVisible()
})
