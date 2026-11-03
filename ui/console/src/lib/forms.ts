import type { Characteristic, Ref } from '../api/types';

export interface KeyValueRow {
  key: string;
  value: string;
}

/**
 * Converts editor rows to TMF characteristics, typing values the way a caller would send them:
 * numbers as numbers, true/false as booleans, everything else as text. Rows without a name are dropped.
 */
export function toCharacteristics(rows: KeyValueRow[]): Characteristic[] {
  return rows
    .filter((row) => row.key.trim() !== '')
    .map((row) => ({ name: row.key.trim(), value: parseScalar(row.value) }));
}

export function parseScalar(raw: string): string | number | boolean {
  const value = raw.trim();
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (value !== '' && !Number.isNaN(Number(value))) return Number(value);
  return value;
}

/** Numeric coefficients from editor rows; rows with a non-numeric weight are reported, not silently dropped. */
export function toCoefficients(rows: KeyValueRow[]): { coefficients: Record<string, number>; invalid: string[] } {
  const coefficients: Record<string, number> = {};
  const invalid: string[] = [];
  for (const row of rows) {
    const key = row.key.trim();
    if (key === '') continue;
    const weight = Number(row.value);
    if (row.value.trim() === '' || Number.isNaN(weight)) invalid.push(key);
    else coefficients[key] = weight;
  }
  return { coefficients, invalid };
}

export function fromRecord(record: Record<string, number> | undefined): KeyValueRow[] {
  return Object.entries(record ?? {}).map(([key, value]) => ({ key, value: String(value) }));
}

/** "web, app" -> [{id: "web"}, {id: "app"}] */
export function toChannelRefs(text: string): Ref[] {
  return text
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part !== '')
    .map((id) => ({ id }));
}

export function fromChannelRefs(refs: Ref[] | undefined): string {
  return (refs ?? []).map((ref) => ref.id).join(', ');
}

/** Attribute names a rule refers to, so the playground can offer them as inputs. */
export function attributesInRule(rule: string | null | undefined): string[] {
  if (!rule) return [];
  const withoutStrings = rule.replace(/'[^']*'|"[^"]*"/g, ' ');
  const reserved = new Set(['and', 'or', 'not', 'true', 'false', 'null', 'eq', 'ne', 'lt', 'gt', 'le', 'ge', 'div', 'mod']);
  const names = withoutStrings.match(/[A-Za-z_][A-Za-z0-9_]*/g) ?? [];
  return [...new Set(names.filter((name) => !reserved.has(name.toLowerCase())))];
}
