import { useState } from 'react';
import { Link } from 'react-router-dom';
import { campaignApi, catalogApi, interactionApi } from '../api/endpoints';
import { ErrorBanner, Notice, PageHeader } from '../components/ui';
import { type DemoLoadResult, loadDemoData } from '../lib/demoData';
import { useTenant } from '../lib/tenant';
import { useResource } from '../lib/useResource';

export function OverviewPage() {
  const { tenant } = useTenant();
  const [seeding, setSeeding] = useState(false);
  const [seedError, setSeedError] = useState<Error>();
  const [seeded, setSeeded] = useState<string>();

  const counts = useResource(async () => {
    const settle = async (fn: () => Promise<{ total: number }>) => {
      try {
        return { total: (await fn()).total };
      } catch (e) {
        return { error: (e as Error).message };
      }
    };
    const [actions, campaigns, interactions] = await Promise.all([
      settle(() => catalogApi.list(tenant)),
      settle(() => campaignApi.list(tenant)),
      settle(() => interactionApi.list(tenant)),
    ]);
    return { actions, campaigns, interactions };
  }, [tenant]);

  const seed = async () => {
    setSeeding(true);
    setSeedError(undefined);
    try {
      setSeeded(describeSeed(await loadDemoData(tenant)));
      await counts.reload();
    } catch (e) {
      setSeedError(e as Error);
    } finally {
      setSeeding(false);
    }
  };

  const tiles: { label: string; to: string; value?: { total?: number; error?: string } }[] = [
    { label: 'Actions', to: '/actions', value: counts.data?.actions },
    { label: 'Campaigns', to: '/campaigns', value: counts.data?.campaigns },
    { label: 'Interactions', to: '/interactions', value: counts.data?.interactions },
  ];

  return (
    <>
      <PageHeader
        title="Overview"
        subtitle={`Tenant ${tenant}. Define actions, target them with campaigns, then ask for the next best action.`}
        actions={
          <button className="btn btn-primary" onClick={seed} disabled={seeding}>
            {seeding ? 'Loading…' : 'Load demo data'}
          </button>
        }
      />
      <ErrorBanner error={seedError} onDismiss={() => setSeedError(undefined)} />
      {seeded && <Notice tone="success">{seeded} Open the <Link to="/playground">decision playground</Link> to try them.</Notice>}

      <section className="tiles">
        {tiles.map((tile) => (
          <Link key={tile.label} to={tile.to} className="tile">
            <span className="tile-label">{tile.label}</span>
            {counts.loading && !counts.data ? (
              <span className="tile-value muted">…</span>
            ) : tile.value?.error ? (
              <span className="tile-error">{tile.value.error}</span>
            ) : (
              <span className="tile-value">{tile.value?.total ?? 0}</span>
            )}
          </Link>
        ))}
      </section>

      <section className="card">
        <h2>How to test a decision</h2>
        <ol className="steps">
          <li>
            <strong>Actions</strong> are what can be recommended: an offer, a message, a service step. Each has a
            business value and an optional propensity model.
          </li>
          <li>
            <strong>Campaigns</strong> pick which actions to show, to whom (an eligibility rule over customer
            attributes), on which channels, how often, and with what control group. Only <em>active</em> campaigns
            are used.
          </li>
          <li>
            The <strong>decision playground</strong> asks for the next best actions for a customer. It shows the
            ranking, the score behind it, and why every other candidate was dropped.
          </li>
          <li>
            Record what the customer did (shown, accepted, declined). Impressions count towards contact caps, and
            accepts teach the decision service which actions work.
          </li>
        </ol>
        <p className="muted">
          Changes reach the decision service through events, usually within a second. If a new campaign does not show
          up yet, try the decision again.
        </p>
      </section>
    </>
  );
}

function describeSeed({ created, existing }: DemoLoadResult): string {
  if (created.actions === 0 && created.campaigns === 0) return 'Demo data is already loaded; nothing was added.';
  const made = `Created ${created.actions} actions and ${created.campaigns} active campaigns.`;
  const kept = existing.actions + existing.campaigns;
  if (kept === 0) return made;
  return `${made} ${kept} demo ${kept === 1 ? 'record was' : 'records were'} already there and kept as is.`;
}
