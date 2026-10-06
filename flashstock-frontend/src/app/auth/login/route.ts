import { NextResponse } from 'next/server';
import { siteOrigin } from '@/lib/flashstock-session';

// Old bookmarks continue to the first-party email/password form.
export function GET() {
  const response = NextResponse.redirect(new URL('/login', siteOrigin()));
  response.headers.set('Cache-Control', 'no-store');
  return response;
  
}
