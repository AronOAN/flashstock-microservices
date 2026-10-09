
'use client';

import Image from 'next/image';
import Link from 'next/link';

import { useRouter } from 'next/navigation';

import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import {
  ApiError,
  apiMutation,
  apiRequest,
} from '@/lib/api-client';

import { useSession } from '@/components/session/SessionProvider';

import type { CatalogItem } from '@/types/domain';

// ============================================
// CONFIGURACIÓN
// ============================================

const FALLBACK_IMAGE =
  '/static/img/vegetable-item-1.jpg';

const COMPACT_LIMIT = 8;

const CLP_FORMATTER = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

// Solo imágenes locales permitidas.
// Rechaza URLs externas, rutas relativas,
// traversal, parámetros y protocolos.
const ALLOWED_IMAGE_PATH =
  /^\/static\/img\/(?:[a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_-]+\.(?:png|jpe?g|webp|avif)$/i;

// ============================================
// TIPOS
// ============================================

type CatalogState =
  | { status: 'loading' }
  | { status: 'success'; items: CatalogItem[] }
  | { status: 'error'; message: string };

type Feedback =
  | { type: 'success'; message: string }
  | { type: 'error'; message: string }
  | null;

type CatalogClientProps = Readonly<{
  compact?: boolean;
}>;

// ============================================
// UTILIDADES
// ============================================

function formatMoney(
  value: number | null | undefined
): string {
  const amount =
    typeof value === 'number' && Number.isFinite(value)
      ? value
      : 0;

  return CLP_FORMATTER.format(amount);
}

function safeImageSource(
  value?: string | null
): string {
  if (!value) return FALLBACK_IMAGE;

  const source = value.trim();

  return ALLOWED_IMAGE_PATH.test(source)
    ? source
    : FALLBACK_IMAGE;
}

function normalizeSearch(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es-CL')
    .trim();
}

// Validación mínima del contrato recibido.
function isCatalogItem(
  value: unknown
): value is CatalogItem {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const item = value as Partial<CatalogItem>;

  return (
    typeof item.sku === 'string' &&
    item.sku.trim().length > 0 &&
    typeof item.name === 'string' &&
    item.name.trim().length > 0 &&
    typeof item.unitPrice === 'number' &&
    Number.isFinite(item.unitPrice) &&
    item.unitPrice >= 0 &&
    typeof item.availableQuantity === 'number' &&
    Number.isInteger(item.availableQuantity) &&
    item.availableQuantity >= 0
  );
}

// Mensajes controlados para errores del API.
function getCatalogError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401:
        return 'No se pudo autorizar la consulta del catálogo.';

      case 403:
        return 'No tienes permisos para consultar el catálogo.';

      case 404:
        return 'El catálogo no está disponible.';

      case 429:
        return 'Demasiadas solicitudes. Intenta nuevamente.';

      default:
        return 'No se pudo obtener el catálogo.';
    }
  }

  return 'No fue posible cargar los productos.';
}

function getCartError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 403:
        return 'No tienes permisos para modificar el carrito.';

      case 404:
        return 'El producto solicitado no está disponible.';

      case 409:
        return 'El producto ya no tiene stock suficiente.';

      case 422:
        return 'No se pudo agregar el producto solicitado.';

      case 429:
        return 'Demasiadas solicitudes. Intenta nuevamente.';

      default:
        return 'No se pudo actualizar el carrito. ' +
          'Comprueba su contenido antes de reintentar.';
    }
  }

  return 'No se pudo actualizar el carrito. ' +
    'Comprueba su contenido antes de reintentar.';
}

// ============================================
// COMPONENTE
// ============================================

