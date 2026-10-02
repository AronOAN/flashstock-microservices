import { timingSafeEqual } from 'node:crypto';
import { NextRequest, NextResponse } from 'next/server';
import { cookieOptions, oauthCookieName, seal, sessionCookieName, siteOrigin } from '@/lib/flashstock-session';

export const runtime = 'nodejs';
const equal = (a:string,b:string) => {
  const aa=Buffer.from(a),bb=Buffer.from(b);
  return aa.length===bb.length && timingSafeEqual(aa,bb);
};

export async function GET(req:NextRequest) {
  const fallback = new URL('/login?error=cognito',siteOrigin());
  const resError = () => {
    const response=NextResponse.redirect(fallback);
    response.cookies.delete(oauthCookieName());
    response.headers.set('Cache-Control','no-store');
    return response;
  };
  const code=req.nextUrl.searchParams.get('code');
  const state=req.nextUrl.searchParams.get('state');
  const saved=req.cookies.get(oauthCookieName())?.value;
  if (!code || !state || !saved) return resError();
  try {
    const {state:expected,verifier}=JSON.parse(saved) as {state:string,verifier:string};
    if (typeof expected!=='string' || typeof verifier!=='string' || !equal(expected,state)) return resError();
    const domain=process.env.COGNITO_DOMAIN;
    const clientId=process.env.COGNITO_APP_CLIENT_ID;
    if (!domain || !clientId || !domain.startsWith('https://')) return resError();
    const result=await fetch(new URL('/oauth2/token',domain),{
      method:'POST',cache:'no-store',headers:{'Content-Type':'application/x-www-form-urlencoded'},
      body:new URLSearchParams({grant_type:'authorization_code',client_id:clientId,
        redirect_uri:`${siteOrigin()}/auth/callback`,code,code_verifier:verifier}),
    });
    if (!result.ok) return resError();
    const token=await result.json() as {access_token?:string,expires_in?:number,token_type?:string};
    if (!token.access_token || token.token_type?.toLowerCase()!=='bearer' ||
        !Number.isInteger(token.expires_in) || !token.expires_in || token.expires_in<1 || token.expires_in>86400) return resError();
    // Cognito userInfo returns verified email/sub; do not infer identity from an unverified JWT payload.
    const userResponse=await fetch(new URL('/oauth2/userInfo',domain),{
      headers:{Authorization:`Bearer ${token.access_token}`},cache:'no-store'
    });
    if (!userResponse.ok) return resError();
    const user=await userResponse.json() as {sub?:string,email?:string,name?:string};
    if (!user.sub || typeof user.sub!=='string') return resError();
    const session=seal({accessToken:token.access_token,
      // small early refresh window; no refresh token stored by phase 3.
      expiresAt:Date.now()+Math.max(1,token.expires_in-30)*1000,
      sub:user.sub,email:typeof user.email==='string'?user.email:null,
      name:typeof user.name==='string'?user.name:null});
    const response=NextResponse.redirect(new URL('/',siteOrigin()));
    response.cookies.set(sessionCookieName(),session,{...cookieOptions(),maxAge:token.expires_in});
    response.cookies.delete(oauthCookieName());
    response.headers.set('Cache-Control','no-store');
    return response;
  } catch {
    return resError();
  }
}
