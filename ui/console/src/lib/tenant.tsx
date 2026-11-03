import { createContext, useContext, useState, type ReactNode } from 'react';

const STORAGE_KEY = 'nba-console.tenant';
const DEFAULT_TENANT = 'demo';

function readStored(): string {
  try {
    return window.localStorage.getItem(STORAGE_KEY) || DEFAULT_TENANT;
  } catch {
    return DEFAULT_TENANT;
  }
}

interface TenantState {
  tenant: string;
  setTenant: (tenant: string) => void;
}

const TenantContext = createContext<TenantState>({ tenant: DEFAULT_TENANT, setTenant: () => {} });

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenant, setTenantState] = useState(readStored);
  const setTenant = (value: string) => {
    setTenantState(value);
    try {
      window.localStorage.setItem(STORAGE_KEY, value);
    } catch {
      // Storage unavailable (private mode); the choice lasts for this page only.
    }
  };
  return <TenantContext.Provider value={{ tenant, setTenant }}>{children}</TenantContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const useTenant = () => useContext(TenantContext);

export const TENANT_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
