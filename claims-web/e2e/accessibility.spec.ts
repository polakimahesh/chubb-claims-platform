import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';
import { api, as } from './support';

/**
 * Automated accessibility scan (axe-core, WCAG 2.1 A/AA rules) of every main screen in both themes.
 * Fails on serious or critical violations; minor/moderate findings are printed for review.
 */
async function scan(page: Page, label: string) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(300); // let Material finish rendering
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const blocking = result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  for (const v of result.violations.filter((x) => !blocking.includes(x))) {
    console.log(`[a11y ${label}] ${v.impact}: ${v.id} (${v.nodes.length})`);
  }
  expect(
    blocking.map((v) => `${v.id}: ${v.help} → ${v.nodes.map((n) => n.target.join(' ')).slice(0, 3).join(' | ')}`),
    `serious/critical accessibility violations on ${label}`,
  ).toEqual([]);
}

for (const scheme of ['light', 'dark'] as const) {
  test.describe(`accessibility (${scheme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
    });

    test('sign-in', async ({ page }) => {
      await page.goto('/login');
      await scan(page, `${scheme} sign-in`);
    });

    test('claimant screens', async ({ page }) => {
      const tan = await api('tan@example.com');
      const c = await (await tan.post('/api/claims', {
        data: {
          claimantName: 'Tan Wei', market: 'SG', claimType: 'MOTOR', description: 'Accessibility scan claim.',
          incidentDate: new Date().toISOString().slice(0, 10), currency: 'SGD', estimatedAmount: 1000,
        },
      })).json();
      await tan.dispose();
      await as(page, 'tan@example.com');
      await scan(page, `${scheme} my claims`);
      await page.goto('/my-claims/new');
      await scan(page, `${scheme} report an incident`);
      await page.goto(`/claims/${c.id}`);
      await expect(page.locator('h1')).toContainText('CLM-');
      await scan(page, `${scheme} claim page (claimant)`);
      await page.goto('/notifications');
      await scan(page, `${scheme} notifications`);
    });

    test('officer screens', async ({ page }) => {
      await as(page, 'officer-1');
      await scan(page, `${scheme} intake queue`);
      await page.goto('/workload');
      await scan(page, `${scheme} workload`);
    });

    test('manager screens', async ({ page }) => {
      await as(page, 'manager-1');
      await expect(page.locator('.tile-label', { hasText: 'Open claims' })).toBeVisible();
      await scan(page, `${scheme} dashboard`);
      await page.goto('/team');
      await scan(page, `${scheme} team`);
      await page.goto('/sla');
      await scan(page, `${scheme} sla`);
    });
  });
}
