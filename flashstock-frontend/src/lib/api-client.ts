
export type ApiEnvelope<T> = Readonly<{
  message: string;
  data: T;
}>;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type MutationMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export type LoginResponse = Readonly<{
  authenticated: boolean;
  challenge?: string;
}>;

type AccessResponse = {
  accessToken?: string;
  expiresIn?: number;
  challenge?: string;
};

const TIMEOUT_MS = 15_000;
const REFRESH_MARGIN_MS = 30_000;

const CSRF_PATH = '/api/auth/session/csrf';
const REFRESH_PATH = '/api/auth/session/refresh';

const PUBLIC_GET = new Set([
  '/api/catalog',
  '/api/maps/config',
]);

// ============================================
// ACCESS TOKEN: SOLO MEMORIA
// ============================================

let accessToken: string | null = null;
let expiresAt = 0;

let authRevision = 0;

let refreshPromise: Promise<void> | null = null;
let csrfTokenPromise: Promise<string> | null = null;

// No exportar variables ni funciones que devuelvan JWT.

function clearAccess(): void {
  accessToken = null;
  expiresAt = 0;
  authRevision++;
}

function validAccess(): boolean {
  return (
    accessToken !== null &&
    expiresAt > Date.now() + REFRESH_MARGIN_MS
  );
}

function rememberAccess(data: AccessResponse): void {
  const token = data.accessToken;
  const seconds = data.expiresIn;

  if (
    typeof token !== 'string' ||
    token.length < 20 ||
    token.length > 16384 ||
    token.split('.').length !== 3 ||
    typeof seconds !== 'number' ||
    !Number.isFinite(seconds) ||
    seconds < 1 ||
    seconds > 3600
  ) {
    throw new ApiError(
      502,
      'Auth devolvió credenciales inválidas.'
    );
  }

  accessToken = token;
  expiresAt = Date.now() + seconds * 1000;
  authRevision++;
}

// ============================================
// VALIDACIÓN DE RUTAS
// ============================================

export function apiUrl(path: string): string {
  if (
    !path.startsWith('/api/') ||
    path.startsWith('//') ||
    /[\\\u0000-\u0020\u007f#]/.test(path)
  ) {
    throw new TypeError('Ruta API inválida');
  }

  const pathname = path.split('?', 1)[0];

  if (
    pathname.length > 2048 ||
    path.length > 8192 ||
    /\/{2,}/.test(pathname) ||
    /%(?:2e|2f|5c|25|0[0-9a-f]|1[0-9a-f]|7f)/i.test(
      pathname
    ) ||
    pathname.split('/').some(
      (part) => part === '.' || part === '..'
    ) ||
    /%(?![a-f\d]{2})/i.test(path)
  ) {
    throw new TypeError('Ruta API inválida');
  }

  return path;
}

function isRecord(
  value: unknown
): value is Record<string, unknown> {
  return (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value)
  );
}

// ============================================
// RESPUESTAS HTTP
// ============================================

async function unwrap<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message =
      response.status === 401
        ? 'Inicio de sesión requerido'
        : response.status === 403
          ? 'Operación no autorizada'
          : response.status === 409
            ? 'La operación está en conflicto'
            : 'Solicitud no completada';

    throw new ApiError(response.status, message);
  }

  if (
    response.status === 204 ||
    response.status === 205
  ) {
    return undefined as T;
  }

  const contentType =
    response.headers.get('content-type') ?? '';

  if (
    !contentType.toLowerCase().includes('application/json')
  ) {
    throw new ApiError(502, 'Respuesta API inválida');
  }

  let body: unknown;

  try {
    body = await response.json();
  } catch {
    throw new ApiError(502, 'Respuesta API inválida');
  }

  if (!isRecord(body) || !Object.hasOwn(body, 'data')) {
    throw new ApiError(502, 'Respuesta API inválida');
  }

  return body.data as T;
}

// ============================================
// CSRF PARA ENDPOINTS CON COOKIES
// ============================================

async function csrfToken(): Promise<string> {
  if (!csrfTokenPromise) {
    const pending = (async () => {
      const response = await fetch(CSRF_PATH, {
        method: 'GET',
        credentials: 'same-origin',
        cache: 'no-store',
        redirect: 'error',
        headers: {
          Accept: 'application/json',
        },
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });

      const data = await unwrap<unknown>(response);

      if (
        !isRecord(data) ||
        typeof data.csrfToken !== 'string' ||
        data.csrfToken.length < 16
      ) {
        throw new ApiError(502, 'Token CSRF inválido');
      }

      return data.csrfToken;
    })();

    csrfTokenPromise = pending;

    void pending.catch(() => {
      if (csrfTokenPromise === pending) {
        csrfTokenPromise = null;
      }
    });
  }

  return csrfTokenPromise;
}

// ============================================
// SOLICITUDES AL MICROSERVICIO AUTH
// ============================================

async function sessionPost<T>(
  path: string,
  body: unknown
): Promise<T> {
  const token = await csrfToken();

  const response = await fetch(apiUrl(path), {
    method: 'POST',
    credentials: 'same-origin',
    cache: 'no-store',
    redirect: 'error',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-CSRF-Token': token,
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });

  if (response.status === 403) {
    csrfTokenPromise = null;
  }

  return unwrap<T>(response);
}