export default function CatalogClient({
  compact = false,
}: CatalogClientProps) {
  const router = useRouter();

  const {
    session,
    loading: sessionLoading,
    error: sessionError,
    refresh,
  } = useSession();

  const [catalog, setCatalog] = useState<CatalogState>({
    status: 'loading',
  });

  const [query, setQuery] = useState('');

  const [feedback, setFeedback] = useState<Feedback>(null);

  const [addingSku, setAddingSku] = useState<string | null>(
    null
  );

  const [reloadVersion, setReloadVersion] = useState(0);

  // Bloqueo inmediato: evita doble clic antes
  // de que React actualice el estado.
  const addingRef = useRef(false);

  // Evita actualizaciones después del desmontaje.
  const mountedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  // ============================================
  // CARGAR CATÁLOGO
  // ============================================

  useEffect(() => {
    let active = true;

    async function loadCatalog(): Promise<void> {
      try {
        const response = await apiRequest<unknown>(
          '/api/catalog'
        );

        if (!active) return;

        if (
          !Array.isArray(response) ||
          !response.every(isCatalogItem)
        ) {
          throw new Error(
            'Contrato del catálogo inválido'
          );
        }

        setCatalog({
          status: 'success',
          items: response,
        });
      } catch (error: unknown) {
        if (!active) return;

        setCatalog({
          status: 'error',
          message: getCatalogError(error),
        });
      }
    }

    void loadCatalog();

    return () => {
      active = false;
    };
  }, [reloadVersion]);

  function retryCatalog(): void {
    setFeedback(null);
    setCatalog({ status: 'loading' });

    setReloadVersion((previous) => previous + 1);
  }

  // ============================================
  // FILTRAR PRODUCTOS
  // ============================================

  const visibleItems = useMemo(() => {
    if (catalog.status !== 'success') {
      return [];
    }

    const normalizedQuery = normalizeSearch(query);

    const filtered = normalizedQuery
      ? catalog.items.filter((item) => {
          const searchableText = [
            item.name,
            item.sku,
            item.category,
            item.description,
          ]
            .filter(Boolean)
            .map((value) =>
              normalizeSearch(String(value))
            )
            .join(' ');

          return searchableText.includes(normalizedQuery);
        })
      : catalog.items;

    return compact
      ? filtered.slice(0, COMPACT_LIMIT)
      : filtered;
  }, [catalog, query, compact]);

  // ============================================
  // AGREGAR PRODUCTO AL CARRITO
  // ============================================

  async function addToCart(sku: string): Promise<void> {
    const normalizedSku = sku.trim();

    // Evitar operaciones simultáneas.
    if (addingRef.current) {
      return;
    }

    if (!normalizedSku) {
      setFeedback({
        type: 'error',
        message: 'El producto no tiene un SKU válido.',
      });
      return;
    }

    // Esperar a que se verifique la sesión.
    if (sessionLoading) {
      return;
    }

    if (sessionError) {
      setFeedback({
        type: 'error',
        message:
          'No se pudo verificar tu sesión. ' +
          'Intenta actualizarla antes de continuar.',
      });
      return;
    }

    // Redirigir al login si no está autenticado.
    if (!session?.authenticated) {
      router.push('/login');
      return;
    }

    // El cliente solo controla la interfaz.
    // El backend debe verificar disponibilidad.
    addingRef.current = true;

    setAddingSku(normalizedSku);
    setFeedback(null);

    try {
      await apiMutation(
        `/api/cart/items/${encodeURIComponent(normalizedSku)}`,
        'POST',
        { quantity: 1 }
      );

      if (!mountedRef.current) return;

      setFeedback({
        type: 'success',
        message: 'Producto agregado al carrito.',
      });
    } catch (error: unknown) {
      if (!mountedRef.current) return;

      if (
        error instanceof ApiError &&
        error.status === 401
      ) {
        router.push('/login');
        return;
      }

      setFeedback({
        type: 'error',
        message: getCartError(error),
      });
    } finally {
      addingRef.current = false;

      if (mountedRef.current) {
        setAddingSku(null);
      }
    }
  }

  // ============================================
  // RENDERIZADO
  // ============================================

  return (
    <section
      className="fs-section"
      aria-labelledby={
        compact ? 'featured-products' : 'catalog-title'
      }
      aria-busy={catalog.status === 'loading'}
    >
      {/* Encabezado */}
      <div className="fs-section-heading">
        <div>
          <span className="fs-kicker">
            Catálogo
          </span>

          <h2
            id={
              compact
                ? 'featured-products'
                : 'catalog-title'
            }
          >
            {compact
              ? 'Productos destacados'
              : 'Tienda FlashStock'}
          </h2>
        </div>

        {/* Buscador */}
        {!compact && (
          <input
            className="fs-input fs-search"
            type="search"
            aria-label="Buscar productos"
            placeholder="Buscar por producto, SKU o categoría"
            maxLength={100}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setFeedback(null);
            }}
          />
        )}
      </div>

      {/* Estado de verificación de sesión */}
      {sessionError && (
        <div>
          <p className="fs-status" role="alert">
            No se pudo verificar tu sesión.
          </p>

          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={() => void refresh()}
          >
            Reintentar sesión
          </button>
        </div>
      )}

      {/* Resultado de agregar al carrito */}
      {feedback && (
        <p
          className="fs-status"
          role={
            feedback.type === 'error'
              ? 'alert'
              : 'status'
          }
        >
          {feedback.message}
        </p>
      )}

      {/* Cargando */}
      {catalog.status === 'loading' && (
        <p className="fs-status" role="status">
          Cargando productos…
        </p>
      )}

      {/* Error */}
      {catalog.status === 'error' && (
        <div>
          <p className="fs-status" role="alert">
            {catalog.message}
          </p>

          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={retryCatalog}
          >
            Reintentar
          </button>
        </div>
      )}

      {/* Catálogo disponible */}
      {catalog.status === 'success' && (
        <>
          {visibleItems.length === 0 ? (
            <div className="fs-empty">
              <h3>
                {query
                  ? 'No encontramos productos'
                  : 'No hay productos disponibles'}
              </h3>

              <p>
                {query
                  ? 'Prueba con otro nombre, SKU o categoría.'
                  : 'El catálogo está vacío por el momento.'}
              </p>
            </div>
          ) : (
            <div className="fs-product-grid">
              {visibleItems.map((item) => {
                const hasStock =
                  item.availableQuantity > 0;

                const isAdding =
                  addingSku === item.sku;

                const buttonDisabled =
                  !hasStock ||
                  addingSku !== null ||
                  sessionLoading;

                return (
                  <article
                    className="fs-product-card"
                    key={item.sku}
                  >
                    {/* Imagen validada */}
                    <Image
                      src={safeImageSource(item.imageUrl)}
                      alt={item.name}
                      width={640}
                      height={480}
                      sizes={
                        '(max-width: 560px) 100vw, ' +
                        '(max-width: 900px) 50vw, 25vw'
                      }
                      loading="lazy"
                    />

                    <div className="fs-product-card-body">
                      <span className="fs-chip">
                        {item.category || 'Producto'}
                      </span>

                      <h3>{item.name}</h3>

                      <p>
                        {item.description ||
                          `SKU ${item.sku}`}
                      </p>

                      <div className="fs-product-meta">
                        <strong>
                          {formatMoney(item.unitPrice)}
                        </strong>

                        <span>
                          {item.availableQuantity}{' '}
                          disponibles
                        </span>
                      </div>

                      <button
                        className="fs-button"
                        type="button"
                        disabled={buttonDisabled}
                        aria-busy={isAdding}
                        onClick={() => {
                          void addToCart(item.sku);
                        }}
                      >
                        {!hasStock
                          ? 'Sin stock'
                          : isAdding
                            ? 'Agregando…'
                            : 'Agregar al carrito'}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </>
      )}

      {/* Acceso a la tienda completa */}
      {compact && catalog.status === 'success' && (
        <div className="fs-center">
          <Link
            className="fs-button fs-button-secondary"
            href="/shop"
          >
            Ver tienda completa
          </Link>
        </div>
      )}
    </section>
  );
}
