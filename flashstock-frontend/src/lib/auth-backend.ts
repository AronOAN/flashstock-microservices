/** Thin, server-only transport. Cognito identity and RS256 authorization are checked by Auth. */
import 'server-only';
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { siteOrigin } from '@/lib/flashstock-session';

type Action = 'exchange' | 'authorize' | 'refresh' | 'revoke';
export type AuthBackendResponse = { status: number; cookies: string[]; ok: boolean };

function baseUrl(): string {
  const base = new URL(process.env.FLASHSTOCK_API_BASE_URL || '');
  if (base.protocol !== 'https:' || base.username || base.password || base.port || base.search || base.hash ||
      !/^[a-z0-9.-]+$/i.test(base.hostname) || !['','/'].includes(base.pathname)) throw new Error('Invalid API base');
  return base.origin;
}
function backendSecret(): string {
  const value=process.env.FLASHSTOCK_BFF_SHARED_SECRET || '';
  if (!/^[A-Za-z0-9+/]{43,}={0,2}$/.test(value) || Buffer.from(value,'base64').length < 32) {
    throw new Error('Missing BFF server secret');
  }
  return value;
}
function validSetCookie(header: string): boolean {
  return /^(?:__Host-flashstock-issued-|flashstock-issued-)(?:access|refresh)=/.test(header) &&
    header.length <= 9000 && !/[\r\n]/.test(header) && !/;\s*domain\s*=/i.test(header) &&
    /;\s*httponly(?:;|$)/i.test(header) && /;\s*samesite=lax(?:;|$)/i.test(header) &&
    (!siteOrigin().startsWith('https://') || /;\s*secure(?:;|$)/i.test(header));
}
export async function callOwned(action:Action, cognitoAccess?:string):Promise<AuthBackendResponse> {
  if (process.env.FLASHSTOCK_ISSUED_TOKENS_ENABLED !== 'true') return {status:503,cookies:[],ok:false};
  const jar=await cookies();
  const rawCookies=jar.getAll().filter(c=>/^(?:__Host-flashstock-issued-|flashstock-issued-)(?:access|refresh)$/.test(c.name))
    .map(c=>`${c.name}=${c.value}`).join('; ');
  const headers=new Headers({Origin:siteOrigin(),'X-Flashstock-BFF-Secret':backendSecret(),Accept:'application/json'});
  if (action==='exchange' || action==='authorize' || action==='refresh') {
    if (!cognitoAccess || cognitoAccess.length<20 || cognitoAccess.length>12000) return {status:401,cookies:[],ok:false};
    headers.set('Authorization',`Bearer ${cognitoAccess}`);
  }
  if (action!=='exchange' && rawCookies) headers.set('Cookie',rawCookies);
  try {
    const response=await fetch(`${baseUrl()}/api/auth/browser/${action}`,{
      method:'POST',headers,cache:'no-store',redirect:'error',signal:AbortSignal.timeout(12000),
    });
    const setCookies=response.headers.getSetCookie();
    if (setCookies.some(h=>!validSetCookie(h))) return {status:502,cookies:[],ok:false};
    if (response.ok && ['exchange','refresh'].includes(action) && (setCookies.length!==2 ||
        !setCookies.some(h=>h.includes('issued-access=')) || !setCookies.some(h=>h.includes('issued-refresh='))))
      return {status:502,cookies:[],ok:false};
    if (response.ok && action==='authorize' && (response.status!==204 || setCookies.length!==0)) return {status:502,cookies:[],ok:false};
    if (response.ok && action==='revoke' && setCookies.length!==2) return {status:502,cookies:[],ok:false};
    return {status:response.status,cookies:setCookies,ok:response.ok};
  } catch { return {status:503,cookies:[],ok:false}; }
}

export function applyOwnedCookies(response:NextResponse, incoming:AuthBackendResponse | null):NextResponse {
  if (incoming) for (const cookie of incoming.cookies) response.headers.append('Set-Cookie',cookie);
  return response;
}

/** Transport-level deletion only; the BFF never reads/decodes an issued JWT. */
export function clearOwnedCookies(response:NextResponse):NextResponse {
  const secure=siteOrigin().startsWith('https://');
  for (const kind of ['access','refresh'] as const) {
    const name=(secure?'__Host-flashstock-issued-':'flashstock-issued-')+kind;
    response.cookies.set(name,'',{httpOnly:true,secure,sameSite:'lax',path:'/',maxAge:0});
  }
  return response;
}
