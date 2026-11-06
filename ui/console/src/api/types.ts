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
  /** Scorer plugin: logistic (default, evaluated in the decision service) or remote (model server). */
  type?: 'logistic' | 'remote' | string | null;
  /** Model name on the model server (remote only). */
  name?: string | null;
  version?: string;
  intercept: number;
  coefficients: Record<string, number>;
  /** Ordered model inputs (remote only). */
  features?: string[] | null;
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
  explanation?: Explanation;
}

export interface Contribution {
  feature: string;
  value: number;
  weight?: number;
  contribution?: number;
}

/** priority = propensity × value × priorityLever, and where the propensity came from. */
export interface Explanation {
  scorer?: string;
  modelName?: string;
  modelVersion?: string;
  modelPropensity: number;
  propensity: number;
  value: number;
  priorityLever: number;
  priority: number;
  scoringFallback?: string;
  contributions: Contribution[];
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
  fallbackReason?: string;
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
