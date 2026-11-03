import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { TENANT_PATTERN, useTenant } from '../lib/tenant';

const NAV = [
  { to: '/', label: 'Overview', end: true },
  { to: '/actions', label: 'Actions' },
  { to: '/campaigns', label: 'Campaigns' },
  { to: '/playground', label: 'Decision playground' },
  { to: '/interactions', label: 'Interactions' },
];

export function Layout() {
  const { tenant, setTenant } = useTenant();
  const [draft, setDraft] = useState(tenant);
  const valid = TENANT_PATTERN.test(draft);

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden>
            ◆
          </span>
          <span>NBA Console</span>
        </div>
        <nav>
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <form
          className="tenant"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) setTenant(draft);
          }}
        >
          <label htmlFor="tenant">Tenant</label>
          <div className="tenant-row">
            <input id="tenant" value={draft} onChange={(e) => setDraft(e.target.value)} aria-invalid={!valid} />
            <button type="submit" className="btn btn-small" disabled={!valid || draft === tenant}>
              Switch
            </button>
          </div>
          <small>Every request is sent with X-Tenant-Id: {tenant}</small>
        </form>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
