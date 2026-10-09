
'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import {
  useRef,
  useState,
} from 'react';

import { useSession } from '@/components/session/SessionProvider';

type NavigationItem = {
  href: string;
  label: string;
  requiresAuth?: boolean;
  requiresAdmin?: boolean;
};

const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  { href: '/', label: 'Inicio' },
  { href: '/shop', label: 'Tienda' },
  { href: '/cart', label: 'Carrito', requiresAuth: true },
  { href: '/checkout', label: 'Checkout', requiresAuth: true },
  { href: '/delivery', label: 'Delivery', requiresAuth: true },
  { href: '/order-status', label: 'Mis pedidos', requiresAuth: true },
  { href: '/contact', label: 'Contacto' },
  {
    href: '/admin',
    label: 'Administración',
    requiresAuth: true,
    requiresAdmin: true,
  },
];

function isActiveRoute(
  pathname: string,
  href: string
): boolean {
  if (href === '/') {
    return pathname === '/';
  }

  return pathname === href ||
    pathname.startsWith(`${href}/`);
}

export default function Header() {
  const pathname = usePathname();

  const {
    session,
    loading,
    error,
    refresh,
    logout,
  } = useSession();

  const [menuOpen, setMenuOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(
    null
  );

  const logoutLock = useRef(false);

  const isAuthenticated =
    !loading &&
    !error &&
    session?.authenticated === true;

  const isAdmin =
    isAuthenticated && session?.admin === true;

  const displayName =
    session?.displayName?.trim() ||
    session?.email?.split('@')[0] ||
    'Usuario';

  const visibleItems = NAVIGATION_ITEMS.filter((item) => {
    if (item.requiresAuth && !isAuthenticated) {
      return false;
    }

    if (item.requiresAdmin && !isAdmin) {
      return false;
    }

    return true;
  });

  function closeMenu(): void {
    setMenuOpen(false);
  }

  async function handleLogout(): Promise<void> {
    if (logoutLock.current) {
      return;
    }

    logoutLock.current = true;

    setLoggingOut(true);
    setLogoutError(null);
    closeMenu();

    try {
      await logout();
    } catch {
      setLogoutError('No se pudo cerrar la sesión.');
    } finally {
      logoutLock.current = false;
      setLoggingOut(false);
    }
  }

  return (
    <header className="fs-header">
      {/* Barra superior */}
      <div className="fs-topbar">
        <span>FlashStock · Santiago, Chile</span>
        <span>Compra segura y trazable</span>
      </div>

      {/* Navegación principal */}
      <nav
        className="fs-nav"
        aria-label="Navegación principal"
      >
        {/* Marca */}
        <Link
          className="fs-brand"
          href="/"
          onClick={closeMenu}
        >
          Flash<span>Stock</span>
        </Link>

        {/* Botón responsive */}
        <button
          className="fs-menu-toggle"
          type="button"
          aria-label={
            menuOpen ? 'Cerrar menú' : 'Abrir menú'
          }
          aria-expanded={menuOpen}
          aria-controls="fs-nav-links"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span aria-hidden="true">
            {menuOpen ? '✕' : '☰'}
          </span>
        </button>

        {/* Enlaces */}
        <div
          id="fs-nav-links"
          className={`fs-nav-links ${
            menuOpen ? 'is-open' : ''
          }`}
        >
          {visibleItems.map((item) => {
            const active = isActiveRoute(
              pathname ?? '',
              item.href
            );

            return (
              <Link
                key={item.href}
                href={item.href}
                className={
                  active ? 'fs-nav-active' : undefined
                }
                aria-current={active ? 'page' : undefined}
                onClick={closeMenu}
              >
                {item.label}
              </Link>
            );
          })}
        </div>

        {/* Cuenta de usuario */}
        <div className="fs-account">
          {loading ? (
            <span
              className="fs-account-loading"
              role="status"
            >
              Verificando sesión…
            </span>
          ) : error ? (
            <div className="fs-account-actions">
              <span className="fs-account-error" role="alert">
                Sesión no disponible
              </span>

              <button
                className="fs-link-button"
                type="button"
                onClick={() => void refresh()}
              >
                Reintentar
              </button>
            </div>
          ) : isAuthenticated ? (
            <div className="fs-account-authenticated">
              <div className="fs-user-info">
                <span className="fs-user-caption">
                  {isAdmin ? 'Administrador' : 'Mi cuenta'}
                </span>

                <strong
                  className="fs-user-label"
                  title={displayName}
                >
                  {displayName}
                </strong>
              </div>

              <button
                className="fs-link-button fs-logout-button"
                type="button"
                onClick={() => void handleLogout()}
                disabled={loggingOut}
                aria-busy={loggingOut}
              >
                {loggingOut
                  ? 'Cerrando sesión…'
                  : 'Cerrar sesión'}
              </button>
            </div>
          ) : (
            <Link
              className="fs-login-button"
              href="/login"
              onClick={closeMenu}
            >
              Iniciar sesión
            </Link>
          )}
        </div>
      </nav>

      {(logoutError || (!loading && error && session?.authenticated)) && (
        <p className="fs-header-error" role="alert">
          {logoutError || 'No se pudo verificar la sesión.'}
        </p>
      )}
    </header>
  );
}
