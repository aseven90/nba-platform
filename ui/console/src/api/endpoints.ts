import { list, request } from './client';
import type {
  PartyInteraction,
  Outcome,
  ProductOffering,
  Promotion,
  Recommendation,
  RecommendationRequest,
} from './types';

const CATALOG = '/tmf-api/productCatalogManagement/v4/productOffering';
const CAMPAIGNS = '/tmf-api/promotionManagement/v4/promotion';
const DECISIONS = '/tmf-api/recommendationManagement/v4/recommendation';
const INTERACTIONS = '/tmf-api/partyInteractionManagement/v4/partyInteraction';
const MERGE_PATCH = 'application/merge-patch+json';

export const catalogApi = {
  list: (tenant: string) => list<ProductOffering>(tenant, CATALOG),
  create: (tenant: string, offering: ProductOffering) =>
    request<ProductOffering>(tenant, CATALOG, { method: 'POST', body: offering }),
  update: (tenant: string, id: string, patch: Partial<ProductOffering>) =>
    request<ProductOffering>(tenant, `${CATALOG}/${id}`, { method: 'PATCH', body: patch, contentType: MERGE_PATCH }),
  remove: (tenant: string, id: string) => request<void>(tenant, `${CATALOG}/${id}`, { method: 'DELETE' }),
};

export const campaignApi = {
  list: (tenant: string) => list<Promotion>(tenant, CAMPAIGNS),
  create: (tenant: string, promotion: Promotion) =>
    request<Promotion>(tenant, CAMPAIGNS, { method: 'POST', body: promotion }),
  update: (tenant: string, id: string, patch: Partial<Promotion>) =>
    request<Promotion>(tenant, `${CAMPAIGNS}/${id}`, { method: 'PATCH', body: patch, contentType: MERGE_PATCH }),
  remove: (tenant: string, id: string) => request<void>(tenant, `${CAMPAIGNS}/${id}`, { method: 'DELETE' }),
};

export const decisionApi = {
  recommend: (tenant: string, body: RecommendationRequest) =>
    request<Recommendation>(tenant, DECISIONS, { method: 'POST', body, query: { explain: true } }),
};

export const interactionApi = {
  list: (tenant: string, partyId?: string) =>
    list<PartyInteraction>(tenant, INTERACTIONS, { 'relatedParty.id': partyId || undefined }),
  record: (
    tenant: string,
    args: { partyId: string; channel: string; decisionId: string; offeringId: string; campaignId?: string; outcome: Outcome },
  ) =>
    request<PartyInteraction>(tenant, INTERACTIONS, {
      method: 'POST',
      // One interaction per decision, action and outcome, however many times the button is pressed.
      headers: { 'Idempotency-Key': `${args.decisionId}:${args.offeringId}:${args.outcome}` },
      body: {
        relatedParty: [{ id: args.partyId, role: 'customer' }],
        channel: [{ id: args.channel }],
        decisionId: args.decisionId,
        productOffering: { id: args.offeringId },
        promotion: args.campaignId ? { id: args.campaignId } : undefined,
        outcome: args.outcome,
      } satisfies PartyInteraction,
    }),
};
