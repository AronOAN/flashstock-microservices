import { NextResponse } from 'next/server';
import { challengeCookieName, revokeRefreshToken } from '@/lib/cognito-password';
import { clearSessionCookies, cookieOptions, oauthCookieName, siteOrigin } from '@/lib/flashstock-session';
export const runtime='nodejs';
export async function GET() {
  await revokeRefreshToken();
  const res=NextResponse.redirect(new URL('/',siteOrigin()));
  clearSessionCookies(res);
  res.cookies.set(oauthCookieName(), '', { ...cookieOptions(), maxAge: 0 });
  res.cookies.set(challengeCookieName(), '', { ...cookieOptions(), maxAge: 0 });
  res.headers.set('Cache-Control','no-store');
  return res;
}
