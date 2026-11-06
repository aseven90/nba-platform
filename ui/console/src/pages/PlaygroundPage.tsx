import { useMemo, useState, type FormEvent } from 'react';
import { campaignApi, decisionApi, interactionApi } from '../api/endpoints';
import type { Explanation, Outcome, Recommendation, RecommendationItem } from '../api/types';
import { KeyValueEditor } from '../components/KeyValueEditor';
import { Chip, Empty, ErrorBanner, Field, Notice, PageHeader } from '../components/ui';
import { attributesInRule, toCharacteristics, type KeyValueRow } from '../lib/forms';
import { describeReason } from '../lib/reasons';
import { useTenant } from '../lib/tenant';
import { useResource } from '../lib/useResource';

const OUTCOMES: { outcome: Outcome; label: string }[] = [
  { outcome: 'IMPRESSION', label: 'Shown' },
  { outcome: 'ACCEPTED', label: 'Accepted' },
  { outcome: 'DECLINED', label: 'Declined' },
];

const format = (n?: number, digits = 3) => (n === undefined ? '–' : n.toFixed(digits));

/** How the item's priority was built: the model behind the propensity, then propensity × value × lever. */
function WhyThisScore({ explanation: e }: { explanation: Explanation }) {
  const model = e.scoringFallback
    ? `default prior (${describeReason(e.scoringFallback)})`
    : e.scorer === 'prior'
      ? 'default prior (no model on this action)'
      : `${e.scorer} model ${[e.modelName, e.modelVersion].filter(Boolean).join(' ')}`;
  return (
    <details className="why">
      <summary>Why this score</summary>
      <p className="small">
        Propensity from <span className="strong">{model}</span>: {format(e.modelPropensity)}
        {Math.abs(e.propensity - e.modelPropensity) > 1e-9 && <>, adjusted by learning to {format(e.propensity)}</>}.
      </p>
      <p className="small mono">
        priority = {format(e.propensity)} × value {format(e.value, 2)} × lever {format(e.priorityLever, 2)} ={' '}
        {format(e.priority, 2)}
      </p>
      {e.contributions.length > 0 && (
        <table className="small contributions">
          <thead>
            <tr>
              <th>Feature</th>
              <th className="num">Value</th>
              <th className="num">Weight</th>
              <th className="num">Effect</th>
            </tr>
          </thead>
          <tbody>
            {e.contributions.map((c) => (
              <tr key={c.feature}>
                <td className="mono">{c.feature}</td>
                <td className="num mono">{format(c.value, 2)}</td>
                <td className="num mono">{format(c.weight)}</td>
                <td className="num mono">
                  {c.contribution === undefined ? '–' : `${c.contribution >= 0 ? '+' : ''}${format(c.contribution)}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </details>
  );
}

export function PlaygroundPage() {
  const { tenant } = useTenant();
  const campaigns = useResource(() => campaignApi.list(tenant), [tenant]);

  const [partyId, setPartyId] = useState('party-42');
  const [channel, setChannel] = useState('web');
  const [maxItems, setMaxItems] = useState('3');
  const [attributes, setAttributes] = useState<KeyValueRow[]>([{ key: 'tenureMonths', value: '24' }]);
  const [result, setResult] = useState<Recommendation>();
  const [error, setError] = useState<Error>();
  const [running, setRunning] = useState(false);
  const [recorded, setRecorded] = useState<Record<string, Outcome[]>>({});

  // Attributes used by active campaign rules, offered as one-click inputs.
  const suggested = useMemo(() => {
    const names = new Set<string>();
    for (const c of campaigns.data?.items ?? []) {
      if (c.lifecycleStatus === 'active') attributesInRule(c.eligibilityRule).forEach((n) => names.add(n));
    }
    return [...names].filter((n) => !attributes.some((row) => row.key === n));
  }, [campaigns.data, attributes]);

  const channels = useMemo(() => {
    const set = new Set(['web', 'app', 'contact-center']);
    campaigns.data?.items.forEach((c) => c.channel?.forEach((ch) => set.add(ch.id)));
    return [...set];
  }, [campaigns.data]);

  const decide = async (e?: FormEvent) => {
    e?.preventDefault();
    setRunning(true);
    try {
      const recommendation = await decisionApi.recommend(tenant, {
        relatedParty: [{ id: partyId.trim(), role: 'customer' }],
        channel: { id: channel },
        characteristic: toCharacteristics(attributes),
        maxItems: Number(maxItems) || 3,
      });
      setResult(recommendation);
      setRecorded({});
      setError(undefined);
    } catch (err) {
      setError(err as Error);
    } finally {
      setRunning(false);
    }
  };

  const record = async (item: RecommendationItem, outcome: Outcome) => {
    if (!result) return;
    try {
      await interactionApi.record(tenant, {
        partyId: result.relatedParty[0].id,
        channel: result.channel.id,
        decisionId: result.id,
        offeringId: item.productOffering.id,
        campaignId: item.promotion?.id,
        outcome,
      });
      setRecorded((prev) => ({ ...prev, [item.id]: [...new Set([...(prev[item.id] ?? []), outcome])] }));
    } catch (err) {
      setError(err as Error);
    }
  };

  return (
    <>
      <PageHeader
        title="Decision playground"
        subtitle="Ask for the next best actions for one customer and see why each one was chosen or dropped."
      />
      <ErrorBanner error={error} onDismiss={() => setError(undefined)} />

      <div className="playground">
        <form className="card form" onSubmit={decide} aria-label="Decision request">
          <h2>Request</h2>
          <Field label="Customer (party) id">
            <input required value={partyId} onChange={(e) => setPartyId(e.target.value)} />
          </Field>
          <div className="grid">
            <Field label="Channel">
              <select value={channel} onChange={(e) => setChannel(e.target.value)}>
                {channels.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </Field>
            <Field label="Max actions">
              <input type="number" min="1" max="20" value={maxItems} onChange={(e) => setMaxItems(e.target.value)} />
            </Field>
          </div>
          <Field
            label="Customer attributes"
            hint="Sent as TMF characteristics. Numbers and true/false are typed automatically. Try optOut = true or consent.web = false."
          >
            <KeyValueEditor
              label="Attribute"
              rows={attributes}
              onChange={setAttributes}
              keyPlaceholder="name"
              valuePlaceholder="value"
              addLabel="Add attribute"
            />
          </Field>
          {suggested.length > 0 && (
            <div className="suggest">
              <span className="muted small">Used by active campaign rules:</span>
              {suggested.map((name) => (
                <button
                  type="button"
                  key={name}
                  className="chip chip-button"
                  onClick={() => setAttributes([...attributes, { key: name, value: '' }])}
                >
                  + {name}
                </button>
              ))}
            </div>
          )}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={running}>
              {running ? 'Deciding…' : 'Get next best actions'}
            </button>
          </div>
        </form>

        <section className="results" aria-live="polite">
          {!result ? (
            <Empty>Run a decision to see the ranked actions here.</Empty>
          ) : (
            <>
              <div className="result-meta">
                <span>
                  Decision <span className="mono">{result.id}</span>
                </span>
                {result.fallback && <Chip tone="warn">safe default</Chip>}
              </div>
              {result.fallback && (
                <Notice tone="warning">
                  The decision service could not run its normal pipeline and returned the configured safe default
                  actions{result.fallbackReason ? ` (${describeReason(result.fallbackReason)})` : ''}. Safe defaults
                  still respect opt-out and channel consent.
                </Notice>
              )}

              <h2>Recommended</h2>
              {result.recommendationItem.length === 0 ? (
                <Empty>
                  Nothing was eligible. The dropped candidates below say why. If you just created a campaign, give
                  the decision service a second to receive it and try again.
                </Empty>
              ) : (
                <ol className="ranked">
                  {result.recommendationItem.map((item) => (
                    <li key={item.id} className="card ranked-item">
                      <div className="rank">{item.priority}</div>
                      <div className="ranked-body">
                        <div className="ranked-title">
                          <span className="strong">{item.productOffering.name ?? item.productOffering.id}</span>
                          {item.promotion && (
                            <span className="muted"> via {item.promotion.name ?? item.promotion.id}</span>
                          )}
                        </div>
                        {!result.fallback && (
                          <dl className="scores">
                            <div>
                              <dt>Score</dt>
                              <dd className="mono">{format(item.score, 2)}</dd>
                            </div>
                            <div>
                              <dt>Propensity</dt>
                              <dd className="mono">{format(item.propensity)}</dd>
                            </div>
                          </dl>
                        )}
                        {!result.fallback && item.explanation && <WhyThisScore explanation={item.explanation} />}
                        <div className="chips">
                          {item.reasonCode.map((code) => (
                            <Chip key={code} title={describeReason(code)}>
                              {code}
                            </Chip>
                          ))}
                        </div>
                        <ul className="reasons">
                          {item.reasonCode.map((code) => (
                            <li key={code}>{describeReason(code)}</li>
                          ))}
                        </ul>
                        <div className="outcomes" role="group" aria-label={`Record response to ${item.productOffering.name ?? item.productOffering.id}`}>
                          {OUTCOMES.map(({ outcome, label }) => {
                            const done = recorded[item.id]?.includes(outcome);
                            return (
                              <button
                                key={outcome}
                                className={`btn btn-small ${done ? 'btn-done' : ''}`}
                                onClick={() => record(item, outcome)}
                                disabled={done}
                              >
                                {done ? `✓ ${label}` : label}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    </li>
                  ))}
                </ol>
              )}

              <h2>Dropped candidates</h2>
              {(result.suppressedItem ?? []).length === 0 ? (
                <p className="muted">No candidates were dropped.</p>
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Action</th>
                        <th>Campaign</th>
                        <th>Stage</th>
                        <th>Why</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.suppressedItem!.map((s, i) => (
                        <tr key={i}>
                          <td>{s.productOffering.name ?? s.productOffering.id}</td>
                          <td>{s.promotion?.name ?? s.promotion?.id ?? '–'}</td>
                          <td className="mono small">{s.stage}</td>
                          <td>
                            <Chip tone="muted">{s.reasonCode}</Chip>
                            <div className="small muted">{describeReason(s.reasonCode)}</div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="muted small">
                Scores include exploration (Thompson sampling), so they vary a little from one run to the next.
              </p>
            </>
          )}
        </section>
      </div>
    </>
  );
}
