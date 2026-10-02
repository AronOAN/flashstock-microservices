import { NextRequest, NextResponse } from 'next/server';
import { completeSignIn } from '@/lib/cognito-password';
import { clearPkceCookie, exchangeCode, matchesState, openPkce } from '@/lib/cognito-oauth';
import { oauthCookieName, siteOrigin } from '@/lib/flashstock-session';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function failed(): NextResponse {
  const response = NextResponse.redirect(new URL('/login?auth_error=1', siteOrigin()));
  clearPkceCookie(response);
  response.headers.set('Cache-Control', 'no-store');
  response.headers.set('Referrer-Policy', 'no-referrer');
  return response;
}

export async function GET(request: NextRequest) {
  try {
    const params = request.nextUrl.searchParams;
    const attempt = openPkce(request.cookies.get(oauthCookieName())?.value);
    if (!attempt || params.getAll('state').length !== 1 ||
        !matchesState(attempt.state, params.get('state')) ||
        params.has('error') || params.getAll('code').length !== 1) return failed();
    const code = params.get('code');
    if (!code || code.length > 4096 || /[\u0000-\u001F\u007F]/.test(code)) return failed();
    const result = await exchangeCode(code, attempt.verifier);
    if (!result) return failed();
    const redirect = NextResponse.redirect(new URL('/', siteOrigin()));
    const response = await completeSignIn(result, redirect);
    if (!response) return failed();
    clearPkceCookie(response);
    response.headers.set('Cache-Control', 'no-store');
    response.headers.set('Referrer-Policy', 'no-referrer');
    return response;
  } catch { return failed(); }
}
