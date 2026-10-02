import { NextRequest, NextResponse } from 'next/server';
import { authError, challengeCookieName, cognitoCall, cognitoConfig, completeSignIn, isChallenge, openChallenge, readAuthBody, sealChallenge, type CognitoResult } from '@/lib/cognito-password';
import { cookieOptions, siteOrigin } from '@/lib/flashstock-session';

export const runtime = 'nodejs';

export async function POST(req: NextRequest) {
  try {
    siteOrigin();
    const body = await readAuthBody(req);
    if (!body) return authError(400, 'Solicitud inválida');
    const pending = req.cookies.get(challengeCookieName())?.value;
    const state = pending ? openChallenge(pending) : null;
    if (!state) return authError(401, 'El paso de verificación caducó. Inicia sesión nuevamente.');
    const answer = state.name === 'NEW_PASSWORD_REQUIRED' ? body.newPassword : body.code;
    if (typeof answer !== 'string' || answer.length < 1 || answer.length > 1024 ||
        (state.name !== 'NEW_PASSWORD_REQUIRED' && !/^\d{4,10}$/.test(answer))) return authError(400, 'Código o contraseña inválido');
    const codeKey: Record<string, string> = { SMS_MFA: 'SMS_MFA_CODE', SOFTWARE_TOKEN_MFA: 'SOFTWARE_TOKEN_MFA_CODE',
      EMAIL_MFA: 'EMAIL_MFA_CODE', EMAIL_OTP: 'EMAIL_OTP_CODE', SMS_OTP: 'SMS_OTP_CODE' };
    const { clientId } = cognitoConfig();
    const result = await cognitoCall('RespondToAuthChallenge', {
      ClientId: clientId, ChallengeName: state.name, Session: state.session,
      ChallengeResponses: { USERNAME: state.username,
        [state.name === 'NEW_PASSWORD_REQUIRED' ? 'NEW_PASSWORD' : codeKey[state.name]]: answer },
    });
    if (!result.ok) return authError(result.status === 429 ? 429 : 401, 'No se pudo verificar. Inicia sesión nuevamente si caducó el código.');
    const data = result.data as CognitoResult;
    if (data.AuthenticationResult) return await completeSignIn(data) || authError(502, 'No se pudo completar la sesión');
    if (!data.ChallengeName || !isChallenge(data.ChallengeName) || !data.Session) return authError(403, 'Contacta al administrador para terminar el acceso.');
    const response = NextResponse.json({ ok: false, challenge: data.ChallengeName }, { status: 202, headers: { 'Cache-Control': 'no-store' } });
    response.cookies.set(challengeCookieName(), sealChallenge({ name: data.ChallengeName,
      username: data.ChallengeParameters?.USER_ID_FOR_SRP || state.username, session: data.Session,
      email: state.email, expiresAt: Date.now() + 300000 }), { ...cookieOptions(), maxAge: 300 });
    return response;
  } catch {
    return authError(503, 'Autenticación no disponible. Revisa la configuración del servidor.');
  }
}
