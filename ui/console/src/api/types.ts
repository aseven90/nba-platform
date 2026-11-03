// Shapes of the TMF resources the console uses. Mirrors contracts/openapi.

export interface Ref {
  id: string;
  name?: string;
  role?: string;
  '@referredType'?: string;
}

export interface ValidFor {
  startDateTime?: string;
  endDateTime?: string;
}

export interface PropensityModel {
  version?: string;
  intercept: number;
  coefficients: Record<string, number>;
}

export interface ProductOffering {
  id?: string;
  href?: string;
  name: string;
  description?: string;
  lifecycleStatus?: string;
  version?: string;
  lastUpdate?: string;
  validFor?: ValidFor;
  category?: { id?: string; name?: string }[];
  channel?: Ref[];
  businessValue?: number;
  propensityModel?: PropensityModel | null;
}

export interface PromotionAction {
  actionType?: string;
  actionObjectId: string;
  priorityLever?: number;
}

export interface Promotion {
  id?: string;
  href?: string;
  name: string;
  description?: string;
  lifecycleStatus?: string;
  version?: string;
  lastUpdate?: string;
  validFor?: ValidFor;
  channel?: Ref[];
  eligibilityRule?: string | null;
  controlGroupPercent?: number;
  maxImpressionsPerDay?: number;
  action: PromotionAction[];
}

export interface Characteristic {
  name: string;
  value: unknown;
}

export interface RecommendationRequest {
  relatedParty: Ref[];
  channel: Ref;
  characteristic: Characteristic[];
  maxItems?: number;
}

export interface RecommendationItem {
  id: string;
  priority: number;
  productOffering: Ref;
  promotion?: Ref;
  score?: number;
  propensity?: number;
  reasonCode: string[];
}

export interface SuppressedItem {
  productOffering: Ref;
  promotion?: Ref;
  stage: string;
  reasonCode: string;
}

export interface Recommendation {
  id: string;
  validFor: ValidFor;
  channel: Ref;
  relatedParty: Ref[];
  recommendationItem: RecommendationItem[];
  suppressedItem?: SuppressedItem[];
  fallback: boolean;
}

export type Outcome = 'IMPRESSION' | 'ACCEPTED' | 'DECLINED' | 'IGNORED';

export interface PartyInteraction {
  id?: string;
  interactionDate?: string;
  relatedParty: Ref[];
  channel: Ref[];
  decisionId?: string;
  productOffering: Ref;
  promotion?: Ref;
  outcome: Outcome;
}

export interface Page<T> {
  items: T[];
  total: number;
}
