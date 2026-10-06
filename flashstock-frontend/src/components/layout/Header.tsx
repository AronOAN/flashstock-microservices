'use client';
import Link from 'next/link';
import { useSession } from '@/components/session/SessionProvider';

export default function Header() {
  const { session, loading, logout } = useSession();
  return (
    <header className="fs-header">
      <div className="fs-topbar"><span>FlashStock · Santiago, Chile</span><span>Compra segura y trazable</span></div>
      <nav className="fs-nav" aria-label="Navegación principal">
        <Link className="fs-brand" href="/">FlashStock</Link>
        <div className="fs-nav-links">
          <Link href="/">Inicio</Link><Link href="/shop">Tienda</Link><Link href="/cart">Carrito</Link>
          <Link href="/checkout">Checkout</Link><Link href="/delivery">Delivery</Link>
          <Link href="/order-status">Pedidos</Link><Link href="/contact">Contacto</Link>
          {session?.admin && <Link href="/admin">Admin</Link>}
        </div>
        <div className="fs-account">
          {loading ? <span>Verificando…</span> : session?.authenticated ? <>
            <span className="fs-user-label">{session.displayName || session.email || 'Usuario'}</span>
            <button className="fs-link-button" type="button" onClick={() => void logout()}>Cerrar sesión</button>
          </> : <Link href="/login">Iniciar sesión</Link>}
        </div>
      </nav>
    </header>
  );
}
