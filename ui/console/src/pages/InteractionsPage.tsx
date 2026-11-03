import { useState } from 'react';
import { interactionApi } from '../api/endpoints';
import { Chip, Empty, ErrorBanner, PageHeader } from '../components/ui';
import { useTenant } from '../lib/tenant';
import { useResource } from '../lib/useResource';

const TONE: Record<string, string> = { ACCEPTED: 'good', DECLINED: 'bad', IMPRESSION: 'muted', IGNORED: 'muted' };

export function InteractionsPage() {
  const { tenant } = useTenant();
  const [filter, setFilter] = useState('');
  const [partyId, setPartyId] = useState('');
  const interactions = useResource(() => interactionApi.list(tenant, partyId), [tenant, partyId]);

  return (
    <>
      <PageHeader title="Interactions" subtitle="Responses recorded against decisions (TMF683 party interactions)." />
      <ErrorBanner error={interactions.error} />
      <form
        className="toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          setPartyId(filter.trim());
        }}
      >
        <input aria-label="Filter by customer id" placeholder="Customer id" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <button className="btn" type="submit">
          Filter
        </button>
        <button className="btn" type="button" onClick={() => void interactions.reload()}>
          Refresh
        </button>
      </form>
      {interactions.data && interactions.data.items.length === 0 ? (
        <Empty>No interactions yet. Record one from the decision playground.</Empty>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Customer</th>
                <th>Channel</th>
                <th>Outcome</th>
                <th>Action</th>
                <th>Campaign</th>
                <th>Decision</th>
              </tr>
            </thead>
            <tbody>
              {interactions.data?.items.map((i) => (
                <tr key={i.id}>
                  <td className="small">{i.interactionDate ? new Date(i.interactionDate).toLocaleString() : '–'}</td>
                  <td>{i.relatedParty[0]?.id}</td>
                  <td>{i.channel[0]?.id}</td>
                  <td>
                    <Chip tone={TONE[i.outcome]}>{i.outcome}</Chip>
                  </td>
                  <td className="mono small">{i.productOffering.id}</td>
                  <td className="mono small">{i.promotion?.id ?? '–'}</td>
                  <td className="mono small">{i.decisionId ?? '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
