
'use client';

import Link from 'next/link';

import {
  useRouter,
  useSearchParams,
} from 'next/navigation';

import {
  useRef,
  useState,
} from 'react';

import {
  ApiError,
  apiUrl,
  sessionRequest,
} from '@/lib/api-client';

import { useSession } from '@/components/session/SessionProvider';

// ============================================
// TIPOS
// ============================================

type Challenge =
  | 'NEW_PASSWORD_REQUIRED'
  | 'SMS_MFA'
  | 'SOFTWARE_TOKEN_MFA'
  | 'EMAIL_MFA'
  | 'EMAIL_OTP'
  | 'SMS_OTP';

// ============================================
// CONFIGURACIÓN DE DESAFÍOS
// ============================================

const CHALLENGE_LABELS: Record<Challenge, string> = {
  NEW_PASSWORD_REQUIRED:
    'Crea una contraseña nueva',

  SMS_MFA:
    'Código enviado por SMS',

  SOFTWARE_TOKEN_MFA:
    'Código de tu aplicación de autenticación',

  EMAIL_MFA:
    'Código enviado por correo',

  EMAIL_OTP:
    'Código enviado por correo',

  SMS_OTP:
    'Código enviado por SMS',
};

const GENERIC_LOGIN_ERROR =
  'No se pudo iniciar sesión. Revisa tus datos e inténtalo nuevamente.';

// ============================================
// VALIDACIONES
// ============================================

function isChallenge(
  value: unknown
): value is Challenge {
  return (
    typeof value === 'string' &&
    Object.prototype.hasOwnProperty.call(
      CHALLENGE_LABELS,
      value
    )
  );
}

function getLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400:
      case 401:
      case 403:
        return GENERIC_LOGIN_ERROR;

      case 429:
        return 'Demasiados intentos. Intenta nuevamente más tarde.';

      case 502:
      case 503:
      case 504:
        return 'El servicio de autenticación no está disponible.';

      default:
        return 'No fue posible completar la autenticación.';
    }
  }

  return 'No fue posible conectar con el servidor.';
}

// ============================================
// COMPONENTE
// ============================================

