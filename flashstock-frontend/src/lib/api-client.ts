export type ApiEnvelope<T> = { message: string; data: T };

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/api/')) throw new Error(`Ruta API inválida: ${path}`);
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  headers.set('Accept', 'application/json');
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin', cache: 'no-store' });
  let payload: { message?: string; data?: unknown } | null = null;
  try { payload = await response.json(); } catch { payload = null; }
  if (!response.ok) throw new ApiError(response.status, payload?.message || `HTTP ${response.status}`);
  if (!payload || !('data' in payload)) throw new ApiError(502, 'Respuesta API inválida');
  return payload.data as T;
}

export function apiMutation<T>(path: string, method: 'POST'|'PUT'|'PATCH'|'DELETE', body?: unknown): Promise<T> {
  return apiRequest<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });
}
