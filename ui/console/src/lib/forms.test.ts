import { describe, expect, it } from 'vitest';
import { attributesInRule, parseScalar, toChannelRefs, toCharacteristics, toCoefficients } from './forms';

describe('form helpers', () => {
  it('types characteristic values like a caller would', () => {
    expect(
      toCharacteristics([
        { key: 'tenureMonths', value: '24' },
        { key: 'optOut', value: 'false' },
        { key: 'segment', value: 'gold' },
        { key: '  ', value: 'ignored' },
      ]),
    ).toEqual([
      { name: 'tenureMonths', value: 24 },
      { name: 'optOut', value: false },
      { name: 'segment', value: 'gold' },
    ]);
    expect(parseScalar('')).toBe('');
    expect(parseScalar(' 0.5 ')).toBe(0.5);
  });

  it('reports non-numeric coefficient weights instead of dropping them', () => {
    expect(
      toCoefficients([
        { key: 'tenureMonths', value: '0.05' },
        { key: 'spend', value: 'abc' },
        { key: 'empty', value: '' },
      ]),
    ).toEqual({ coefficients: { tenureMonths: 0.05 }, invalid: ['spend', 'empty'] });
  });

  it('parses channel lists', () => {
    expect(toChannelRefs('web, app,,')).toEqual([{ id: 'web' }, { id: 'app' }]);
  });

  it('finds the attributes a rule depends on, ignoring literals and operators', () => {
    expect(attributesInRule("tenureMonths >= 12 and segment == 'gold' or not optOut")).toEqual([
      'tenureMonths',
      'segment',
      'optOut',
    ]);
    expect(attributesInRule(null)).toEqual([]);
  });
});
