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
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    // A fixed size, so that the page always uses the same layout (the wide header).
    viewport: { width: 1280, height: 720 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
