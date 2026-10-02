import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { clearSessionCookies, cookieOptions, getRefreshState, getSession, refreshCookieName,
  REFRESH_TTL_SECONDS, seal, sealRefresh, sessionCookieName, siteOrigin,
  type FlashStockRefresh, type FlashStockSession } from '@/lib/flashstock-session';
import { NextRequest, NextResponse } from 'next/server';

const challenges = ['NEW_PASSWORD_REQUIRED', 'SMS_MFA', 'SOFTWARE_TOKEN_MFA', 'EMAIL_MFA', 'EMAIL_OTP', 'SMS_OTP'] as const;
export type ChallengeName = typeof challenges[number];
export type PendingChallenge = { name: ChallengeName; username: string; session: string; expiresAt: number; email: string };

export function challengeCookieName(): string {
  return cookieOptions().secure ? '__Host-flashstock-challenge' : 'flashstock-challenge';
}

export function authError(status: number, message: string): NextResponse {
  return NextResponse.json({ ok: false, message }, { status, headers: { 'Cache-Control': 'no-store' } });
}

export async function readAuthBody(req: NextRequest): Promise<Record<string, unknown> | null> {
  if (req.headers.get('origin') !== siteOrigin() ||
      !req.headers.get('content-type')?.toLowerCase().startsWith('application/json') ||
      Number(req.headers.get('content-length') || '0') > 4096) return null;
  try {
    const raw = await req.text();
    if (raw.length > 4096) return null;
    const body: unknown = JSON.parse(raw);
    return body && typeof body === 'object' && !Array.isArray(body) ? body as Record<string, unknown> : null;
  } catch { return null; }
}

export function cognitoConfig(): { endpoint: string; clientId: string } {
  const issuer = new URL(process.env.COGNITO_ISSUER_URL || '');
  const region = /^cognito-idp\.([a-z0-9-]+)\.amazonaws\.com$/.exec(issuer.hostname)?.[1];
  if (issuer.protocol !== 'https:' || !region || issuer.port || issuer.search || issuer.hash ||
      !new RegExp(`^/${region}_[A-Za-z0-9]+$`).test(issuer.pathname)) {
    throw new Error('COGNITO_ISSUER_URL inválido');
  }
  const clientId = process.env.COGNITO_APP_CLIENT_ID || '';
  if (!/^[a-z0-9]{1,128}$/.test(clientId)) throw new Error('COGNITO_APP_CLIENT_ID inválido');
  return { endpoint: `${issuer.origin}/`, clientId };
}

export type CognitoResult = {
  AuthenticationResult?: { AccessToken?: string; RefreshToken?: string; ExpiresIn?: number; TokenType?: string };
  ChallengeName?: string;
  ChallengeParameters?: { USER_ID_FOR_SRP?: string; requiredAttributes?: string };
  Session?: string;
};

export async function cognitoCall(action: 'InitiateAuth' | 'RespondToAuthChallenge' | 'GetUser' | 'GetTokensFromRefreshToken', body: object): Promise<{ ok: boolean; status: number; data: Record<string, unknown> }> {
  const { endpoint } = cognitoConfig();
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-amz-json-1.1', 'X-Amz-Target': `AWSCognitoIdentityProviderService.${action}` },
    body: JSON.stringify(body), cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(10000),
  });
  // Never expose raw Cognito errors, user existence, tokens, or challenge sessions.
  if (!response.ok) return { ok: false, status: response.status, data: {} };
  const data = await response.json();
  if (!data || typeof data !== 'object' || Array.isArray(data)) return { ok: false, status: 502, data: {} };
  return { ok: true, status: response.status, data };
}

export function isChallenge(name: string): name is ChallengeName {
  return (challenges as readonly string[]).includes(name);
}

function validAccessToken(token: CognitoResult['AuthenticationResult']): token is NonNullable<CognitoResult['AuthenticationResult']> & { AccessToken: string; ExpiresIn: number } {
  return typeof token?.AccessToken === 'string' && token.AccessToken.length >= 20 && token.AccessToken.length <= 12000 &&
    token.TokenType?.toLowerCase() === 'bearer' && Number.isInteger(token.ExpiresIn) &&
    !!token.ExpiresIn && token.ExpiresIn >= 1 && token.ExpiresIn <= 86400;
}

async function cognitoUser(accessToken: string): Promise<{ sub: string; email: string | null; name: string | null } | null> {
  const user = await cognitoCall('GetUser', { AccessToken: accessToken });
  if (!user.ok) {
    if (user.status >= 500 || user.status === 429) throw new Error('Cognito GetUser unavailable');
    return null;
  }
  if (!Array.isArray(user.data.UserAttributes)) return null;
  const attributes = new Map<string, string>();
  for (const entry of user.data.UserAttributes) {
    if (entry && typeof entry === 'object' && 'Name' in entry && 'Value' in entry &&
        typeof entry.Name === 'string' && typeof entry.Value === 'string') attributes.set(entry.Name, entry.Value);
  }
  const sub = attributes.get('sub');
  if (!sub || !/^[a-zA-Z0-9-]{20,80}$/.test(sub)) return null;
  return { sub, email: attributes.get('email_verified') === 'true' ? attributes.get('email') || null : null,
    name: attributes.get('name') || null };
}

