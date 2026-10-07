export type ApiEnvelope<T> = { message: string; data: T };
type Access = { accessToken?: string; expiresIn?: number; challenge?: string };

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message); this.name = 'ApiError'; }
}

// The access JWT lives only in memory. Auth owns login, refresh, permissions and cookies.
let accessToken: string | null = null;
let expiresAt = 0;
let refreshPromise: Promise<string | null> | null = null;

export function apiUrl(path: string): string {
  if (!path.startsWith('/api/') || /[\\\x00-\x20]/.test(path) || path.split(/[/?#]/).includes('..'))
    throw new Error('Ruta API inválida');
  const base = (process.env.NEXT_PUBLIC_API_BASE_URL || '').replace(/\/$/, '');
  if (base && !/^https:\/\/[a-z0-9.-]+(?::\d+)?$/i.test(base) && !/^http:\/\/localhost(?::\d+)?$/.test(base))
    throw new Error('Configura el origen HTTPS del API Gateway');
  return `${base}${path}`;
}

async function payload<T>(response: Response): Promise<T> {
  let body: Partial<ApiEnvelope<T>> | null = null;
  try { body = await response.json(); } catch { /* Gateway errors may be empty. */ }
  if (!response.ok) throw new ApiError(response.status, body?.message || `HTTP ${response.status}`);
  if (!body || !('data' in body)) throw new ApiError(502, 'Respuesta API inválida');
  return body.data as T;
}

function remember(data: Access): void {
  if (typeof data.accessToken === 'string' && typeof data.expiresIn === 'number') {
    accessToken = data.accessToken;
    expiresAt = Date.now() + data.expiresIn * 1000;
  }
}

export async function sessionRequest<T extends Access>(action: 'login' | 'challenge', body: unknown): Promise<T> {
  const data = await payload<T>(await fetch(apiUrl(`/api/auth/session/${action}`), {
    method: 'POST', credentials: 'include', cache: 'no-store', redirect: 'error',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(15000),
  }));
  remember(data);
  return data;
}

async function refreshAccess(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const data = await payload<Access>(await fetch(apiUrl('/api/auth/session/refresh'), {
          method: 'POST', credentials: 'include', cache: 'no-store', redirect: 'error',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: '{}',
          signal: AbortSignal.timeout(15000),
        }));
        remember(data);
        if (!accessToken) throw new ApiError(502, 'Auth no devolvió un Access Token');
        return accessToken;
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) { accessToken = null; expiresAt = 0; return null; }
        throw error;
      }
    })().finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

export async function logoutSession(): Promise<void> {
  await payload(await fetch(apiUrl('/api/auth/session/logout'), {
    method: 'POST', credentials: 'include', cache: 'no-store', redirect: 'error',
    headers: { 'Content-Type': 'application/json' }, body: '{}', signal: AbortSignal.timeout(15000),
  }));
  accessToken = null; expiresAt = 0;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = apiUrl(path);
  const isPublic = ['/api/catalog', '/api/maps/config'].includes(path.split('?')[0]);
  if (!isPublic && (!accessToken || expiresAt < Date.now() + 30000)) await refreshAccess();
  if (!isPublic && !accessToken) throw new ApiError(401, 'Inicio de sesión requerido');
  const send = () => {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body) headers.set('Content-Type', 'application/json');
    headers.delete('Authorization');
    if (!isPublic && accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
    return fetch(url, { ...init, headers, credentials: 'omit', cache: 'no-store', redirect: 'error',
      signal: init.signal || AbortSignal.timeout(15000) });
  };
  let response = await send();
  if (!isPublic && response.status === 401) {
    accessToken = null; expiresAt = 0;
    if (await refreshAccess()) response = await send();
  }
  return payload<T>(response);
}

export function apiMutation<T>(path: string, method: 'POST'|'PUT'|'PATCH'|'DELETE', body?: unknown): Promise<T> {
  return apiRequest<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });
}
