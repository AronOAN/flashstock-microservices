import { NextResponse } from 'next/server';
import { oauthCookieName, siteOrigin } from '@/lib/flashstock-session';

// OAuth callbacks are retired; a code arriving here is never exchanged.
export function GET() {
  const response = NextResponse.redirect(new URL('/login', siteOrigin()));
  response.cookies.delete(oauthCookieName());
  response.headers.set('Cache-Control', 'no-store');
  return response;
}
