import { NextResponse } from 'next/server';
import { oauthCookieName, sessionCookieName, siteOrigin } from '@/lib/flashstock-session';
export const runtime='nodejs';
// End of local BFF session; revoke OAuth refresh tokens if they are added in a later phase.
export async function GET() {
  const res=NextResponse.redirect(new URL('/',siteOrigin()));
  res.cookies.delete(sessionCookieName());
  res.cookies.delete(oauthCookieName());
  res.headers.set('Cache-Control','no-store');
  return res;
}
