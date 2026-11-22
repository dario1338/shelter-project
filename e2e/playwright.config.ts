import { defineConfig } from '@playwright/test'

// The address of the application under test (the staging proxy), given by the pipeline.
// There is no default on purpose: a test run that silently went to another address would prove nothing.
const baseURL = process.env.E2E_BASE_URL
if (!baseURL) {
  throw new Error('E2E_BASE_URL is not set')
}

export default defineConfig({
  testDir: './tests',
  // One browser at a time: the staging services are small free instances, and the tests share one database schema.
  workers: 1,
  fullyParallel: false,
  // The first request can wake a sleeping instance (up to two or three minutes), so the limits are generous.
  timeout: 240_000,
  expect: { timeout: 15_000 },
  // One repeat of a failed test, reported as "flaky" when it passes the second time, so it is not hidden.
  retries: 1,
  // results.json is read by the pipeline: a test that passed only on the retry makes the build UNSTABLE.
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }], ['json', { outputFile: 'results.json' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    // A fixed size, so that the page always uses the same layout (the wide header).
    viewport: { width: 1280, height: 720 },
    // Without these two Playwright waits for an action or a page for as long as the whole test may last (4 minutes),
    // so a missing button would look like a hang. With them it fails in 30 seconds and says which step it was.
    actionTimeout: 30_000,
    navigationTimeout: 90_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
