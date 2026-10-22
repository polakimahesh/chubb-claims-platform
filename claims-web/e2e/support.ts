import { APIRequestContext, expect, Page, request } from '@playwright/test';

export const PASSWORD = process.env['DEMO_PASSWORD'] ?? '';
if (!PASSWORD) throw new Error('Set DEMO_PASSWORD to the backend APP_SECURITY_DEMO_PASSWORD');

export const PDF = { name: 'police-report.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n% e2e test document\n') };

export async function signIn(page: Page, username: string, password = PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username or e-mail').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

export async function signOut(page: Page): Promise<void> {
  await page.getByTestId('account-menu').click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login/);
}

export async function as(page: Page, username: string): Promise<void> {
  if (!page.url().includes('/login') && page.url() !== 'about:blank') {
    const menu = page.getByTestId('account-menu');
    if (await menu.isVisible()) await signOut(page);
  }
  await signIn(page, username);
  await expect(page.getByTestId('account-menu')).toContainText(username);
}

/** Picks an option of the mat-select with the given label. */
export async function choose(page: Page, label: string, option: string | RegExp): Promise<void> {
  // A click that lands while the previous panel is still animating closed does not open it, so retry until it does.
  await expect(async () => {
    await page.getByRole('combobox', { name: label }).click();
    await expect(page.getByRole('listbox')).toBeVisible({ timeout: 1000 });
  }).toPass({ timeout: 10_000 });
  await page.getByRole('option', { name: option }).click();
  await expect(page.getByRole('listbox')).toHaveCount(0);
}

/** Dialog helper: the confirm button inside the open dialog. */
export function dialog(page: Page) {
  return page.getByRole('dialog');
}

/** Status chip text on the claim page header. */
export async function expectStatus(page: Page, label: string): Promise<void> {
  await expect(page.locator('.ref-line app-status-chip')).toContainText(label);
}

/** Reports and notifications are fed by Kafka, so retry with a reload until they catch up. */
export async function eventually(page: Page, check: () => Promise<void>): Promise<void> {
  await expect(async () => {
    await page.reload();
    await check();
  }).toPass({ timeout: 20_000, intervals: [500, 1000, 2000] });
}

/** Direct API access for test set-up (fast path, same credentials as the UI). */
export async function api(username: string): Promise<APIRequestContext> {
  return request.newContext({
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    httpCredentials: { username, password: PASSWORD, send: 'always' },
  });
}

/** A past date in the en-US format the native date adapter parses. */
export function daysAgo(n: number): string {
  const d = new Date(Date.now() - n * 864e5);
  return `${d.getMonth() + 1}/${d.getDate()}/${d.getFullYear()}`;
}
