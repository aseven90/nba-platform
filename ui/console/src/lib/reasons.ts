/** Plain-language meaning of the reason codes the decision pipeline records. */
const REASONS: Record<string, string> = {
  MODEL_DEFAULT_PRIOR: 'No propensity model on this action, so the default prior was used.',
  EXPLORING_NEW_ARM: 'No response data yet; the score is a draw that explores this action.',
  POSTERIOR_SAMPLED: 'Score drawn from what this campaign and action have earned so far.',
  SAFE_DEFAULT: 'Safe default answer: the normal decision could not be made.',
  PIPELINE_ERROR: 'A pipeline stage failed.',
  DEADLINE_EXCEEDED: 'The decision took longer than its deadline.',
  CIRCUIT_OPEN: 'Recent decisions kept failing, so the circuit breaker is open.',
  BULKHEAD_FULL: 'Too many decisions were already running on this channel.',
  OFFERING_UNKNOWN: 'The campaign names an action that is not in the catalog.',
  OFFERING_NOT_LIVE: 'The action is not Active or Launched, or is outside its validity dates.',
  OFFERING_CHANNEL_MISMATCH: 'The action is not offered on this channel.',
  OPTED_OUT: 'The party has opted out of all contact.',
  NO_CHANNEL_CONSENT: 'The party has not consented to this channel.',
  RULE_NOT_MET: 'The campaign eligibility rule evaluated to false.',
  RULE_NOT_EVALUABLE: 'The eligibility rule could not be evaluated, usually because an attribute it uses is missing.',
  CONTACT_CAP_REACHED: "The party already saw this campaign the maximum number of times in the last 24 hours.",
  CONTROL_GROUP: "The party is in this campaign's control group, held out to measure lift.",
  DUPLICATE_OFFERING: 'Another campaign offers the same action with a higher priority.',
  RANKED_OUT: 'Eligible, but ranked below the number of actions requested.',
};

export function describeReason(code: string): string {
  if (code.startsWith('MODEL_')) return `Scored by propensity model ${code.slice('MODEL_'.length)}.`;
  return REASONS[code] ?? code;
}
