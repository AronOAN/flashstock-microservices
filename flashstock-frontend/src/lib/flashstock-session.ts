import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { cookies } from 'next/headers';

export type FlashStockSession = {
  accessToken: string;
  expiresAt: number;
  sub: string;
  email: string | null;
  name: string | null;
};

export function siteOrigin(): string {
  const configured = process.env.FLASHSTOCK_SITE_URL?.trim();
  const hosted = Boolean(process.env.VERCEL || process.env.NODE_ENV === 'production');
  if (!configured && hosted) {
    throw new Error('FLASHSTOCK_SITE_URL debe ser el dominio HTTPS real de Vercel');
  }
  const raw = configured || 'http://localhost:3000';
  const parsed = new URL(raw);
  if (parsed.pathname !== '/' || parsed.search || parsed.hash || parsed.username || parsed.password) {
    throw new Error('FLASHSTOCK_SITE_URL debe contener solo el origen');
  }
  if (hosted && (parsed.protocol !== 'https:' || ['localhost','127.0.0.1','[::1]'].includes(parsed.hostname.toLowerCase()))) {
    throw new Error('Producción no puede redirigir a localhost: configura FLASHSTOCK_SITE_URL con HTTPS real');
  }
  if (!['http:','https:'].includes(parsed.protocol)) throw new Error('Protocolo inválido');
  return parsed.origin;
}

export function secureCookies(): boolean { return siteOrigin().startsWith('https://'); }
export function sessionCookieName(): string { return secureCookies() ? '__Host-flashstock-session' : 'flashstock-session'; }
export function oauthCookieName(): string { return secureCookies() ? '__Host-flashstock-oauth' : 'flashstock-oauth'; }

function key(): Buffer {
  const input = process.env.FLASHSTOCK_SESSION_KEY;
  if (!input) throw new Error('Falta FLASHSTOCK_SESSION_KEY (base64 de 32 bytes)');
  const secret = Buffer.from(input, 'base64');
  if (secret.length !== 32) throw new Error('FLASHSTOCK_SESSION_KEY debe contener 32 bytes');
  return secret;
}

export function seal(session: FlashStockSession): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key(), iv);
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(session), 'utf8'), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), ciphertext]).toString('base64url');
}

export function unseal(value: string): FlashStockSession | null {
  try {
    const bytes = Buffer.from(value, 'base64url');
    if (bytes.length < 30 || bytes.length > 16384) return null;
    const decipher = createDecipheriv('aes-256-gcm', key(), bytes.subarray(0,12));
    decipher.setAuthTag(bytes.subarray(12,28));
    const payload = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(28)), decipher.final()]).toString('utf8')) as FlashStockSession;
    if (!payload || typeof payload.accessToken !== 'string' || typeof payload.sub !== 'string' ||
        typeof payload.expiresAt !== 'number' || Date.now() >= payload.expiresAt) return null;
    return payload;
  } catch {
    return null;
  }
}
export async function getSession(): Promise<FlashStockSession | null> {
  const cookie = (await cookies()).get(sessionCookieName())?.value;
  return cookie ? unseal(cookie) : null;
}

export const cookieOptions = () => ({ httpOnly: true as const, sameSite: 'lax' as const,
  secure: secureCookies(), path: '/' as const });