export default function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const { refresh } = useSession();

  const loginError =
    searchParams.get('auth_error') === '1';

  // ============================================
  // ESTADOS
  // ============================================

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [answer, setAnswer] = useState('');

  const [challenge, setChallenge] =
    useState<Challenge | null>(null);

  const [message, setMessage] = useState(
    loginError
      ? 'No se pudo completar el acceso con Cognito.'
      : ''
  );

  const [busy, setBusy] = useState(false);

  // Bloqueo inmediato para impedir solicitudes
  // duplicadas antes del siguiente renderizado.
  const submittingRef = useRef(false);

  // ============================================
  // AUTENTICACIÓN
  // ============================================

  async function submit(): Promise<void> {
    if (submittingRef.current) {
      return;
    }

    const normalizedEmail = email.trim();

    if (!challenge) {
      if (
        normalizedEmail.length === 0 ||
        password.length === 0
      ) {
        setMessage('Completa tus credenciales.');
        return;
      }
    } else if (answer.trim().length === 0) {
      setMessage('Ingresa la respuesta solicitada.');
      return;
    }

    submittingRef.current = true;

    setBusy(true);
    setMessage('');

    try {
      const result = await sessionRequest(
        challenge ? 'challenge' : 'login',
        challenge
          ? {
              answer:
                challenge === 'NEW_PASSWORD_REQUIRED'
                  ? answer
                  : answer.trim(),
            }
          : {
              email: normalizedEmail,
              password,
            }
      );

      // ========================================
      // AUTENTICACIÓN COMPLETADA
      // ========================================

      if (result.authenticated === true) {
        // No guardar credenciales en localStorage,
        // sessionStorage ni React Context.
        setPassword('');
        setAnswer('');
        setChallenge(null);

        // api-client conserva el access JWT
        // únicamente en memoria.
        // El refresh real permanece en Auth Java.
        await refresh();

        // Navegación SPA, sin recarga completa.
        router.replace('/');

        return;
      }

      // ========================================
      // DESAFÍO DE COGNITO
      // ========================================

      if (isChallenge(result.challenge)) {
        setChallenge(result.challenge);

        // Limpiar credenciales y respuestas anteriores.
        setPassword('');
        setAnswer('');

        return;
      }

      setMessage(GENERIC_LOGIN_ERROR);
    } catch (error: unknown) {
      setMessage(getLoginError(error));
    } finally {
      submittingRef.current = false;
      setBusy(false);
    }
  }

  // ============================================
  // REINICIAR DESAFÍO
  // ============================================

  function restartLogin(): void {
    if (submittingRef.current) {
      return;
    }

    setChallenge(null);
    setPassword('');
    setAnswer('');
    setMessage('');
  }

  // ============================================
  // INTERFAZ
  // ============================================

  return (
    <div className="fs-login-shell">

      {/* Panel informativo */}
      <aside
        className="fs-login-aside"
        aria-label="FlashStock"
      >
        <Link
          className="fs-login-brand"
          href="/"
          aria-label="FlashStock, volver a la tienda"
        >
          <span
            className="fs-login-mark"
            aria-hidden="true"
          >
            F
          </span>

          <span>FlashStock</span>
        </Link>

        <div className="fs-login-aside-copy">
          <div className="fs-login-kicker">
            Una experiencia más simple
          </div>

          <h1>
            Tu tienda,
            <br />
            <span>tu espacio.</span>
          </h1>

          <p>
            Accede a tu cuenta para gestionar
            tus compras y consultar tus pedidos
            en un mismo lugar.
          </p>
        </div>

        <div className="fs-login-aside-footer">
          FlashStock · Tu acceso, bajo tu control.
        </div>
      </aside>

      {/* Contenido principal */}
      <main
        className="fs-login-main"
        id="contenido"
      >
        <div className="fs-login-top">
          <Link
            href="/"
            className="fs-login-back"
          >
            ← Volver a la tienda
          </Link>
        </div>

        <section
          className="fs-login-card"
          aria-labelledby="login-heading"
          aria-busy={busy}
        >
          <p className="fs-login-eyebrow">
            Bienvenido de nuevo
          </p>

          <h2 id="login-heading">
            {challenge
              ? 'Verifica tu identidad'
              : 'Inicia sesión'}
          </h2>

          <p className="fs-login-intro">
            {challenge
              ? 'Completa la verificación para continuar.'
              : 'Ingresa tu correo y contraseña de FlashStock.'}
          </p>

          {/* Mensajes */}
          {message && (
            <div
              className="fs-login-alert"
              role="alert"
            >
              {message}
            </div>
          )}

          {/* Formulario */}
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            {challenge ? (
              <>
                {/* Verificación MFA */}
                <label
                  className="fs-login-label"
                  htmlFor="challenge-answer"
                >
                  {CHALLENGE_LABELS[challenge]}
                </label>

                <input
                  key={challenge}
                  className="fs-login-input"
                  id="challenge-answer"
                  name="answer"
                  required
                  autoFocus
                  disabled={busy}
                  type={
                    challenge === 'NEW_PASSWORD_REQUIRED'
                      ? 'password'
                      : 'text'
                  }
                  autoComplete={
                    challenge === 'NEW_PASSWORD_REQUIRED'
                      ? 'new-password'
                      : 'one-time-code'
                  }
                  inputMode={
                    challenge === 'NEW_PASSWORD_REQUIRED'
                      ? 'text'
                      : 'numeric'
                  }
                  maxLength={
                    challenge === 'NEW_PASSWORD_REQUIRED'
                      ? 256
                      : 64
                  }
                  value={answer}
                  onChange={(event) => {
                    setAnswer(event.target.value);
                    setMessage('');
                  }}
                />
              </>
            ) : (
              <>
                {/* Correo */}
                <label
                  className="fs-login-label"
                  htmlFor="login-email"
                >
                  Correo electrónico
                </label>

                <input
                  className="fs-login-input"
                  id="login-email"
                  name="email"
                  type="email"
                  autoComplete="username"
                  inputMode="email"
                  placeholder="correo@ejemplo.com"
                  required
                  maxLength={254}
                  disabled={busy}
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    setMessage('');
                  }}
                />

                {/* Contraseña */}
                <label
                  className="fs-login-label"
                  htmlFor="login-password"
                >
                  Contraseña
                </label>

                <input
                  className="fs-login-input"
                  id="login-password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="Tu contraseña"
                  required
                  maxLength={256}
                  disabled={busy}
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    setMessage('');
                  }}
                />
              </>
            )}

            {/* Botón principal */}
            <button
              className="fs-login-primary"
              type="submit"
              disabled={busy}
            >
              {busy
                ? 'Verificando…'
                : challenge
                  ? 'Verificar'
                  : 'Iniciar sesión'}
            </button>
          </form>

          {/* Login con Cognito */}
          {!challenge && (
            <>
              <a
                className="fs-login-pkce"
                href={apiUrl('/api/auth/session/pkce')}
                aria-label="Acceder mediante Cognito"
              >
                Acceder mediante Cognito
              </a>

              <p className="fs-login-method-note">
                Accede con Cognito mediante
                Authorization Code y PKCE.
              </p>
            </>
          )}

          {/* Reiniciar MFA */}
          {challenge && (
            <button
              className="fs-login-restart"
              type="button"
              disabled={busy}
              onClick={restartLogin}
            >
              Volver al inicio de sesión
            </button>
          )}

          {/* Información de seguridad */}
          <p className="fs-login-identity-note">
            Tus credenciales se envían al servicio
            de autenticación de FlashStock mediante
            una conexión segura. Nunca compartas
            tu contraseña ni tus códigos de verificación.
          </p>

          <Link
            className="fs-login-secondary"
            href="/"
          >
            Seguir explorando sin iniciar sesión →
          </Link>
        </section>

        <p className="fs-login-footnote">
          ¿Necesitas ayuda para acceder?
          Contacta al equipo de soporte de FlashStock.
        </p>
      </main>
    </div>
  );
}
