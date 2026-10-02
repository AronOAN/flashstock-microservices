import { createHash, randomBytes } from 'node:crypto';
import { NextResponse } from 'next/server';
import { cookieOptions, oauthCookieName, siteOrigin } from '@/lib/flashstock-session';

export const runtime = 'nodejs';
export async function GET() {
  const domain = process.env.COGNITO_DOMAIN;
  const clientId = process.env.COGNITO_APP_CLIENT_ID;
  if (!domain || !clientId || !domain.startsWith('https://')) {
    return NextResponse.json({message:'Configura COGNITO_DOMAIN y COGNITO_APP_CLIENT_ID'}, {status:503});
  }
  const state = randomBytes(32).toString('base64url');
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const endpoint = new URL('/oauth2/authorize',domain);
  endpoint.searchParams.set('response_type','code');
  endpoint.searchParams.set('client_id',clientId);
  endpoint.searchParams.set('redirect_uri',`${siteOrigin()}/auth/callback`);
  endpoint.searchParams.set('scope','openid email profile');
  endpoint.searchParams.set('state',state);
  endpoint.searchParams.set('code_challenge_method','S256');
  endpoint.searchParams.set('code_challenge',challenge);
  const res = NextResponse.redirect(endpoint);
  res.cookies.set(oauthCookieName(), JSON.stringify({state,verifier}), {...cookieOptions(), maxAge:300});
  res.headers.set('Cache-Control','no-store');
  return res;
}
