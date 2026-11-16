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
