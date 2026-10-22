import { expect, Page, test } from '@playwright/test';
import { as, choose, daysAgo, PDF } from './support';

/** The app on a phone (Pixel 7): navigation drawer, the claim wizard, the claim page and reports, no sideways scroll. */

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, 'page is wider than the screen').toBeLessThanOrEqual(1);
}

async function openNav(page: Page, link: string) {
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.locator('mat-sidenav').getByRole('link', { name: link }).click();
}

test.describe.serial('mobile', () => {
  let claimUrl = '';

  test('sign-in fits the screen and the brand panel is hidden', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expect(page.locator('.hero')).toBeHidden();
    await expectNoHorizontalScroll(page);
  });

  test('claimant reports an incident through the wizard on a phone', async ({ page }) => {
    await as(page, 'tan@example.com');
    await expect(page.getByRole('button', { name: 'Open navigation' })).toBeVisible();   // drawer, not a sidebar
    await openNav(page, 'Report an incident');
    await expect(page).toHaveURL(/\/my-claims\/new$/);

    await page.getByRole('radio', { name: /Property/ }).click();
    await choose(page, 'Market', 'Hong Kong (HKD)');
    await page.getByLabel('Date of incident').fill(daysAgo(1));
    await page.getByLabel('What happened?').fill('Ceiling leak after heavy rain.');
    await expectNoHorizontalScroll(page);
    await page.getByRole('button', { name: 'Continue' }).click();

    await page.getByLabel('Your full name').fill('Tan Wei');
    await page.getByLabel('Estimated amount').fill('7800');
    await page.getByTestId('file-input').setInputFiles(PDF);
    await page.getByRole('button', { name: 'Review' }).click();
    await expect(page.getByText('HKD 7,800.00')).toBeVisible();
    await page.getByRole('button', { name: 'Submit claim' }).click();

    await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}\?submitted=1$/);
    claimUrl = new URL(page.url()).pathname;
    await expect(page.getByTestId('reference-number')).toHaveText(/^CLM-HK-/);
    await expectNoHorizontalScroll(page);   // the confirmation panel stacks on a phone
    await expect(page.getByText('police-report.pdf')).toBeVisible();
    await expectNoHorizontalScroll(page);
  });

  test('officer picks the claim up and acts on it from a phone', async ({ page }) => {
    await as(page, 'officer-1');
    await page.goto(claimUrl);
    await page.getByRole('button', { name: 'Pick up' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Pick up' }).click();
    await expect(page.locator('.ref-line app-status-chip')).toContainText('Under review');
    for (const action of ['Assess', 'Request info', 'Approve', 'Reject', 'Reassign']) {
      await expect(page.getByRole('button', { name: action })).toBeVisible();
    }
    await expectNoHorizontalScroll(page);
  });

  test('tables scroll inside their card, not the page', async ({ page }) => {
    await as(page, 'officer-1');
    await openNav(page, 'My workload');
    await expect(page.getByRole('table')).toBeVisible();
    await expectNoHorizontalScroll(page);
  });

  test('manager reports are usable on a phone', async ({ page }) => {
    await as(page, 'manager-1');
    await expect(page.getByText('Outstanding exposure')).toBeVisible();
    await expectNoHorizontalScroll(page);
    await openNav(page, 'Team performance');
    await expect(page).toHaveURL(/\/team$/);
    await expectNoHorizontalScroll(page);
    await openNav(page, 'SLA breaches');
    await expectNoHorizontalScroll(page);
  });

  test('notifications are readable on a phone', async ({ page }) => {
    await as(page, 'tan@example.com');
    await openNav(page, 'Notifications');
    await expect(page).toHaveURL(/\/notifications$/);
    await expectNoHorizontalScroll(page);
  });
});
