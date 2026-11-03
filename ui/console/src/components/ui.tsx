import type { ReactNode } from 'react';

export function ErrorBanner({ error, onDismiss }: { error?: Error | string; onDismiss?: () => void }) {
  if (!error) return null;
  const message = typeof error === 'string' ? error : error.message;
  return (
    <div className="banner banner-error" role="alert">
      <span>{message}</span>
      {onDismiss && (
        <button className="btn-link" onClick={onDismiss} aria-label="Dismiss">
          Dismiss
        </button>
      )}
    </div>
  );
}

export function Notice({ children, tone = 'info' }: { children: ReactNode; tone?: 'info' | 'success' | 'warning' }) {
  return <div className={`banner banner-${tone}`}>{children}</div>;
}

const STATUS_TONE: Record<string, string> = {
  active: 'good',
  launched: 'good',
  draft: 'muted',
  'in design': 'muted',
  'in test': 'muted',
  suspended: 'warn',
  retired: 'bad',
  obsolete: 'bad',
};

export function StatusBadge({ status }: { status?: string }) {
  const tone = STATUS_TONE[(status ?? '').toLowerCase()] ?? 'muted';
  return <span className={`badge badge-${tone}`}>{status ?? 'unknown'}</span>;
}

export function Chip({ children, title, tone }: { children: ReactNode; title?: string; tone?: string }) {
  return (
    <span className={`chip ${tone ? `chip-${tone}` : ''}`} title={title}>
      {children}
    </span>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}
