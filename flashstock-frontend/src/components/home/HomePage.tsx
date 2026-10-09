
import Image from 'next/image';
import Link from 'next/link';

import AppShell from '@/components/layout/AppShell';
import CatalogClient from '@/components/catalog/CatalogClient';

// Características visibles en la página principal.
const FEATURES = [
  {
    title: 'Inventario actualizado',
    description:
      'Consulta los productos y su disponibilidad desde Inventory.',
  },
  {
    title: 'Cuenta protegida',
    description:
      'Autenticación y control de acceso gestionados por el backend.',
  },
  {
    title: 'Tus pedidos',
    description:
      'Consulta el historial y el estado de tus compras.',
  },
  {
    title: 'Seguimiento de entregas',
    description:
      'Revisa la información de despacho disponible en Shipping.',
  },
] as const;

export default function HomePage() {
  return (
    <AppShell>
      {/* Sección principal */}
      <section
        className="fs-hero"
        aria-labelledby="home-title"
      >
        <div className="fs-hero-copy">
          <span className="fs-kicker">
            FlashStock
          </span>

          <h1 id="home-title">
            Productos frescos para tus compras diarias.
          </h1>

          <p>
            Explora nuestro catálogo, consulta la
            disponibilidad de productos y realiza
            tus compras desde un solo lugar.
          </p>

          <div className="fs-actions">
            <Link
              className="fs-button"
              href="/shop"
            >
              Comprar ahora
            </Link>

            <Link
              className="fs-button fs-button-secondary"
              href="/order-status"
            >
              Ver mis pedidos
            </Link>
          </div>
        </div>

        {/* Imagen principal */}
        <div className="fs-hero-image">
          <Image
            src="/static/img/hero-img-1.png"
            alt="Productos frescos de FlashStock"
            width={720}
            height={560}
            sizes="(max-width: 900px) 100vw, 42vw"
            preload
            style={{
              width: '100%',
              height: 'auto',
              objectFit: 'contain',
            }}
          />
        </div>
      </section>

      {/* Características */}
      <section
        className="fs-feature-grid"
        aria-label="Características de FlashStock"
      >
        {FEATURES.map((feature) => (
          <article key={feature.title}>
            <strong>
              {feature.title}
            </strong>

            <span>
              {feature.description}
            </span>
          </article>
        ))}
      </section>

      {/* Catálogo conectado al backend */}
      <CatalogClient compact />
    </AppShell>
  );
}
