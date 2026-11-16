import { expect, test } from '@playwright/test'
import { openHome } from './helpers'

// A visitor who is not signed in can look at the dogs. Read only: the test writes nothing.
test('a visitor can browse the dogs and open one', async ({ page }) => {
  await openHome(page)

  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Dogs' }).click()
  await expect(page).toHaveURL(/\/dogs/)
  await expect(page.getByRole('heading', { level: 1, name: 'Dogs' })).toBeVisible()

  // The staging schema can be empty, so both outcomes of the list are a pass: dogs, or the message that there are none.
  // The part that opens a dog runs only when there is one. Once a test creates its own dog (the admin scenario),
  // this becomes unconditional.
  const dogLinks = page.locator('a[href^="/dogs/"]')
  await expect(dogLinks.first().or(page.getByText('There are no dogs yet.'))).toBeVisible()

  if ((await dogLinks.count()) > 0) {
    const name = (await dogLinks.first().innerText()).trim()
    await dogLinks.first().click()
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()

    await page.getByRole('link', { name: /all dogs/i }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Dogs' })).toBeVisible()
  }
})
