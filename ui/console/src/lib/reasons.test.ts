import { describe, expect, it } from 'vitest';
import { describeReason } from './reasons';

describe('describeReason', () => {
  it('prefers known codes over the MODEL_ prefix', () => {
    expect(describeReason('MODEL_DEFAULT_PRIOR')).toMatch(/default prior/);
    expect(describeReason('MODEL_v3')).toBe('Scored by propensity model v3.');
  });

  it('explains scoring fallbacks', () => {
    expect(describeReason('SCORING_FALLBACK')).toMatch(/default prior/);
    expect(describeReason('SCORING_TIMEOUT')).toMatch(/timeout/);
    expect(describeReason('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});
