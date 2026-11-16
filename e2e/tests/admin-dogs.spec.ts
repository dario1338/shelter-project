import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { adminAccount, deleteDog, openHome, signIn, signOut } from './helpers'

// The administrator adds a dog, a visitor who is not signed in sees it, and the administrator deletes it.
// It writes to the staging schema only, and removes what it made, also when an assertion fails.
test('an administrator adds a dog, a visitor sees it, and the administrator deletes it', async ({ page }) => {
  const { email, password } = adminAccount()
  const name = `E2E dog ${randomUUID().slice(0, 8)}`
  let id: string | undefined
  let deleted = false

  try {
    await openHome(page)
    await signIn(page, email, password)

    // The list has a button for adding a dog only for the administrator.
    await page.goto('/dogs')
    await page.getByRole('link', { name: 'Add dog' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Add a dog' })).toBeVisible()

    await page.getByLabel('Name').fill(name)
    await page.getByLabel('Breed').fill('Test breed')
    await page.getByLabel('Year of birth').fill('2020')
    await page.getByLabel('Description').fill('Made by the end-to-end test; it is deleted at the end of the test.')
    await page.locator('form').getByRole('button', { name: 'Add dog' }).click()

    // After saving, the application opens the edit page of the new dog (the address holds its number).
    await expect(page).toHaveURL(/\/admin\/dogs\/\d+\/edit/)
    id = /\/admin\/dogs\/(\d+)\/edit/.exec(page.url())?.[1]
    expect(id).toBeDefined()

    // A visitor who is not signed in opens the page of the dog.
    await signOut(page)
    await page.goto(`/dogs/${id}`)
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Edit dog' })).toHaveCount(0)

    // The administrator deletes the dog, and its page is gone.
    await signIn(page, email, password)
    await deleteDog(page, id!)
    deleted = true
    await page.goto(`/dogs/${id}`)
    await expect(page.getByText('There is no dog with this address.')).toBeVisible()
  } finally {
    // Cleanup that must not hide the real failure: it tries once and ignores its own errors.
    if (id && !deleted) {
      try {
        if ((await page.getByRole('button', { name: 'Sign out' }).count()) === 0) {
          await signIn(page, email, password)
        }
        await deleteDog(page, id)
      } catch {
        // The dog stays in staging; the test has already failed for another reason.
      }
    }
  }
})
