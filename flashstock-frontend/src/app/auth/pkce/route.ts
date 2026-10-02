import { NextResponse } from 'next/server';
import { cookieOptions, oauthCookieName } from '@/lib/flashstock-session';
import { challengeFor, newPkceAttempt, oauthConfig, sealPkce } from '@/lib/cognito-oauth';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export function GET() {
  try {
    const { domain, clientId, redirectUri } = oauthConfig();
    const attempt = newPkceAttempt();
    const authorization = new URL('/oauth2/authorize', domain);
    authorization.search = new URLSearchParams({ response_type: 'code', client_id: clientId,
      redirect_uri: redirectUri, scope: 'openid email profile aws.cognito.signin.user.admin',
      code_challenge: challengeFor(attempt.verifier), code_challenge_method: 'S256',
      state: attempt.state }).toString();
    const response = NextResponse.redirect(authorization);
    response.cookies.set(oauthCookieName(), sealPkce(attempt), { ...cookieOptions(), maxAge: 300 });
    response.headers.set('Cache-Control', 'no-store');
    response.headers.set('Referrer-Policy', 'no-referrer');
    return response;
  } catch {
    return NextResponse.json({ message: 'Acceso con Cognito no disponible' },
      { status: 503, headers: { 'Cache-Control': 'no-store' } });
  }
}
