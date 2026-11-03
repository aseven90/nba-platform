import type { KeyValueRow } from '../lib/forms';

interface Props {
  rows: KeyValueRow[];
  onChange: (rows: KeyValueRow[]) => void;
  keyPlaceholder: string;
  valuePlaceholder: string;
  addLabel: string;
  label: string;
}

export function KeyValueEditor({ rows, onChange, keyPlaceholder, valuePlaceholder, addLabel, label }: Props) {
  const update = (index: number, patch: Partial<KeyValueRow>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  return (
    <div className="kv" role="group" aria-label={label}>
      {rows.map((row, index) => (
        <div className="kv-row" key={index}>
          <input
            aria-label={`${label} name ${index + 1}`}
            placeholder={keyPlaceholder}
            value={row.key}
            onChange={(e) => update(index, { key: e.target.value })}
          />
          <input
            aria-label={`${label} value ${index + 1}`}
            placeholder={valuePlaceholder}
            value={row.value}
            onChange={(e) => update(index, { value: e.target.value })}
          />
          <button
            type="button"
            className="btn-icon"
            aria-label={`Remove ${row.key || 'row'}`}
            onClick={() => onChange(rows.filter((_, i) => i !== index))}
          >
            ×
          </button>
        </div>
      ))}
      <button type="button" className="btn-link" onClick={() => onChange([...rows, { key: '', value: '' }])}>
        + {addLabel}
      </button>
    </div>
  );
}
