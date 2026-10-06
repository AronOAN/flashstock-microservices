import Image from 'next/image';
import Link from 'next/link';
import AppShell from '@/components/layout/AppShell';
import CatalogClient from '@/components/catalog/CatalogClient';
export default function HomePage() {
  return <AppShell>
    <section className="fs-hero"><div className="fs-hero-copy"><span className="fs-kicker">FlashStock</span>
      <h1>Productos frescos con inventario y pedidos trazables.</h1>
      <p>Una sola interfaz React. La autorización, el stock, el carrito, los pedidos y la sesión se validan en servicios Java.</p>
      <div className="fs-actions"><Link className="fs-button" href="/shop">Comprar ahora</Link><Link className="fs-button fs-button-secondary" href="/order-status">Ver mis pedidos</Link></div>
    </div><div className="fs-hero-image"><Image src="/static/img/hero-img-1.png" alt="Productos frescos FlashStock" width={720} height={560} priority/></div></section>
    <section className="fs-feature-grid"><article><strong>Inventario real</strong><span>El stock viene de Inventory.</span></article>
      <article><strong>Sesión segura</strong><span>Cookies HttpOnly y validación en Auth.</span></article>
      <article><strong>Pedidos propios</strong><span>Ownership validado por el backend.</span></article>
      <article><strong>Entrega trazable</strong><span>Estado de Shipping en tiempo real.</span></article></section>
    <CatalogClient compact/>
  </AppShell>;
}
