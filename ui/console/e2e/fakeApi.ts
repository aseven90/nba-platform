import type { Page, Route } from '@playwright/test';

type Json = Record<string, unknown>;

/**
 * Stateful stand-in for the four services: enough behaviour to drive every screen. Recommendation responses
 * are canned per scenario so the assertions are deterministic.
 */
export class FakeApi {
  offerings: Json[] = [];
  campaigns: Json[] = [];
  interactions: Json[] = [];
  interactionKeys: string[] = [];
  tenants = new Set<string>();
  fallback = false;
  private seq = 0;

  async install(page: Page) {
    await page.route('**/tmf-api/**', (route) => this.handle(route));
  }

  private nextId(prefix: string) {
    this.seq += 1;
    return `${prefix}-${this.seq}`;
  }

  private async handle(route: Route) {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    const tenant = request.headers()['x-tenant-id'];
    if (!tenant) return route.fulfill({ status: 400, json: { code: '400', reason: 'missingTenant', message: 'no tenant' } });
    this.tenants.add(tenant);
    const body = request.postData() ? (JSON.parse(request.postData()!) as Json) : {};
    const path = url.pathname;

    const crud = (store: Json[], base: string, type: string) => {
      const id = path.slice(base.length + 1);
      if (method === 'GET' && !id) {
        return route.fulfill({ json: store, headers: { 'X-Total-Count': String(store.length) } });
      }
      if (method === 'POST') {
        const created = { lifecycleStatus: 'draft', ...body, id: this.nextId(type), '@type': type };
        store.push(created);
        return route.fulfill({ status: 201, json: created });
      }
      const index = store.findIndex((item) => item.id === id);
      if (index < 0) return route.fulfill({ status: 404, json: { code: '404', reason: 'notFound', message: `${id} not found` } });
      if (method === 'PATCH') {
        store[index] = { ...store[index], ...body };
        return route.fulfill({ json: store[index] });
      }
      if (method === 'DELETE') {
        store.splice(index, 1);
        return route.fulfill({ status: 204 });
      }
      return route.fulfill({ json: store[index] });
    };

    if (path.startsWith('/tmf-api/productCatalogManagement/v4/productOffering')) {
      return crud(this.offerings, '/tmf-api/productCatalogManagement/v4/productOffering', 'offer');
    }
    if (path.startsWith('/tmf-api/promotionManagement/v4/promotion')) {
      return crud(this.campaigns, '/tmf-api/promotionManagement/v4/promotion', 'campaign');
    }
    if (path.startsWith('/tmf-api/partyInteractionManagement/v4/partyInteraction')) {
      if (method === 'POST') {
        this.interactionKeys.push(request.headers()['idempotency-key'] ?? '');
        const created = { ...body, id: this.nextId('interaction'), interactionDate: new Date().toISOString() };
        this.interactions.unshift(created);
        return route.fulfill({ status: 201, json: created });
      }
      return route.fulfill({ json: this.interactions, headers: { 'X-Total-Count': String(this.interactions.length) } });
    }
    if (path === '/tmf-api/recommendationManagement/v4/recommendation') {
      return route.fulfill({ status: 201, json: this.recommendation(body, url.searchParams.get('explain') === 'true') });
    }
    return route.fulfill({ status: 404, json: { code: '404', reason: 'notFound', message: path } });
  }

  private recommendation(body: Json, explain: boolean) {
    const party = (body.relatedParty as Json[])[0];
    const channel = body.channel as Json;
    const [first, second, third] = this.offerings;
    const [growth, onboarding] = this.campaigns;
    const ref = (o?: Json) => (o ? { id: o.id, name: o.name } : { id: 'unknown' });
    const items = this.fallback
      ? [{ id: '1', priority: 1, productOffering: ref(third), reasonCode: ['SAFE_DEFAULT', 'DEADLINE_EXCEEDED'] }]
      : [
          { id: '1', priority: 1, productOffering: ref(first), promotion: ref(growth), score: 14.21, propensity: 0.118, reasonCode: ['MODEL_v1', 'EXPLORING_NEW_ARM'] },
          { id: '2', priority: 2, productOffering: ref(second), promotion: ref(growth), score: 11.4, propensity: 0.237, reasonCode: ['MODEL_v1', 'POSTERIOR_SAMPLED'] },
        ];
    return {
      id: 'decision-123',
      validFor: { startDateTime: new Date().toISOString() },
      channel,
      relatedParty: [party],
      recommendationItem: items,
      suppressedItem: explain && !this.fallback
        ? [{ productOffering: ref(third), promotion: ref(onboarding), stage: 'eligibility-rules', reasonCode: 'RULE_NOT_MET' }]
        : explain ? [] : undefined,
      fallback: this.fallback,
    };
  }
}
