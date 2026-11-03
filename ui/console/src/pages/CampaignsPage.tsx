import { useState, type FormEvent } from 'react';
import { campaignApi, catalogApi } from '../api/endpoints';
import type { Promotion } from '../api/types';
import { Empty, ErrorBanner, Field, PageHeader, StatusBadge } from '../components/ui';
import { fromChannelRefs, toChannelRefs } from '../lib/forms';
import { useTenant } from '../lib/tenant';
import { useResource } from '../lib/useResource';

const STATUSES = ['draft', 'active', 'suspended', 'retired'];

interface ActionRow {
  offeringId: string;
  lever: string;
}

interface Draft {
  id?: string;
  name: string;
  description: string;
  lifecycleStatus: string;
  channels: string;
  eligibilityRule: string;
  controlGroupPercent: string;
  maxImpressionsPerDay: string;
  actions: ActionRow[];
}

const EMPTY: Draft = {
  name: '',
  description: '',
  lifecycleStatus: 'active',
  channels: 'web',
  eligibilityRule: '',
  controlGroupPercent: '0',
  maxImpressionsPerDay: '0',
  actions: [{ offeringId: '', lever: '1' }],
};

function toDraft(p: Promotion): Draft {
  return {
    id: p.id,
    name: p.name,
    description: p.description ?? '',
    lifecycleStatus: p.lifecycleStatus ?? 'draft',
    channels: fromChannelRefs(p.channel),
    eligibilityRule: p.eligibilityRule ?? '',
    controlGroupPercent: String(p.controlGroupPercent ?? 0),
    maxImpressionsPerDay: String(p.maxImpressionsPerDay ?? 0),
    actions: p.action.map((a) => ({ offeringId: a.actionObjectId, lever: String(a.priorityLever ?? 1) })),
  };
}

