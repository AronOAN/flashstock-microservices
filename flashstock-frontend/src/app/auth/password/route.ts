import { NextRequest, NextResponse } from 'next/server';
import { authError, challengeCookieName, cognitoCall, cognitoConfig, completeSignIn, isChallenge, readAuthBody, sealChallenge, type CognitoResult } from '@/lib/cognito-password';
import { cookieOptions, siteOrigin } from '@/lib/flashstock-session';

export const runtime = 'nodejs';

export async function POST(req: NextRequest) {
  try {
    siteOrigin();
    const body = await readAuthBody(req);
    if (!body) return authError(400, 'Solicitud inválida');
    const email = body.email;
    const password = body.password;
    if (typeof email !== 'string' || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
        typeof password !== 'string' || password.length < 1 || password.length > 1024) return authError(400, 'Solicitud inválida');
    const { clientId } = cognitoConfig();
    const result = await cognitoCall('InitiateAuth', {
      AuthFlow: 'USER_PASSWORD_AUTH', ClientId: clientId,
      AuthParameters: { USERNAME: email.trim(), PASSWORD: password },
    });
    if (!result.ok) return authError(result.status === 429 ? 429 : 401, 'No se pudo iniciar sesión. Revisa tus credenciales o contacta al administrador.');
    const data = result.data as CognitoResult;
    if (data.AuthenticationResult) return await completeSignIn(data) || authError(502, 'No se pudo completar la sesión');
    if (typeof data.ChallengeName !== 'string' || !isChallenge(data.ChallengeName) ||
        typeof data.Session !== 'string' || data.Session.length > 4096 || data.Session.length < 10) {
      return authError(403, 'Esta cuenta requiere un paso adicional. Contacta al administrador.');
    }
    const name = data.ChallengeName;
    const username = data.ChallengeParameters?.USER_ID_FOR_SRP || email.trim();
    const response = NextResponse.json({ ok: false, challenge: name }, { status: 202, headers: { 'Cache-Control': 'no-store' } });
    response.cookies.set(challengeCookieName(), sealChallenge({ name, username, session: data.Session,
      email: email.trim(), expiresAt: Date.now() + 300000 }), { ...cookieOptions(), maxAge: 300 });
    return response;
  } catch {
    return authError(503, 'Autenticación no disponible. Revisa la configuración del servidor.');
  }
}
