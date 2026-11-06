import { campaignApi, catalogApi } from '../api/endpoints';
import type { ProductOffering, Promotion } from '../api/types';

export interface DemoLoadResult {
  /** Demo records written by this call. */
  created: { actions: number; campaigns: number };
  /** Demo records that were already present (matched by name) and left untouched. */
  existing: { actions: number; campaigns: number };
}

/**
 * Loads a small, industry-neutral data set that exercises rules, levers, caps and control groups.
 *
 * Idempotent: demo records are identified by name within the tenant, so pressing the button again only
 * creates what is missing (for example, an action someone deleted) and never duplicates what is there.
 * Existing records are not overwritten, so edits made to the demo data survive a reload.
 */
export async function loadDemoData(tenant: string): Promise<DemoLoadResult> {
  const offerings: ProductOffering[] = [
    {
      name: 'Premium plan upgrade',
      description: 'Move to the premium tier',
      lifecycleStatus: 'Active',
      category: [{ name: 'upsell' }],
      channel: [{ id: 'web' }, { id: 'app' }],
      businessValue: 120,
      propensityModel: { version: 'v1', intercept: -3, coefficients: { tenureMonths: 0.05, monthlySpend: 0.01 } },
    },
    {
      name: 'Loyalty reward',
      description: 'Thank-you voucher for long-standing customers',
      lifecycleStatus: 'Active',
      category: [{ name: 'retention' }],
      channel: [{ id: 'web' }, { id: 'app' }, { id: 'contact-center' }],
      businessValue: 40,
      propensityModel: { version: 'v1', intercept: -1, coefficients: { tenureMonths: 0.03 } },
    },
    {
      name: 'Complete your profile',
      description: 'Service nudge with no direct revenue',
      lifecycleStatus: 'Active',
      category: [{ name: 'service' }],
      channel: [],
      businessValue: 0,
      propensityModel: { version: 'v1', intercept: 0.5, coefficients: {} },
    },
  ];
  const result: DemoLoadResult = { created: { actions: 0, campaigns: 0 }, existing: { actions: 0, campaigns: 0 } };

  const existingOfferings = byName((await catalogApi.list(tenant)).items);
  const ids: string[] = [];
  for (const offering of offerings) {
    const found = existingOfferings.get(offering.name);
    if (found?.id) {
      result.existing.actions += 1;
      ids.push(found.id);
    } else {
      result.created.actions += 1;
      ids.push((await catalogApi.create(tenant, offering)).id!);
    }
  }
  const [upgrade, reward, profile] = ids;

  const campaigns: Promotion[] = [
    {
    name: 'Grow loyal customers',
    lifecycleStatus: 'active',
    channel: [{ id: 'web' }, { id: 'app' }],
    eligibilityRule: 'tenureMonths >= 12',
    controlGroupPercent: 5,
    maxImpressionsPerDay: 3,
    action: [
      { actionObjectId: upgrade, priorityLever: 1 },
      { actionObjectId: reward, priorityLever: 1.2 },
    ],
    },
    {
    name: 'Onboarding',
    lifecycleStatus: 'active',
    channel: [],
    eligibilityRule: 'tenureMonths < 6',
    controlGroupPercent: 0,
    maxImpressionsPerDay: 0,
    action: [{ actionObjectId: profile, priorityLever: 1 }],
    },
  ];
  const existingCampaigns = byName((await campaignApi.list(tenant)).items);
  for (const campaign of campaigns) {
    if (existingCampaigns.has(campaign.name)) {
      result.existing.campaigns += 1;
    } else {
      result.created.campaigns += 1;
      await campaignApi.create(tenant, campaign);
    }
  }
  return result;
}

/** Index by name; the first record wins if a tenant already holds duplicates from before this fix. */
function byName<T extends { name?: string }>(items: T[]): Map<string, T> {
  const map = new Map<string, T>();
  for (const item of items) if (item.name && !map.has(item.name)) map.set(item.name, item);
  return map;
}