export function CampaignsPage() {
  const { tenant } = useTenant();
  const campaigns = useResource(() => campaignApi.list(tenant), [tenant]);
  const offerings = useResource(() => catalogApi.list(tenant), [tenant]);
  const [draft, setDraft] = useState<Draft>();
  const [error, setError] = useState<Error | string>();
  const [saving, setSaving] = useState(false);

  const offeringName = (id: string) => offerings.data?.items.find((o) => o.id === id)?.name ?? id;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft) return;
    const actions = draft.actions.filter((a) => a.offeringId);
    if (actions.length === 0) {
      setError('Pick at least one action for the campaign.');
      return;
    }
    const body: Promotion = {
      name: draft.name.trim(),
      description: draft.description.trim() || undefined,
      lifecycleStatus: draft.lifecycleStatus,
      channel: toChannelRefs(draft.channels),
      eligibilityRule: draft.eligibilityRule.trim() || null,
      controlGroupPercent: Number(draft.controlGroupPercent) || 0,
      maxImpressionsPerDay: Number(draft.maxImpressionsPerDay) || 0,
      action: actions.map((a) => ({ actionObjectId: a.offeringId, priorityLever: Number(a.lever) || 1 })),
    };
    setSaving(true);
    try {
      if (draft.id) await campaignApi.update(tenant, draft.id, body);
      else await campaignApi.create(tenant, body);
      setDraft(undefined);
      setError(undefined);
      await campaigns.reload();
    } catch (err) {
      setError(err as Error);
    } finally {
      setSaving(false);
    }
  };

  const setStatus = async (p: Promotion, lifecycleStatus: string) => {
    try {
      await campaignApi.update(tenant, p.id!, { lifecycleStatus });
      await campaigns.reload();
    } catch (err) {
      setError(err as Error);
    }
  };

  const remove = async (p: Promotion) => {
    if (!window.confirm(`Delete campaign "${p.name}"?`)) return;
    try {
      await campaignApi.remove(tenant, p.id!);
      await campaigns.reload();
    } catch (err) {
      setError(err as Error);
    }
  };

  const updateAction = (index: number, patch: Partial<ActionRow>) =>
    draft && setDraft({ ...draft, actions: draft.actions.map((a, i) => (i === index ? { ...a, ...patch } : a)) });

  return (
    <>
      <PageHeader
        title="Campaigns"
        subtitle="Who gets which actions, where and how often (TMF671 promotions)."
        actions={
          <button className="btn btn-primary" onClick={() => setDraft({ ...EMPTY })}>
            New campaign
          </button>
        }
      />
      <ErrorBanner error={error ?? campaigns.error} onDismiss={() => setError(undefined)} />

      {draft && (
        <form className="card form" onSubmit={save} aria-label="Campaign form">
          <h2>{draft.id ? 'Edit campaign' : 'New campaign'}</h2>
          <div className="grid">
            <Field label="Name">
              <input required value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </Field>
            <Field label="Status" hint="Only active campaigns are used in decisions.">
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
            <Field label="Channels" hint="Comma separated. Leave empty for every channel.">
              <input value={draft.channels} onChange={(e) => setDraft({ ...draft, channels: e.target.value })} />
            </Field>
            <Field
              label="Eligibility rule"
              hint="Over request attributes, e.g. tenureMonths >= 12 and segment == 'gold'. Empty means everyone."
            >
              <input
                className="mono"
                value={draft.eligibilityRule}
                onChange={(e) => setDraft({ ...draft, eligibilityRule: e.target.value })}
              />
            </Field>
            <Field label="Control group %" hint="Share of customers held out to measure lift (0 to 50).">
              <input
                type="number"
                min="0"
                max="50"
                step="any"
                value={draft.controlGroupPercent}
                onChange={(e) => setDraft({ ...draft, controlGroupPercent: e.target.value })}
              />
            </Field>
            <Field label="Max impressions per day" hint="Per customer. 0 means no cap.">
              <input
                type="number"
                min="0"
                value={draft.maxImpressionsPerDay}
                onChange={(e) => setDraft({ ...draft, maxImpressionsPerDay: e.target.value })}
              />
            </Field>
          </div>

          <fieldset>
            <legend>Actions</legend>
            <p className="muted small">The priority lever multiplies an action's score: above 1 boosts it, below 1 damps it.</p>
            {draft.actions.map((a, index) => (
              <div className="kv-row" key={index}>
                <select
                  aria-label={`Action ${index + 1}`}
                  value={a.offeringId}
                  onChange={(e) => updateAction(index, { offeringId: e.target.value })}
                >
                  <option value="">Choose an action…</option>
                  {offerings.data?.items.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name} ({o.lifecycleStatus})
                    </option>
                  ))}
                  {a.offeringId && !offerings.data?.items.some((o) => o.id === a.offeringId) && (
                    <option value={a.offeringId}>{a.offeringId} (not in catalog)</option>
                  )}
                </select>
                <input
                  aria-label={`Priority lever ${index + 1}`}
                  type="number"
                  min="0.01"
                  step="any"
                  value={a.lever}
                  onChange={(e) => updateAction(index, { lever: e.target.value })}
                />
                <button
                  type="button"
                  className="btn-icon"
                  aria-label="Remove action"
                  onClick={() => setDraft({ ...draft, actions: draft.actions.filter((_, i) => i !== index) })}
                >
                  ×
                </button>
              </div>
            ))}
            <button
              type="button"
              className="btn-link"
              onClick={() => setDraft({ ...draft, actions: [...draft.actions, { offeringId: '', lever: '1' }] })}
            >
              + Add action
            </button>
          </fieldset>

          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setDraft(undefined)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save campaign'}
            </button>
          </div>
        </form>
      )}

      {campaigns.data && campaigns.data.items.length === 0 ? (
        <Empty>No campaigns yet. Create actions first, then a campaign that recommends them.</Empty>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Rule</th>
                <th>Actions</th>
                <th>Channels</th>
                <th className="num">Control</th>
                <th className="num">Cap/day</th>
                <th aria-label="Row actions" />
              </tr>
            </thead>
            <tbody>
              {campaigns.data?.items.map((p) => (
                <tr key={p.id}>
                  <td>
                    <div className="strong">{p.name}</div>
                    <div className="muted small mono">{p.id}</div>
                  </td>
                  <td>
                    <StatusBadge status={p.lifecycleStatus} />
                  </td>
                  <td className="mono small">{p.eligibilityRule || <span className="muted">everyone</span>}</td>
                  <td className="small">
                    {p.action.map((a) => (
                      <div key={a.actionObjectId}>
                        {offeringName(a.actionObjectId)}
                        {a.priorityLever !== 1 && <span className="muted"> ×{a.priorityLever}</span>}
                      </div>
                    ))}
                  </td>
                  <td>{fromChannelRefs(p.channel) || <span className="muted">all</span>}</td>
                  <td className="num">{p.controlGroupPercent}%</td>
                  <td className="num">{p.maxImpressionsPerDay || '–'}</td>
                  <td className="row-actions">
                    {p.lifecycleStatus === 'active' ? (
                      <button className="btn btn-small" onClick={() => setStatus(p, 'suspended')}>
                        Suspend
                      </button>
                    ) : (
                      <button className="btn btn-small" onClick={() => setStatus(p, 'active')}>
                        Activate
                      </button>
                    )}
                    <button className="btn btn-small" onClick={() => setDraft(toDraft(p))}>
                      Edit
                    </button>
                    <button className="btn btn-small btn-danger" onClick={() => remove(p)}>
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
