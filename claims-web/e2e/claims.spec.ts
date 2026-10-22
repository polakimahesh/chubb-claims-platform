import { expect, test } from '@playwright/test';
import { api, as, choose, daysAgo, dialog, eventually, expectStatus, PDF, signIn } from './support';

/**
 * Every backend feature, driven through the UI against the running services. The tests are serial: later ones
 * continue with the claims created earlier, like a claim moving through its lifecycle.
 */
test.describe.serial('claims platform', () => {
  let claimUrl = '';
  let claimNumber = '';

  // ---------- sign-in ----------

  test('wrong password is refused with a clear message', async ({ page }) => {
    await signIn(page, 'tan@example.com', 'definitely-wrong');
    await expect(page.getByRole('alert')).toContainText('Incorrect username or password');
    await expect(page).toHaveURL(/\/login/);
  });

  test('each role lands on its own home screen with its own navigation', async ({ page }) => {
    await as(page, 'tan@example.com');
    await expect(page).toHaveURL(/\/my-claims$/);
    await expect(page.getByRole('link', { name: 'Intake queue' })).toHaveCount(0);

    await as(page, 'officer-1');
    await expect(page).toHaveURL(/\/queue$/);
    await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveCount(0);

    await as(page, 'manager-1');
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('link', { name: 'SLA breaches' })).toBeVisible();
  });

  test('a claimant cannot open staff screens', async ({ page }) => {
    await as(page, 'tan@example.com');
    await page.goto('/queue');
    await expect(page).toHaveURL(/\/my-claims$/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/my-claims$/);
  });

  // ---------- claimant: submit (features 2, 7) ----------

  test('claimant reports an incident with a document; the currency follows the market', async ({ page }) => {
    await as(page, 'tan@example.com');
    await page.getByRole('link', { name: 'Report an incident' }).first().click();

    // step 1 - validation blocks an empty step
    await page.getByRole('button', { name: 'Continue' }).click();
    await expect(page.getByText('Choose motor or property')).toBeVisible();

    await page.getByRole('radio', { name: /Motor/ }).click();
    await choose(page, 'Market', 'Singapore (SGD)');
    await page.getByLabel('Date of incident').fill(daysAgo(3));
    await page.getByLabel('What happened?').fill('Rear-ended at traffic lights on Orchard Road.');
    await page.getByRole('button', { name: 'Continue' }).click();

    // step 2 - currency prefix derived from the market; file type validation
    await page.getByLabel('Your full name').fill('Tan Wei');
    await expect(page.locator('.mat-mdc-form-field-text-prefix')).toContainText('SGD');
    await page.getByLabel('Estimated amount').fill('5000.555');
    await page.getByRole('button', { name: 'Review' }).click();
    await expect(page.getByText('Use up to 2 decimal places')).toBeVisible();
    await page.getByLabel('Estimated amount').fill('5000');
    await page.getByTestId('file-input').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('x') });
    await expect(page.getByText('only PDF, PNG and JPEG files are accepted')).toBeVisible();
    await page.getByTestId('file-input').setInputFiles(PDF);
    await expect(page.getByText('police-report.pdf')).toBeVisible();
    await page.getByRole('button', { name: 'Review' }).click();

    // step 3 - review and submit
    await expect(page.getByText('SGD 5,000.00')).toBeVisible();
    await page.getByRole('button', { name: 'Submit claim' }).click();

    await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}\?submitted=1$/);
    claimUrl = new URL(page.url()).pathname;
    claimNumber = (await page.locator('h1').first().innerText()).trim();
    expect(claimNumber).toMatch(/^CLM-SG-\d{4}-[0-9A-F]{8}$/);

    // confirmation with the reference number, as on a live claims portal; it can be dismissed
    await expect(page.getByText('Thank you. Your claim has been submitted.')).toBeVisible();
    await expect(page.getByTestId('reference-number')).toHaveText(claimNumber);
    await expect(page.getByRole('list', { name: 'What happens next' })).toBeVisible();
    await page.getByRole('button', { name: 'Dismiss' }).click();
    await expect(page.getByText('Thank you. Your claim has been submitted.')).toHaveCount(0);

    await expectStatus(page, 'Submitted');
    await expect(page.getByText('police-report.pdf')).toBeVisible();
    await expect(page.getByText('Waiting for a claims officer')).toBeVisible();
  });

  test('claimant sees the claim in their list (feature 3)', async ({ page }) => {
    await as(page, 'tan@example.com');
    await expect(page.getByRole('link', { name: claimNumber })).toBeVisible();
  });

  test('another claimant cannot see it - the API answers 404 (record-level rule)', async ({ page }) => {
    await as(page, 'lee@example.com');
    await page.goto(claimUrl);
    await expect(page.getByText('Claim not found')).toBeVisible();
  });

  // ---------- officer: queue, pick up, request info (10, 11, 13) ----------

  test('officer filters the queue and picks the claim up', async ({ page }) => {
    await as(page, 'officer-1');
    await choose(page, 'Market', 'Australia');
    await expect(page.getByRole('link', { name: claimNumber })).toHaveCount(0);
    await choose(page, 'Market', 'Singapore');
    await expect(page.locator('tr.mat-mdc-row').first()).toBeVisible();
    await expect(page.locator('tr.mat-mdc-row').filter({ hasNotText: 'Singapore' })).toHaveCount(0);
    // oldest first: a new claim can be on a later page when earlier runs left claims in the queue
    const row = page.getByRole('row').filter({ hasText: claimNumber });
    const range = page.locator('.mat-mdc-paginator-range-label');
    while (!(await row.isVisible())) {
      const next = page.getByRole('button', { name: 'Next page' });
      await expect(next, `claim ${claimNumber} not found in the queue`).toBeEnabled();
      const before = await range.innerText();
      await next.click();
      await expect(range).not.toHaveText(before);
      await expect(page.getByRole('progressbar')).toHaveCount(0);   // the next page's rows have arrived
    }
    await row.getByRole('button', { name: 'Pick up' }).click();
    await expect(page).toHaveURL(new RegExp(claimUrl + '$'));
    await expectStatus(page, 'Under review');
    await expect(page.getByText('Assigned to officer-1')).toBeVisible();
  });

  test('approve is blocked until the claim is assessed', async ({ page }) => {
    await as(page, 'officer-1');
    await page.goto(claimUrl);
    await expect(page.getByRole('button', { name: 'Approve' })).toBeDisabled();
  });

  test('officer requests more information', async ({ page }) => {
    await as(page, 'officer-1');
    await page.goto(claimUrl);
    await page.getByRole('button', { name: 'Request info' }).click();
    await dialog(page).getByLabel('What do you need from the claimant?').fill('Please upload the police report number.');
    await dialog(page).getByRole('button', { name: 'Send request' }).click();
    await expectStatus(page, 'Info requested');
    await expect(page.getByText('Awaiting the claimant\'s answer')).toBeVisible();
  });

  test('another officer sees the claim read-only', async ({ page }) => {
    await as(page, 'officer-2');
    await page.goto(claimUrl);
    await expect(page.getByText('Only the assigned officer can act on it')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Request info' })).toHaveCount(0);
  });

  // ---------- claimant: answer, notifications (6, 9) ----------

  test('claimant is notified, answers, and the claim returns to review', async ({ page }) => {
    await as(page, 'tan@example.com');
    await page.getByRole('link', { name: 'Notifications' }).click();
    await expect(page).toHaveURL(/\/notifications$/);
    await eventually(page, async () => {
      await expect(page.getByText(`Action needed: more information for claim ${claimNumber}`, { exact: true })).toBeVisible({ timeout: 1000 });
    });

    await page.goto(claimUrl);
    await expect(page.getByText('We need more information from you')).toBeVisible();
    await page.getByLabel('Your answer').fill('Police report number SPF-2026-1234.');
    await page.getByRole('button', { name: 'Send answer' }).click();
    await expectStatus(page, 'Under review');
    await expect(page.getByText('SPF-2026-1234')).toBeVisible();
  });

  // ---------- officer: assess, approve, settle (14, 15, 17) ----------

  test('officer assesses, approves and settles', async ({ page }) => {
    await as(page, 'officer-1');
    await page.goto(claimUrl);

    await page.getByRole('button', { name: 'Assess' }).click();
    await dialog(page).getByLabel('Assessed amount').fill('4200');
    await dialog(page).getByLabel('Assessment note (optional)').fill('Bumper and tail light replacement.');
    await dialog(page).getByRole('button', { name: 'Save assessment' }).click();
    await expect(page.getByText('SGD 4,200.00').first()).toBeVisible();

    await page.getByRole('button', { name: 'Approve' }).click();
    await dialog(page).getByRole('button', { name: 'Approve' }).click();
    await expectStatus(page, 'Approved');

    await page.getByRole('button', { name: 'Settle' }).click();
    await dialog(page).getByRole('button', { name: 'Mark as settled' }).click();
    await expectStatus(page, 'Settled');
    await expect(page.getByRole('button', { name: 'Settle' })).toHaveCount(0);
  });

  test('claimant sees the decision, the timeline, and can no longer add documents', async ({ page }) => {
    await as(page, 'tan@example.com');
    await page.goto(claimUrl);
    await expectStatus(page, 'Settled');
    await expect(page.locator('.timeline')).toContainText('Assessed at 4200');
    await expect(page.getByText('Documents cannot be added to a closed claim')).toBeVisible();
    await page.getByRole('link', { name: 'Notifications' }).click();
    await expect(page).toHaveURL(/\/notifications$/);
    await eventually(page, async () => {
      await expect(page.getByText(`Your claim ${claimNumber} has been settled`, { exact: true })).toBeVisible({ timeout: 1000 });
    });
  });

  // ---------- documents: upload from the claim page, download by claimant and staff (7, 8) ----------

  test('claimant attaches a document on the claim page; claimant and officer download the same bytes', async ({ page }) => {
    const tan = await api('tan@example.com');
    const created = await (await tan.post('/api/claims', {
      data: {
        claimantName: 'Tan Wei', market: 'MY', claimType: 'MOTOR', description: 'Windscreen cracked by debris.',
        incidentDate: new Date().toISOString().slice(0, 10), currency: 'MYR', estimatedAmount: 900,
      },
    })).json();
    await tan.dispose();
    const url = `/claims/${created.id}`;
    const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.from('e2e-image-bytes')]);

    await as(page, 'tan@example.com');
    await page.goto(url);
    await expect(page.getByText('No documents attached.')).toBeVisible();
    await page.getByTestId('file-input').setInputFiles({ name: 'windscreen.png', mimeType: 'image/png', buffer: png });
    await page.getByRole('button', { name: /Upload 1 file/ }).click();
    await expect(page.getByText('1 of 20')).toBeVisible();

    const readDownload = async () => {
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        page.getByRole('button', { name: 'Download windscreen.png' }).click(),
      ]);
      expect(download.suggestedFilename()).toBe('windscreen.png');
      const chunks: Buffer[] = [];
      for await (const chunk of await download.createReadStream()) chunks.push(chunk as Buffer);
      return Buffer.concat(chunks);
    };
    expect((await readDownload()).equals(png)).toBe(true);

    await as(page, 'officer-2');
    await page.goto(url);
    expect((await readDownload()).equals(png)).toBe(true);
  });

  // ---------- reassign and reject (16, 18, 20) ----------

  test('officer reassigns, manager reassigns back, officer rejects with a reason', async ({ page }) => {
    const lee = await api('lee@example.com');
    const created = await (await lee.post('/api/claims', {
      data: {
        claimantName: 'Lee Min', market: 'HK', claimType: 'PROPERTY', description: 'Water damage from upstairs flat.',
        incidentDate: new Date().toISOString().slice(0, 10), currency: 'HKD', estimatedAmount: 12000,
      },
    })).json();
    const url = `/claims/${created.id}`;

    await as(page, 'officer-1');
    await page.goto(url);
    await page.getByRole('button', { name: 'Pick up' }).click();
    await dialog(page).getByRole('button', { name: 'Pick up' }).click();
    await expectStatus(page, 'Under review');

    await page.getByRole('button', { name: 'Reassign' }).click();
    await choose(page, 'New officer', 'officer-2');
    await dialog(page).getByLabel('Reason (optional)').fill('Property specialist');
    await dialog(page).getByRole('button', { name: 'Reassign' }).click();
    await expect(page.getByText('Assigned to officer-2')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reassign' })).toHaveCount(0);

    await as(page, 'manager-1');
    await page.goto(url);
    await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0); // managers do not decide claims
    await page.getByRole('button', { name: 'Reassign' }).click();
    await choose(page, 'New officer', 'officer-1');
    await dialog(page).getByRole('button', { name: 'Reassign' }).click();
    await expect(page.locator('.facts')).toContainText('officer-1');

    await as(page, 'officer-1');
    await page.goto(url);
    await page.getByRole('button', { name: 'Reject' }).click();
    await expect(dialog(page).getByRole('button', { name: 'Reject claim' })).toBeDisabled();
    await dialog(page).getByLabel('Reason for rejection').fill('Damage predates the policy start date.');
    await dialog(page).getByRole('button', { name: 'Reject claim' }).click();
    await expectStatus(page, 'Rejected');

    await as(page, 'lee@example.com');
    await page.goto(url);
    await expect(page.getByText('Reason: Damage predates the policy start date.')).toBeVisible();
    await lee.dispose();
  });

  // ---------- workload (12, 19) ----------

  // Workload lists grow with every run (oldest first, 20 per page), so these checks compare the screen with the API
  // and check the filter on every row, instead of looking for one claim that may be on a later page.
  test('officer workload filters by status', async ({ page }) => {
    const officer = await api('officer-1');
    const settled = (await (await officer.get('/api/staff/claims?status=SETTLED&size=1')).json()).totalItems as number;
    await officer.dispose();
    expect(settled).toBeGreaterThan(0);   // this run settled one earlier

    await as(page, 'officer-1');
    await page.getByRole('link', { name: 'My workload' }).click();
    await choose(page, 'Status', 'Settled');
    await expect(page.getByText(`${settled} claim(s)`)).toBeVisible();
    const rows = page.locator('tr.mat-mdc-row');
    await expect(rows.first()).toBeVisible();
    await expect(rows.filter({ hasNotText: 'Settled' })).toHaveCount(0);
    await choose(page, 'Status', 'Approved');
    await expect(rows.filter({ hasText: 'Settled' })).toHaveCount(0);
  });

  test('manager views any officer\'s workload', async ({ page }) => {
    const mgr = await api('manager-1');
    const total = (await (await mgr.get('/api/staff/claims?officerId=officer-1&size=1')).json()).totalItems as number;
    await mgr.dispose();

    await as(page, 'manager-1');
    await page.getByRole('link', { name: 'Officer workload' }).click();
    await expect(page.getByText('Choose an officer')).toBeVisible();
    await choose(page, 'Officer', 'officer-1');
    await expect(page.getByText(`${total} claim(s)`)).toBeVisible();
    await expect(page.locator('tr.mat-mdc-row').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Pick up' })).toHaveCount(0);   // managers do not pick up claims
    await expect(page).toHaveURL(/officerId=officer-1/);                              // shareable link
  });

  // ---------- manager reports (21 - 26) ----------

  test('manager dashboard: summary, exposure and converted total', async ({ page }) => {
    // exposure is an aggregate over all open claims, so measure it first and expect the new claim to add to it
    const manager = await api('manager-1');
    const rows: { claimType: string; totalExposure: number }[] = await (await manager.get('/api/reports/exposure?market=AU')).json();
    await manager.dispose();
    const before = rows.find((r) => r.claimType === 'PROPERTY')?.totalExposure ?? 0;
    const expected = new Intl.NumberFormat('en-SG', { style: 'currency', currency: 'AUD', currencyDisplay: 'code' }).format(before + 8000);

    const tan = await api('tan@example.com');
    await tan.post('/api/claims', {
      data: {
        claimantName: 'Tan Wei', market: 'AU', claimType: 'PROPERTY', description: 'Storm damage to roof.',
        incidentDate: new Date().toISOString().slice(0, 10), currency: 'AUD', estimatedAmount: 8000,
      },
    });
    await tan.dispose();

    await as(page, 'manager-1');
    await eventually(page, async () => {
      await expect(page.locator('table')).toContainText(expected, { timeout: 1000 });
    });
    await expect(page.getByText('Unassigned backlog')).toBeVisible();
    await expect(page.getByText(/indicative|not live/i)).toBeVisible();
    await choose(page, 'Convert to', 'AUD');
    await expect(page.locator('.tile').filter({ hasText: 'Outstanding exposure' })).toContainText('AUD');
    // the market filter shows only that market's rows (data-independent: this test just created an AU claim)
    await choose(page, 'Market', 'Australia');
    const marketRows = page.locator('tr.mat-mdc-row');
    await expect(marketRows.filter({ hasNotText: 'Australia' })).toHaveCount(0);   // retries until the filter applies
    await expect(marketRows.first()).toContainText('Australia');
  });

  test('manager team performance and SLA report', async ({ page }) => {
    await as(page, 'manager-1');
    await page.getByRole('link', { name: 'Team performance' }).click();
    await expect(page).toHaveURL(/\/team$/);
    const perf = page.locator('mat-card').filter({ hasText: 'Performance' });
    await eventually(page, async () => {
      await expect(perf.getByRole('row').filter({ hasText: 'officer-1' })).toBeVisible({ timeout: 1000 });
    });
    await page.getByRole('link', { name: 'SLA breaches' }).click();
    await expect(page.getByText('Not picked up in time')).toBeVisible();
  });

  test('SLA breaches: an unassigned claim past the threshold is listed and links to the claim (26)', async ({ page }) => {
    // A real breach needs a claim older than reporting.sla.unassigned-hours (24 h by default). Run the reporting
    // service with the threshold at 0 (SLA_UNASSIGNED_HOURS=0 in Docker) and set E2E_SLA_ZERO=1 to exercise it.
    test.skip(process.env['E2E_SLA_ZERO'] !== '1', 'needs reporting-service with reporting.sla.unassigned-hours=0');
    const tan = await api('tan@example.com');
    const created = await (await tan.post('/api/claims', {
      data: {
        claimantName: 'Tan Wei', market: 'TH', claimType: 'PROPERTY', description: 'Burst pipe in the bathroom.',
        incidentDate: new Date().toISOString().slice(0, 10), currency: 'THB', estimatedAmount: 25000,
      },
    })).json();
    await tan.dispose();

    await as(page, 'manager-1');
    await page.goto('/sla');
    const row = page.getByRole('row').filter({ hasText: created.claimNumber });
    await eventually(page, async () => {
      await expect(row).toBeVisible({ timeout: 1000 });
    });
    await expect(row).toContainText('Not picked up');
    await expect(row).toContainText('Unassigned');
    await expect(page.locator('.tile').filter({ hasText: 'Not picked up in time' }).locator('.tile-value')).not.toHaveText('0');
    await row.getByRole('link', { name: created.claimNumber }).click();
    await expect(page).toHaveURL(new RegExp(`/claims/${created.id}$`));
  });

  // ---------- theme ----------

  test('dark mode can be chosen, survives a reload and sign-out, and system mode can be restored', async ({ page }) => {
    await as(page, 'officer-1');
    await page.getByTestId('theme-menu').click();
    await page.getByRole('menuitemradio', { name: 'Dark' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    // the switch animates for 0.2 s, so wait for the final dark page colour
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe('rgb(0, 0, 0)');   // the dark theme's page colour (--app-bg)

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.getByTestId('account-menu').click();
    await page.getByRole('menuitem', { name: 'Sign out' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');   // a browser preference, not per user

    await page.getByTestId('theme-toggle').click();   // the sign-in page has its own switch
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await signIn(page, 'officer-1');
    await page.getByTestId('theme-menu').click();
    await page.getByRole('menuitemradio', { name: 'System' }).click();
    await expect(page.locator('html')).not.toHaveAttribute('data-theme', /.*/);
  });

  test('staff see all notifications and can filter by claimant (27)', async ({ page }) => {
    await as(page, 'officer-1');
    await page.getByRole('link', { name: 'Notifications' }).click();
    await expect(page).toHaveURL(/\/notifications$/);
    await page.getByLabel('Claimant e-mail').fill('lee@example.com');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByText(/Decision on your claim/).first()).toBeVisible();
    await expect(page.getByText('tan@example.com')).toHaveCount(0);
  });

  test('session survives a reload and sign-out clears it', async ({ page }) => {
    await as(page, 'officer-2');
    await page.reload();
    await expect(page.getByTestId('account-menu')).toContainText('officer-2');
    await page.getByTestId('account-menu').click();
    await page.getByRole('menuitem', { name: 'Sign out' }).click();
    await page.goto('/queue');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fqueue/);
  });
});
