import { campaignApi, catalogApi } from '../api/endpoints';
import type { ProductOffering } from '../api/types';

/** A small, industry-neutral data set that exercises rules, levers, caps and control groups. */
export async function loadDemoData(tenant: string): Promise<{ actions: number; campaigns: number }> {
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
  const created = [];
  for (const offering of offerings) created.push(await catalogApi.create(tenant, offering));
  const [upgrade, reward, profile] = created.map((o) => o.id!);

  await campaignApi.create(tenant, {
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
  });
  await campaignApi.create(tenant, {
    name: 'Onboarding',
    lifecycleStatus: 'active',
    channel: [],
    eligibilityRule: 'tenureMonths < 6',
    controlGroupPercent: 0,
    maxImpressionsPerDay: 0,
    action: [{ actionObjectId: profile, priorityLever: 1 }],
  });
  return { actions: created.length, campaigns: 2 };
}
