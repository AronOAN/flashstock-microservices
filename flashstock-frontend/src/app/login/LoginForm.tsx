'use client';

import Link from 'next/link';
import { useState, type FormEvent } from 'react';

type Challenge = 'NEW_PASSWORD_REQUIRED' | 'SMS_MFA' | 'SOFTWARE_TOKEN_MFA' | 'EMAIL_MFA' | 'EMAIL_OTP' | 'SMS_OTP';
const challengeLabels: Record<Challenge, string> = {
  NEW_PASSWORD_REQUIRED: 'Crea una contraseña nueva', SMS_MFA: 'Código enviado por SMS',
  SOFTWARE_TOKEN_MFA: 'Código de tu aplicación de autenticación',
  EMAIL_MFA: 'Código enviado por correo', EMAIL_OTP: 'Código enviado por correo', SMS_OTP: 'Código enviado por SMS',
};

export default function LoginForm({ loginError = false }: { loginError?: boolean }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [answer, setAnswer] = useState('');
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  const [message, setMessage] = useState(loginError ? 'No se pudo completar el acceso con Cognito. Inténtalo nuevamente.' : '');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setMessage('');
    try {
      const response = await fetch(challenge ? '/auth/password/challenge' : '/auth/password', {
        method: 'POST', credentials: 'same-origin', cache: 'no-store',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(challenge ? (challenge === 'NEW_PASSWORD_REQUIRED' ? { newPassword: answer } : { code: answer }) : { email, password }),
      });
      const result: { ok?: boolean; challenge?: Challenge; message?: string } = await response.json();
      if (response.ok && result.ok) {
        setPassword(''); setAnswer('');
        window.location.assign('/');
        return;
      }
      if (response.status === 202 && result.challenge && result.challenge in challengeLabels) {
        setChallenge(result.challenge); setPassword(''); setAnswer('');
        return;
      }
      setMessage(result.message || 'No se pudo iniciar sesión. Inténtalo nuevamente.');
    } catch {
      setMessage('No fue posible conectar con el servidor. Inténtalo nuevamente.');
    } finally { setBusy(false); }
  }

  return (
    <div className="fs-login-shell">
      <aside className="fs-login-aside" aria-label="FlashStock">
        <Link className="fs-login-brand" href="/" aria-label="FlashStock, volver a la tienda">
          <span className="fs-login-mark" aria-hidden="true">F</span><span>FlashStock</span>
        </Link>
        <div className="fs-login-aside-copy">
          <div className="fs-login-kicker">Una experiencia más simple</div>
          <h1>Tu tienda,<br /><span>tu espacio.</span></h1>
          <p>Accede a tu cuenta para gestionar tus compras y consultar tus pedidos en un mismo lugar.</p>
        </div>
        <div className="fs-login-aside-footer">FlashStock · Tu acceso, bajo tu control.</div>
      </aside>
      <main className="fs-login-main" id="contenido">
        <div className="fs-login-top"><Link href="/" className="fs-login-back">← Volver a la tienda</Link></div>
        <section className="fs-login-card" aria-labelledby="login-heading">
          <p className="fs-login-eyebrow">Bienvenido de nuevo</p>
          <h2 id="login-heading">Inicia sesión</h2>
          <p className="fs-login-intro">Ingresa tu correo y contraseña de FlashStock.</p>
          {message && <div className="fs-login-alert" role="alert">{message}</div>}
          <form onSubmit={submit}>
            {challenge ? <>
              <label className="fs-login-label" htmlFor="challenge-answer">{challengeLabels[challenge]}</label>
              <input className="fs-login-input" id="challenge-answer" required autoFocus
                type={challenge === 'NEW_PASSWORD_REQUIRED' ? 'password' : 'text'}
                autoComplete={challenge === 'NEW_PASSWORD_REQUIRED' ? 'new-password' : 'one-time-code'}
                inputMode={challenge === 'NEW_PASSWORD_REQUIRED' ? 'text' : 'numeric'}
                value={answer} onChange={event => setAnswer(event.target.value)} />
            </> : <>
              <label className="fs-login-label" htmlFor="login-email">Correo electrónico</label>
              <input className="fs-login-input" id="login-email" type="email" name="email" autoComplete="username"
                required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} />
              <label className="fs-login-label" htmlFor="login-password">Contraseña</label>
              <input className="fs-login-input" id="login-password" type="password" name="password" autoComplete="current-password"
                required value={password} onChange={event => setPassword(event.target.value)} />
            </>}
            <button className="fs-login-primary" type="submit" disabled={busy}>
              {busy ? 'Verificando…' : challenge ? 'Verificar' : 'Iniciar sesión'}
            </button>
          </form>
          {!challenge && <Link className="fs-login-pkce" href="/auth/pkce">
            Acceder mediante Cognito
          </Link>}
          {!challenge && <p className="fs-login-method-note">Esta opción abre la página de Cognito y usa Authorization Code con PKCE.</p>}
          {challenge && <button className="fs-login-restart" type="button" onClick={() => { setChallenge(null); setAnswer(''); setMessage(''); }}>
            Volver al inicio de sesión
          </button>}
          <p className="fs-login-identity-note">Tus credenciales se envían al servidor de FlashStock, que solicita la autenticación a Amazon Cognito. Nunca compartas tu contraseña.</p>
          <Link className="fs-login-secondary" href="/">Seguir explorando sin iniciar sesión →</Link>
        </section>
        <p className="fs-login-footnote">¿Necesitas ayuda para acceder? Contacta al equipo de soporte de FlashStock.</p>
      </main>
    </div>
  );
}