export async function completeSignIn(result: CognitoResult): Promise<NextResponse | null> {
  const token = result.AuthenticationResult;
  // Without a refresh token the browser would lose its session after 15 minutes.
  if (!validAccessToken(token) || typeof token.RefreshToken !== 'string' ||
      token.RefreshToken.length < 20 || token.RefreshToken.length > 4096) return null;
  const user = await cognitoUser(token.AccessToken);
  if (!user) return null;
  const now = Date.now();
  const session: FlashStockSession = { accessToken: token.AccessToken,
    expiresAt: now + Math.max(1, token.ExpiresIn - 30) * 1000,
    sub: user.sub, email: user.email, name: user.name };
  const refresh: FlashStockRefresh = { refreshToken: token.RefreshToken, sub: user.sub,
    expiresAt: now + REFRESH_TTL_SECONDS * 1000 };
  const encryptedSession = seal(session);
  const encryptedRefresh = sealRefresh(refresh);
  if (encryptedSession.length > 3800 || encryptedRefresh.length > 3800) return null;
  const response = NextResponse.json({ ok: true }, { headers: { 'Cache-Control': 'no-store' } });
  response.cookies.set(sessionCookieName(), encryptedSession, { ...cookieOptions(), maxAge: token.ExpiresIn });
  response.cookies.set(refreshCookieName(), encryptedRefresh, { ...cookieOptions(), maxAge: REFRESH_TTL_SECONDS });
  response.cookies.set(challengeCookieName(), '', { ...cookieOptions(), maxAge: 0 });
  return response;
}

export type SessionResolution = {
  session: FlashStockSession | null;
  refreshed?: { session: FlashStockSession; accessMaxAge: number; refresh?: FlashStockRefresh };
  clear?: boolean;
  unavailable?: boolean;
};

export async function resolveSession(): Promise<SessionResolution> {
  const existing = await getSession();
  if (existing && existing.expiresAt > Date.now() + 30000) return { session: existing };
  const refresh = await getRefreshState();
  if (!refresh) return { session: existing };
  try {
    const { clientId } = cognitoConfig();
    const response = await cognitoCall('GetTokensFromRefreshToken', {
      ClientId: clientId, RefreshToken: refresh.refreshToken,
    });
    if (!response.ok) {
      // Cognito rejects expired/revoked tokens with a 4xx; network/5xx errors are retriable.
      if (response.status >= 500 || response.status === 429) return { session: existing, unavailable: !existing };
      return { session: null, clear: true };
    }
    const token = (response.data as CognitoResult).AuthenticationResult;
    if (!validAccessToken(token)) return { session: existing, unavailable: !existing };
    const user = await cognitoUser(token.AccessToken);
    // Tie every refreshed access token to the original subject. Never swap identities.
    if (!user || user.sub !== refresh.sub) return { session: null, clear: true };
    const session: FlashStockSession = { accessToken: token.AccessToken,
      expiresAt: Date.now() + Math.max(1, token.ExpiresIn - 30) * 1000,
      sub: refresh.sub, email: user.email, name: user.name };
    const rotated = typeof token.RefreshToken === 'string' && token.RefreshToken.length >= 20 &&
      token.RefreshToken.length <= 4096 ? { ...refresh, refreshToken: token.RefreshToken } : undefined;
    if (seal(session).length > 3800 || (rotated && sealRefresh(rotated).length > 3800)) {
      return { session: existing, unavailable: !existing };
    }
    return { session, refreshed: { session, accessMaxAge: token.ExpiresIn, refresh: rotated } };
  } catch {
    return { session: existing, unavailable: !existing };
  }
}

export function attachSessionCookies(response: NextResponse, state: SessionResolution): NextResponse {
  if (state.clear) clearSessionCookies(response);
  if (state.refreshed) {
    response.cookies.set(sessionCookieName(), seal(state.refreshed.session),
      { ...cookieOptions(), maxAge: state.refreshed.accessMaxAge });
    if (state.refreshed.refresh) {
      response.cookies.set(refreshCookieName(), sealRefresh(state.refreshed.refresh),
        { ...cookieOptions(), maxAge: Math.max(0, Math.floor((state.refreshed.refresh.expiresAt - Date.now()) / 1000)) });
    }
  }
  return response;
}

export async function revokeRefreshToken(): Promise<void> {
  const refresh = await getRefreshState();
  if (!refresh) return;
  try {
    const { endpoint, clientId } = cognitoConfig();
    await fetch(endpoint, {
      method: 'POST', headers: { 'Content-Type': 'application/x-amz-json-1.1',
        'X-Amz-Target': 'AWSCognitoIdentityProviderService.RevokeToken' },
      body: JSON.stringify({ ClientId: clientId, Token: refresh.refreshToken }),
      cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(5000),
    });
  } catch { /* Always clear the local session, even when Cognito is unavailable. */ }
}

// A separate authenticated envelope prevents a session cookie from being substituted for challenge state.
export function sealChallenge(challenge: PendingChallenge): string {
  const secret = Buffer.from(process.env.FLASHSTOCK_SESSION_KEY || '', 'base64');
  if (secret.length !== 32) throw new Error('FLASHSTOCK_SESSION_KEY inválida');
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', secret, iv);
  cipher.setAAD(Buffer.from('flashstock-challenge-v1'));
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(challenge)), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString('base64url');
}

export function openChallenge(value: string): PendingChallenge | null {
  try {
    const secret = Buffer.from(process.env.FLASHSTOCK_SESSION_KEY || '', 'base64');
    const bytes = Buffer.from(value, 'base64url');
    if (secret.length !== 32 || bytes.length < 30 || bytes.length > 8192) return null;
    const decipher = createDecipheriv('aes-256-gcm', secret, bytes.subarray(0, 12));
    decipher.setAAD(Buffer.from('flashstock-challenge-v1'));
    decipher.setAuthTag(bytes.subarray(12, 28));
    const state = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(28)), decipher.final()]).toString()) as PendingChallenge;
    return state && isChallenge(state.name) && typeof state.username === 'string' && state.username.length < 256 &&
      typeof state.session === 'string' && state.session.length < 4096 && typeof state.email === 'string' &&
      Number.isFinite(state.expiresAt) && Date.now() < state.expiresAt ? state : null;
  } catch { return null; }
}
