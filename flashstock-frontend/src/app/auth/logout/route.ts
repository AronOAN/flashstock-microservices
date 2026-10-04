import { NextRequest, NextResponse } from 'next/server';
import { revokeRefreshToken } from '@/lib/cognito-password';
import { callOwned, clearOwnedCookies } from '@/lib/auth-backend';
import { clearSessionCookies, siteOrigin, cookieOptions, oauthCookieName } from '@/lib/flashstock-session';
export const runtime = 'nodejs';
export async function POST(req:NextRequest) {
  if (req.headers.get('origin') !== siteOrigin()) return NextResponse.json({ok:false},{status:403});
  const own = process.env.FLASHSTOCK_ISSUED_TOKENS_ENABLED === 'true' ? await callOwned('revoke') : null;
  if (own && !own.ok) return NextResponse.json({ok:false,message:'No se pudo revocar la sesión; vuelve a intentarlo.'},
    {status:503,headers:{'Cache-Control':'no-store'}});
  await revokeRefreshToken();
  const res=NextResponse.json({ok:true},{headers:{'Cache-Control':'no-store'}});
  clearSessionCookies(res);
  res.cookies.set(oauthCookieName(),'',{...cookieOptions(),maxAge:0});
  clearOwnedCookies(res);
  return res;
}
export function GET() {
  return NextResponse.json({ok:false,message:'Utiliza POST para cerrar sesión'},
    {status:405,headers:{Allow:'POST','Cache-Control':'no-store'}});
}
