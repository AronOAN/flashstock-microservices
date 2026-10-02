import type { Metadata } from 'next';
import Link from 'next/link';
import './login.css';

export const metadata: Metadata = {
  title: 'Iniciar sesión | FlashStock',
  description: 'Accede a tu cuenta FlashStock mediante el inicio de sesión seguro de Cognito.',
};

type Props = { searchParams: Promise<{ error?: string | string[] }> };

export default async function LoginPage({ searchParams }: Props) {
  const { error } = await searchParams;
  const hasError = Boolean(error);

  return (
    <>
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
            <p className="fs-login-intro">Continúa de forma segura con tu cuenta de FlashStock. Serás dirigido al acceso protegido de nuestro proveedor de identidad.</p>
            {hasError && (
              <div className="fs-login-alert" role="alert">No fue posible completar el inicio de sesión. Inténtalo nuevamente.</div>
            )}
            {/* Native anchor: Cognito OAuth must use a full document navigation, never a Next RSC fetch. */}
            <a className="fs-login-primary" href="/auth/login" target="_top">
              <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><path d="M10 17l5-5-5-5"/><path d="M15 12H3"/></svg>
              Continuar para iniciar sesión
            </a>
            <p className="fs-login-identity-note">
              <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
              <span>Tu contraseña se introduce en la página segura de Amazon Cognito, nunca en esta pantalla.</span>
            </p>
            <Link className="fs-login-secondary" href="/">Seguir explorando sin iniciar sesión →</Link>
          </section>
          <p className="fs-login-footnote">¿Necesitas ayuda para acceder? Contacta al equipo de soporte de FlashStock. Nunca compartas tu contraseña ni códigos de verificación.</p>
        </main>
      </div>
    </>
  );
}
