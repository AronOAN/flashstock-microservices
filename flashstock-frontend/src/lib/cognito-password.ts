import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { cookieOptions, seal, sessionCookieName, siteOrigin, type FlashStockSession } from '@/lib/flashstock-session';
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
  AuthenticationResult?: { AccessToken?: string; ExpiresIn?: number; TokenType?: string };
  ChallengeName?: string;
  ChallengeParameters?: { USER_ID_FOR_SRP?: string; requiredAttributes?: string };
  Session?: string;
};

export async function cognitoCall(action: 'InitiateAuth' | 'RespondToAuthChallenge' | 'GetUser', body: object): Promise<{ ok: boolean; status: number; data: Record<string, unknown> }> {
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

export async function completeSignIn(result: CognitoResult): Promise<NextResponse | null> {
  const token = result.AuthenticationResult;
  if (typeof token?.AccessToken !== 'string' || token.AccessToken.length < 20 || token.AccessToken.length > 12000 ||
      token.TokenType?.toLowerCase() !== 'bearer' || !Number.isInteger(token.ExpiresIn) ||
      !token.ExpiresIn || token.ExpiresIn < 1 || token.ExpiresIn > 86400) return null;
  const user = await cognitoCall('GetUser', { AccessToken: token.AccessToken });
  if (!user.ok || !Array.isArray(user.data.UserAttributes)) return null;
  const attributes = new Map<string, string>();
  for (const entry of user.data.UserAttributes) {
    if (entry && typeof entry === 'object' && 'Name' in entry && 'Value' in entry &&
        typeof entry.Name === 'string' && typeof entry.Value === 'string') attributes.set(entry.Name, entry.Value);
  }
  const sub = attributes.get('sub');
  if (!sub || !/^[a-zA-Z0-9-]{20,80}$/.test(sub)) return null;
  const session: FlashStockSession = { accessToken: token.AccessToken,
    expiresAt: Date.now() + Math.max(1, token.ExpiresIn - 30) * 1000,
    sub, email: attributes.get('email_verified') === 'true' ? attributes.get('email') || null : null,
    name: attributes.get('name') || null };
  const encryptedSession = seal(session);
  if (encryptedSession.length > 3800) return null;
  const response = NextResponse.json({ ok: true }, { headers: { 'Cache-Control': 'no-store' } });
  response.cookies.set(sessionCookieName(), encryptedSession, { ...cookieOptions(), maxAge: token.ExpiresIn });
  response.cookies.delete(challengeCookieName());
  return response;
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
