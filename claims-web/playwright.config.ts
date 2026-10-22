import { defineConfig, devices } from '@playwright/test';

/** E2E_BASE_URL=http://localhost:8080 runs the suite against the Docker stack (nginx) instead of the dev server. */
const baseURL = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';

/**
 * End-to-end tests against the real backend through the dev-server proxy.
 * Needs the three services (and Kafka) running and DEMO_PASSWORD set to their APP_SECURITY_DEMO_PASSWORD.
 * The dev server is started automatically unless one is already running on :4200.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    // full workflows, theme and accessibility checks at desktop size
    {
      name: 'desktop', testIgnore: /mobile\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    // the same app on a phone: navigation drawer, wizard, claim page, reports, no horizontal scrolling
    { name: 'mobile', testMatch: /mobile\.spec\.ts/, use: { ...devices['Pixel 7'] } },
  ],
  webServer: process.env['E2E_BASE_URL'] ? undefined : {
    command: 'npm start',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