// ============================================
// RENOVACIÓN DEL ACCESS JWT
// ============================================

async function refreshAccess(): Promise<void> {
  if (validAccess()) return;

  if (!refreshPromise) {
    const revision = authRevision;

    const pending = (async () => {
      try {
        const result = await sessionPost<AccessResponse>(
          REFRESH_PATH,
          {}
        );

        // Impedir que una respuesta anterior a logout
        // o login sobrescriba la sesión más reciente.
        if (revision !== authRevision) return;

        rememberAccess(result);

        // Java pudo rotar la cookie de refresh.
        csrfTokenPromise = null;
      } catch (error: unknown) {
        if (
          revision === authRevision &&
          error instanceof ApiError &&
          error.status === 401
        ) {
          clearAccess();
        }

        throw error;
      }
    })();

    refreshPromise = pending.finally(() => {
      if (refreshPromise === settled) {
        refreshPromise = null;
      }
    });

    const settled = refreshPromise;
  }

  await refreshPromise;
}

async function requireAccess(): Promise<string> {
  if (!validAccess()) {
    await refreshAccess();
  }

  if (!accessToken || !validAccess()) {
    throw new ApiError(401, 'Inicio de sesión requerido');
  }

  return accessToken;
}

// ============================================
// LOGIN / DESAFÍOS DE COGNITO
// ============================================

export async function sessionRequest(
  action: 'login' | 'challenge',
  body: unknown
): Promise<LoginResponse> {
  clearAccess();

  const revision = authRevision;

  try {
    const result = await sessionPost<AccessResponse>(
      `/api/auth/session/${action}`,
      body
    );

    if (revision !== authRevision) {
      throw new ApiError(
        409,
        'La sesión cambió durante la autenticación.'
      );
    }

    if (result.accessToken) {
      rememberAccess(result);

      return {
        authenticated: true,
      };
    }

    if (
      typeof result.challenge === 'string' &&
      result.challenge.length > 0
    ) {
      return {
        authenticated: false,
        challenge: result.challenge,
      };
    }

    throw new ApiError(
      502,
      'Respuesta de autenticación inválida'
    );
  } finally {
    csrfTokenPromise = null;
  }
}

// ============================================
// PETICIONES DE NEGOCIO
// ============================================

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {}
): Promise<T> {
  const url = apiUrl(path);
  const pathname = path.split('?', 1)[0];

  const method = (init.method ?? 'GET').toUpperCase();

  const allowedMethods = [
    'GET',
    'HEAD',
    'POST',
    'PUT',
    'PATCH',
    'DELETE',
  ];

  if (!allowedMethods.includes(method)) {
    throw new TypeError('Método HTTP no permitido');
  }

  const isPublic =
    method === 'GET' && PUBLIC_GET.has(pathname);

  const headers = new Headers(init.headers);

  // Nunca confiar en headers de identidad
  // suministrados por componentes.
  headers.delete('Authorization');
  headers.delete('Cookie');
  headers.delete('X-CSRF-Token');

  headers.set('Accept', 'application/json');

  if (
    init.body != null &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json');
  }

  const timeout = AbortSignal.timeout(TIMEOUT_MS);

  const signal = init.signal
    ? AbortSignal.any([init.signal, timeout])
    : timeout;

  async function send(jwt: string | null) {
    const requestHeaders = new Headers(headers);

    if (jwt) {
      requestHeaders.set('Authorization', `Bearer ${jwt}`);
    }

    return fetch(url, {
      ...init,
      method,
      headers: requestHeaders,
      credentials: 'omit',
      cache: 'no-store',
      redirect: 'error',
      signal,
    });
  }

  let token: string | null = isPublic
    ? null
    : await requireAccess();

  let response = await send(token);

  // Solo las lecturas pueden reintentarse
  // automáticamente después de un 401.
  if (
    !isPublic &&
    response.status === 401 &&
    (method === 'GET' || method === 'HEAD')
  ) {
    if (accessToken === token) {
      clearAccess();
    }

    token = await requireAccess();
    response = await send(token);
  } else if (
    !isPublic &&
    response.status === 401 &&
    accessToken === token
  ) {
    clearAccess();
  }

  return unwrap<T>(response);
}

export function apiMutation<T>(
  path: string,
  method: MutationMethod,
  body?: unknown
): Promise<T> {
  return apiRequest<T>(path, {
    method,
    body:
      body === undefined
        ? undefined
        : JSON.stringify(body),
  });
}

// ============================================
// LOGOUT
// ============================================

export async function logoutSession(): Promise<void> {
  // Invalidación local inmediata.
  clearAccess();

  try {
    // Java revoca la sesión de renovación
    // y elimina su cookie HttpOnly.
    await sessionPost<void>(
      '/api/auth/session/logout',
      {}
    );
  } finally {
    // Aunque falle la red, no recuperamos
    // el access JWT anterior.
    csrfTokenPromise = null;
  }
}
