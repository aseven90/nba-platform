import { useState, type FormEvent } from 'react';
import { catalogApi } from '../api/endpoints';
import type { ProductOffering } from '../api/types';
import { KeyValueEditor } from '../components/KeyValueEditor';
import { Empty, ErrorBanner, Field, PageHeader, StatusBadge } from '../components/ui';
import { fromChannelRefs, fromRecord, toChannelRefs, toCoefficients, type KeyValueRow } from '../lib/forms';
import { useTenant } from '../lib/tenant';
import { useResource } from '../lib/useResource';

const STATUSES = ['In design', 'In test', 'Active', 'Launched', 'Retired'];

interface Draft {
  id?: string;
  name: string;
  description: string;
  lifecycleStatus: string;
  category: string;
  channels: string;
  businessValue: string;
  modelEnabled: boolean;
  modelVersion: string;
  intercept: string;
  coefficients: KeyValueRow[];
}

const EMPTY: Draft = {
  name: '',
  description: '',
  lifecycleStatus: 'Active',
  category: '',
  channels: 'web',
  businessValue: '0',
  modelEnabled: true,
  modelVersion: 'v1',
  intercept: '-2',
  coefficients: [{ key: 'tenureMonths', value: '0.05' }],
};

function toDraft(o: ProductOffering): Draft {
  return {
    id: o.id,
    name: o.name,
    description: o.description ?? '',
    lifecycleStatus: o.lifecycleStatus ?? 'In design',
    category: o.category?.[0]?.name ?? '',
    channels: fromChannelRefs(o.channel),
    businessValue: String(o.businessValue ?? 0),
    modelEnabled: !!o.propensityModel,
    modelVersion: o.propensityModel?.version ?? 'v1',
    intercept: String(o.propensityModel?.intercept ?? -2),
    coefficients: fromRecord(o.propensityModel?.coefficients),
  };
}

export function ActionsPage() {
  const { tenant } = useTenant();
  const offerings = useResource(() => catalogApi.list(tenant), [tenant]);
  const [draft, setDraft] = useState<Draft>();
  const [error, setError] = useState<Error | string>();
  const [saving, setSaving] = useState(false);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft) return;
    const { coefficients, invalid } = toCoefficients(draft.coefficients);
    if (invalid.length > 0) {
      setError(`Coefficient weights must be numbers: ${invalid.join(', ')}`);
      return;
    }
    const body: ProductOffering = {
      name: draft.name.trim(),
      description: draft.description.trim() || undefined,
      lifecycleStatus: draft.lifecycleStatus,
      category: draft.category.trim() ? [{ name: draft.category.trim() }] : [],
      channel: toChannelRefs(draft.channels),
      businessValue: Number(draft.businessValue) || 0,
      propensityModel: draft.modelEnabled
        ? { version: draft.modelVersion.trim() || undefined, intercept: Number(draft.intercept) || 0, coefficients }
        : null,
    };
    setSaving(true);
    try {
      if (draft.id) await catalogApi.update(tenant, draft.id, body);
      else await catalogApi.create(tenant, body);
      setDraft(undefined);
      setError(undefined);
      await offerings.reload();
    } catch (err) {
      setError(err as Error);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (o: ProductOffering) => {
    if (!window.confirm(`Delete "${o.name}"? Campaigns that use it will skip it.`)) return;
    try {
      await catalogApi.remove(tenant, o.id!);
      await offerings.reload();
    } catch (err) {
      setError(err as Error);
    }
  };

  return (
    <>
      <PageHeader
        title="Actions"
        subtitle="Everything that can be recommended (TMF620 product offerings)."
        actions={
          <button className="btn btn-primary" onClick={() => setDraft({ ...EMPTY })}>
            New action
          </button>
        }
      />
      <ErrorBanner error={error ?? offerings.error} onDismiss={() => setError(undefined)} />

      {draft && (
        <form className="card form" onSubmit={save} aria-label="Action form">
          <h2>{draft.id ? 'Edit action' : 'New action'}</h2>
          <div className="grid">
            <Field label="Name">
              <input required value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </Field>
            <Field label="Status" hint="Decisions only use Active or Launched actions.">
              <select
                value={draft.lifecycleStatus}
                onChange={(e) => setDraft({ ...draft, lifecycleStatus: e.target.value })}
              >
                {STATUSES.map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </Field>
            <Field label="Description">
              <input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
            </Field>
            <Field label="Category">
              <input value={draft.category} onChange={(e) => setDraft({ ...draft, category: e.target.value })} />
            </Field>
            <Field label="Channels" hint="Comma separated. Leave empty for every channel.">
              <input value={draft.channels} onChange={(e) => setDraft({ ...draft, channels: e.target.value })} />
            </Field>
            <Field label="Business value" hint="Value of a positive response. 0 for non-monetary actions.">
              <input
                type="number"
                min="0"
                step="any"
                value={draft.businessValue}
                onChange={(e) => setDraft({ ...draft, businessValue: e.target.value })}
              />
            </Field>
          </div>

          <fieldset className="model">
            <legend>
              <label className="inline">
                <input
                  type="checkbox"
                  checked={draft.modelEnabled}
                  onChange={(e) => setDraft({ ...draft, modelEnabled: e.target.checked })}
                />
                Propensity model
              </label>
            </legend>
            {draft.modelEnabled ? (
              <>
                <p className="muted small">
                  Likelihood of a positive response = sigmoid(intercept + Σ weight × attribute). Attributes come from
                  the decision request.
                </p>
                <div className="grid">
                  <Field label="Model version">
                    <input
                      value={draft.modelVersion}
                      onChange={(e) => setDraft({ ...draft, modelVersion: e.target.value })}
                    />
                  </Field>
                  <Field label="Intercept">
                    <input
                      type="number"
                      step="any"
                      value={draft.intercept}
                      onChange={(e) => setDraft({ ...draft, intercept: e.target.value })}
                    />
                  </Field>
                </div>
                <KeyValueEditor
                  label="Coefficient"
                  rows={draft.coefficients}
                  onChange={(coefficients) => setDraft({ ...draft, coefficients })}
                  keyPlaceholder="attribute, e.g. tenureMonths"
                  valuePlaceholder="weight, e.g. 0.05"
                  addLabel="Add coefficient"
                />
              </>
            ) : (
              <p className="muted small">Without a model the decision service uses a default propensity of 5%.</p>
            )}
          </fieldset>

          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setDraft(undefined)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save action'}
            </button>
          </div>
        </form>
      )}

      {offerings.data && offerings.data.items.length === 0 ? (
        <Empty>No actions yet. Create one, or load the demo data from the overview.</Empty>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Channels</th>
                <th className="num">Value</th>
                <th>Model</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {offerings.data?.items.map((o) => (
                <tr key={o.id}>
                  <td>
                    <div className="strong">{o.name}</div>
                    <div className="muted small mono">{o.id}</div>
                  </td>
                  <td>
                    <StatusBadge status={o.lifecycleStatus} />
                  </td>
                  <td>{fromChannelRefs(o.channel) || <span className="muted">all</span>}</td>
                  <td className="num">{o.businessValue}</td>
                  <td>
                    {o.propensityModel ? (
                      <span className="mono small">
                        {o.propensityModel.version} · {Object.keys(o.propensityModel.coefficients ?? {}).length} features
                      </span>
                    ) : (
                      <span className="muted">default</span>
                    )}
                  </td>
                  <td className="row-actions">
                    <button className="btn btn-small" onClick={() => setDraft(toDraft(o))}>
                      Edit
                    </button>
                    <button className="btn btn-small btn-danger" onClick={() => remove(o)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
