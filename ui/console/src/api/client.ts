import type { Page } from './types';

/** Error raised for non-2xx responses, carrying the TMF630 error body when there is one. */
export class ApiError extends Error {
  readonly status: number;
  readonly reason?: string;

  constructor(status: number, message: string, reason?: string) {
    super(message);
    this.status = status;
    this.reason = reason;
  }
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string>;
  contentType?: string;
}

/** Calls a TMF API path on the same origin; the console's web server routes each API to its service. */
export async function request<T>(tenant: string, path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
  }
  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers: {
      'X-Tenant-Id': tenant,
      Accept: 'application/json',
      ...(options.body !== undefined ? { 'Content-Type': options.contentType ?? 'application/json' } : {}),
      ...options.headers,
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
  if (!response.ok) {
    throw await toError(response);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Fetches a TMF list endpoint and reads the X-Total-Count header. */
export async function list<T>(
  tenant: string,
  path: string,
  query: Record<string, string | number | undefined> = {},
): Promise<Page<T>> {
  const url = new URL(path, window.location.origin);
  const params: Record<string, string | number | undefined> = { limit: 200, ...query };
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
  }
  const response = await fetch(url, { headers: { 'X-Tenant-Id': tenant, Accept: 'application/json' } });
  if (!response.ok) throw await toError(response);
  const items = (await response.json()) as T[];
  return { items, total: Number(response.headers.get('X-Total-Count') ?? items.length) };
}

async function toError(response: Response): Promise<ApiError> {
  let reason: string | undefined;
  let message = `${response.status} ${response.statusText}`;
  try {
    const body = await response.json();
    reason = body.reason;
    if (body.message) message = body.message;
  } catch {
    // Not a TMF error body (for example, the service is down and the proxy answered).
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      message = 'The service is not reachable. Is it running?';
    }
  }
  return new ApiError(response.status, message, reason);
}
