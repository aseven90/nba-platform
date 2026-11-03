import { expect, test } from '@playwright/test';
import { FakeApi } from './fakeApi';

const SHOTS = process.env.SCREENSHOT_DIR;

test('load demo data, then decide and record a response', async ({ page }) => {
  const api = new FakeApi();
  await api.install(page);
  await page.goto('/');

  await page.getByRole('button', { name: 'Load demo data' }).click();
  await expect(page.getByText('Created 3 actions and 2 active campaigns.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Actions 3' })).toBeVisible();
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/1-overview.png`, fullPage: true });

  await page.getByRole('link', { name: 'Campaigns', exact: true }).first().click();
  await expect(page.getByRole('cell', { name: /Grow loyal customers/ })).toBeVisible();
  await expect(page.getByText('tenureMonths >= 12')).toBeVisible();
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/3-campaigns.png`, fullPage: true });

  await page.getByRole('link', { name: 'Decision playground' }).click();
  await page.getByRole('button', { name: 'Get next best actions' }).click();
  const ranked = page.locator('.ranked-item');
  await expect(ranked).toHaveCount(2);
  await expect(ranked.first()).toContainText('Premium plan upgrade');
  await expect(ranked.first()).toContainText('via Grow loyal customers');
  await expect(page.getByText('The campaign eligibility rule evaluated to false.')).toBeVisible();

  await ranked.first().getByRole('button', { name: 'Accepted' }).click();
  await expect(ranked.first().getByRole('button', { name: '✓ Accepted' })).toBeDisabled();
  expect(api.interactionKeys).toEqual(['decision-123:offer-1:ACCEPTED']);
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/4-playground.png`, fullPage: true });

  await page.getByRole('link', { name: 'Interactions' }).click();
  await expect(page.getByRole('cell', { name: 'ACCEPTED' })).toBeVisible();
  expect([...api.tenants]).toEqual(['demo']);
});

test('create and edit an action with a propensity model', async ({ page }) => {
  const api = new FakeApi();
  await api.install(page);
  await page.goto('/actions');

  await page.getByRole('button', { name: 'New action' }).click();
  const form = page.getByRole('form', { name: 'Action form' });
  await form.getByLabel('Name', { exact: true }).fill('Paperless billing');
  await form.getByLabel('Business value').fill('15');
  await form.getByRole('button', { name: 'Save action' }).click();

  await expect(page.getByRole('cell', { name: /Paperless billing/ })).toBeVisible();
  expect(api.offerings[0]).toMatchObject({
    name: 'Paperless billing',
    lifecycleStatus: 'Active',
    businessValue: 15,
    channel: [{ id: 'web' }],
    propensityModel: { version: 'v1', intercept: -2, coefficients: { tenureMonths: 0.05 } },
  });

  await page.getByRole('button', { name: 'Edit' }).click();
  await form.getByLabel('Status').selectOption('Retired');
  await form.getByRole('button', { name: 'Save action' }).click();
  await expect(page.getByText('Retired', { exact: true })).toBeVisible();
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/2-actions.png`, fullPage: true });
});

test('shows the safe default answer clearly', async ({ page }) => {
  const api = new FakeApi();
  api.offerings = [{ id: 'safe-1', name: 'Contact us' }];
  api.fallback = true;
  await api.install(page);
  await page.goto('/playground');
  await page.getByRole('button', { name: 'Get next best actions' }).click();
  await expect(page.getByText('safe default', { exact: true })).toBeVisible();
  await expect(page.getByText('The decision took longer than its deadline.')).toBeVisible();
});

test('switching tenant sends the new tenant id', async ({ page }) => {
  const api = new FakeApi();
  await api.install(page);
  await page.goto('/actions');
  await page.getByLabel('Tenant').fill('acme');
  await page.getByRole('button', { name: 'Switch' }).click();
  await expect(page.getByText('X-Tenant-Id: acme')).toBeVisible();
  await expect.poll(() => api.tenants.has('acme')).toBe(true);
});
