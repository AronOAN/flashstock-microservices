import { createCipheriv, createDecipheriv, createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { cognitoConfig, type CognitoResult } from '@/lib/cognito-password';
import { cookieOptions, oauthCookieName, siteOrigin } from '@/lib/flashstock-session';
import type { NextResponse } from 'next/server';

export type PkceAttempt = { state: string; verifier: string; expiresAt: number };

export function oauthConfig(): { domain: string; clientId: string; redirectUri: string } {
  const { clientId, endpoint } = cognitoConfig();
  const region = new URL(endpoint).hostname.split('.')[1];
  const domain = new URL(process.env.COGNITO_DOMAIN_URL || '');
  if (domain.protocol !== 'https:' || domain.port || domain.username || domain.password ||
      domain.pathname !== '/' || domain.search || domain.hash ||
      !new RegExp(`^[a-z0-9-]+\\.auth\\.${region}\\.amazoncognito\\.com$`).test(domain.hostname)) {
    throw new Error('COGNITO_DOMAIN_URL inválido o no corresponde a la región del pool');
  }
  return { domain: domain.origin, clientId, redirectUri: `${siteOrigin()}/auth/callback` };
}

export function newPkceAttempt(): PkceAttempt {
  return { state: randomBytes(32).toString('base64url'),
    verifier: randomBytes(32).toString('base64url'), expiresAt: Date.now() + 5 * 60_000 };
}

export function challengeFor(verifier: string): string {
  return createHash('sha256').update(verifier, 'ascii').digest('base64url');
}

function key(): Buffer {
  const secret = Buffer.from(process.env.FLASHSTOCK_SESSION_KEY || '', 'base64');
  if (secret.length !== 32) throw new Error('FLASHSTOCK_SESSION_KEY inválida');
  return secret;
}

export function sealPkce(attempt: PkceAttempt): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key(), iv);
  cipher.setAAD(Buffer.from('flashstock-pkce-v1'));
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(attempt)), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString('base64url');
}

export function openPkce(value: string | undefined): PkceAttempt | null {
  try {
    if (!value || value.length > 4096) return null;
    const bytes = Buffer.from(value, 'base64url');
    if (bytes.length < 30 || bytes.length > 2048) return null;
    const decipher = createDecipheriv('aes-256-gcm', key(), bytes.subarray(0, 12));
    decipher.setAAD(Buffer.from('flashstock-pkce-v1'));
    decipher.setAuthTag(bytes.subarray(12, 28));
    const data: unknown = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(28)), decipher.final()]).toString('utf8'));
    if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
    const state = data as Partial<PkceAttempt>;
    if (!state.state || !/^[a-zA-Z0-9_-]{43}$/.test(state.state) ||
        !state.verifier || !/^[a-zA-Z0-9_-]{43}$/.test(state.verifier) ||
        typeof state.expiresAt !== 'number' || !Number.isFinite(state.expiresAt) ||
        Date.now() >= state.expiresAt || state.expiresAt > Date.now() + 5 * 60_000) return null;
    return state as PkceAttempt;
  } catch { return null; }
}

export function matchesState(expected: string, received: string | null): boolean {
  if (!received || received.length !== expected.length) return false;
  return timingSafeEqual(Buffer.from(expected), Buffer.from(received));
}

export function clearPkceCookie(response: NextResponse): void {
  response.cookies.set(oauthCookieName(), '', { ...cookieOptions(), maxAge: 0 });
}

export async function exchangeCode(code: string, verifier: string): Promise<CognitoResult | null> {
  const { domain, clientId, redirectUri } = oauthConfig();
  const body = new URLSearchParams({ grant_type: 'authorization_code', client_id: clientId,
    redirect_uri: redirectUri, code, code_verifier: verifier });
  const response = await fetch(`${domain}/oauth2/token`, { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
    body, cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(10000) });
  if (!response.ok) return null;
  const token: unknown = await response.json();
  if (!token || typeof token !== 'object' || Array.isArray(token)) return null;
  const payload = token as Record<string, unknown>;
  return { AuthenticationResult: {
    AccessToken: typeof payload.access_token === 'string' ? payload.access_token : undefined,
    RefreshToken: typeof payload.refresh_token === 'string' ? payload.refresh_token : undefined,
    ExpiresIn: typeof payload.expires_in === 'number' ? payload.expires_in : undefined,
    TokenType: typeof payload.token_type === 'string' ? payload.token_type : undefined,
  } };
}
