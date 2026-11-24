import { randomInt, randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'
import { openHome } from './helpers'

const DAY_MS = 24 * 60 * 60 * 1000

// A time of its own for every run: a day between one and twelve months ahead and an hour from 10 to 15. A fixed time
// stays booked on staging after the run, and the next run finds it taken (a time is booked only once per dog).
function uniqueTime(): { date: string; hour: number } {
  const date = new Date(Date.now() + randomInt(30, 365) * DAY_MS).toISOString().slice(0, 10)
  return { date, hour: randomInt(10, 16) }
}

async function cancelTheVisit(page: Page): Promise<void> {
  await page.goto('/visits')
  await page.getByRole('button', { name: 'Cancel visit' }).click()
  await page.getByRole('button', { name: 'Yes, cancel it' }).click()
  await expect(page.getByText('Cancelled', { exact: true })).toBeVisible()
}

// A new user books a visit to the first dog in the list, finds it among their visits and cancels it. The test removes
// what it made: the visit is cancelled also when an assertion fails after the booking, so the time is free again.
test('a user books a visit to a dog, sees it among their visits and cancels it', async ({ page }) => {
  const email = `e2e-${randomUUID()}@example.com`
  const password = `E2e-${randomUUID()}`
  const { date, hour } = uniqueTime()
  let booked = false
  let cancelled = false

  await openHome(page)

  await page.goto('/register')
  await page.getByLabel('First name').fill('Playwright')
  await page.getByLabel('Last name').fill('Visit')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.locator('form').getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  try {
    await page.goto('/dogs')
    const firstDog = page.locator('a[href^="/dogs/"]').first()
    const name = (await firstDog.innerText()).trim()
    await firstDog.click()
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()

    await page.getByLabel('Day').fill(date)
    await page.getByLabel('Time').selectOption(String(hour))
    await page.getByRole('button', { name: 'Book a visit' }).click()
    await expect(page.getByText(/Your visit is booked for/)).toBeVisible()
    booked = true

    await page.getByRole('link', { name: 'My visits' }).first().click()
    await expect(page.getByRole('heading', { level: 1, name: 'My visits' })).toBeVisible()
    await expect(page.getByRole('link', { name })).toBeVisible()

    await cancelTheVisit(page)
    cancelled = true
  } finally {
    // Cleanup that must not hide the real failure: it tries once and ignores its own errors.
    if (booked && !cancelled) {
      try {
        await cancelTheVisit(page)
      } catch {
        // The visit stays booked on staging; the test has already failed for another reason.
      }
    }
  }
})
