
'use client';

import Link from 'next/link';

import {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  ApiError,
  apiMutation,
  apiRequest,
} from '@/lib/api-client';

import { useSession } from '@/components/session/SessionProvider';

import type { CartItem } from '@/types/domain';

// ============================================
// CONFIGURACIÓN
// ============================================

const CLP_FORMATTER = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

// ============================================
// TIPOS
// ============================================

type CartState =
  | { status: 'loading' }
  | { status: 'success'; items: CartItem[] }
  | { status: 'error'; message: string };

type Feedback = {
  type: 'success' | 'error';
  message: string;
};

type CartRowProps = {
  item: CartItem;
  busy: boolean;
  onUpdate: (sku: string, quantity: number) => Promise<void>;
  onRemove: (sku: string) => Promise<void>;
};

// ============================================
// UTILIDADES
// ============================================

function formatMoney(value: number): string {
  if (!Number.isFinite(value) || value < 0) {
    return 'No disponible';
  }

  return CLP_FORMATTER.format(value);
}

function isCartItem(value: unknown): value is CartItem {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const item = value as Record<string, unknown>;

  return (
    typeof item.inventoryId === 'number' &&
    Number.isSafeInteger(item.inventoryId) &&
    item.inventoryId >= 0 &&
    typeof item.sku === 'string' &&
    item.sku.trim().length > 0 &&
    typeof item.productName === 'string' &&
    typeof item.quantity === 'number' &&
    Number.isSafeInteger(item.quantity) &&
    item.quantity > 0 &&
    typeof item.unitPrice === 'number' &&
    Number.isFinite(item.unitPrice) &&
    item.unitPrice >= 0 &&
    typeof item.availableQuantity === 'number' &&
    Number.isSafeInteger(item.availableQuantity) &&
    item.availableQuantity >= 0 &&
    Number.isFinite(item.unitPrice * item.quantity)
  );
}

function getCartError(
  error: unknown,
  action: 'load' | 'update'
): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400:
      case 422:
        return 'Los datos de la operación no son válidos.';

      case 401:
        return 'Tu sesión ha expirado. Inicia sesión nuevamente.';

      case 403:
        return 'No tienes permisos para acceder a este carrito.';

      case 404:
        return action === 'load'
          ? 'El servicio del carrito no está disponible.'
          : 'El producto ya no está disponible en tu carrito.';

      case 409:
        return 'El carrito o el inventario cambió. ' +
          'Revisa las cantidades disponibles.';

      case 429:
        return 'Demasiadas solicitudes. Intenta más tarde.';

      default:
        return 'No se pudo completar la operación. ' +
          'Consulta el estado actualizado del carrito.';
    }
  }

  return action === 'load'
    ? 'No se pudo cargar el carrito.'
    : 'No se pudo confirmar la operación. ' +
        'Revisa el carrito actualizado.';
}

// ============================================
// FILA EDITABLE DEL CARRITO
// ============================================

function CartRow({
  item,
  busy,
  onUpdate,
  onRemove,
}: CartRowProps) {
  const [quantity, setQuantity] = useState(
    String(item.quantity)
  );

  const [inputError, setInputError] = useState<
    string | null
  >(null);

  const outOfStock =
    item.quantity > item.availableQuantity;

  const hasChanges =
    quantity.trim() !== String(item.quantity);

  function submitQuantity(): void {
    const normalized = quantity.trim();
    const parsed = Number(normalized);

    if (
      normalized.length === 0 ||
      !Number.isSafeInteger(parsed) ||
      parsed < 1 ||
      parsed > item.availableQuantity
    ) {
      setInputError(
        `Ingresa una cantidad válida entre 1 y ${item.availableQuantity}.`
      );
      return;
    }

    setInputError(null);

    if (parsed === item.quantity) {
      return;
    }

    void onUpdate(item.sku, parsed);
  }

  return (
    <tr>
      <td>
        <strong>{item.productName}</strong>
      </td>

      <td>{item.sku}</td>

      <td>{formatMoney(item.unitPrice)}</td>

      <td>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            submitQuantity();
          }}
        >
          <div className="fs-cart-quantity">
            <input
              className="fs-qty"
              type="number"
              name="quantity"
              aria-label={
                `Cantidad de ${item.productName}`
              }
              min={1}
              max={
                item.availableQuantity > 0
                  ? item.availableQuantity
                  : undefined
              }
              step={1}
              required
              disabled={
                busy || item.availableQuantity === 0
              }
              value={quantity}
              onChange={(event) => {
                setQuantity(event.target.value);
                setInputError(null);
              }}
            />

            <button
              className="fs-link-button"
              type="submit"
              disabled={
                busy ||
                !hasChanges ||
                item.availableQuantity === 0
              }
            >
              Actualizar
            </button>
          </div>
        </form>

        {inputError && (
          <p className="fs-status" role="alert">
            {inputError}
          </p>
        )}

        {outOfStock && (
          <p className="fs-status" role="status">
            Stock disponible: {item.availableQuantity}
          </p>
        )}
      </td>

      <td>
        {formatMoney(item.unitPrice * item.quantity)}
      </td>

      <td>
        <button
          className="fs-link-button fs-danger"
          type="button"
          disabled={busy}
          onClick={() => {
            void onRemove(item.sku);
          }}
          aria-label={`Quitar ${item.productName}`}
        >
          Quitar
        </button>
      </td>
    </tr>
  );
}

// ============================================
// CARRITO AUTENTICADO
// ============================================

function AuthenticatedCart({
  refreshSession,
}: {
  refreshSession: () => Promise<void>;
}) {
  const [cart, setCart] = useState<CartState>({
    status: 'loading',
  });

  const [feedback, setFeedback] = useState<
    Feedback | null
  >(null);

  const [pendingSku, setPendingSku] = useState<
    string | null
  >(null);

  const [reloadVersion, setReloadVersion] = useState(0);

  // Evita mutaciones simultáneas.
  const mutationLockRef = useRef(false);

  // Evita actualizar componentes desmontados.
  const mountedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  // ============================================
  // CARGAR CARRITO
  // ============================================

  useEffect(() => {
    let active = true;

    const controller = new AbortController();

    async function loadCart(): Promise<void> {
      try {
        const response = await apiRequest<unknown>(
          '/api/cart',
          {
            signal: controller.signal,
          }
        );

        if (!active) return;

        if (
          !Array.isArray(response) ||
          !response.every(isCartItem)
        ) {
          throw new Error('Contrato del carrito inválido');
        }

        const uniqueSkus = new Set(
          response.map((item) => item.sku)
        );

        if (uniqueSkus.size !== response.length) {
          throw new Error('SKU duplicado en el carrito');
        }

        setCart({
          status: 'success',
          items: response,
        });
      } catch (error: unknown) {
        if (!active) return;

        setCart({
          status: 'error',
          message: getCartError(error, 'load'),
        });

        if (
          error instanceof ApiError &&
          error.status === 401
        ) {
          void refreshSession();
        }
      }
    }

    void loadCart();

    return () => {
      active = false;
      controller.abort();
    };
  }, [reloadVersion, refreshSession]);

  function reloadCart(): void {
    if (mutationLockRef.current) {
      return;
    }

    setCart({ status: 'loading' });
    setFeedback(null);

    setReloadVersion((previous) => previous + 1);
  }

  // ============================================
  // MUTACIONES DEL CARRITO
  // ============================================

  async function mutateCart(
    sku: string,
    quantity: number | null
  ): Promise<void> {
    if (
      mutationLockRef.current ||
      cart.status !== 'success'
    ) {
      return;
    }

    const item = cart.items.find(
      (current) => current.sku === sku
    );

    if (!item) {
      return;
    }

    if (
      quantity !== null &&
      (
        !Number.isSafeInteger(quantity) ||
        quantity < 1 ||
        quantity > item.availableQuantity
      )
    ) {
      setFeedback({
        type: 'error',
        message: 'La cantidad solicitada no es válida.',
      });
      return;
    }

    if (quantity !== null && quantity === item.quantity) {
      return;
    }

    // Bloqueo inmediato antes de enviar HTTP.
    mutationLockRef.current = true;

    setPendingSku(sku);
    setFeedback(null);

    try {
      const path =
        `/api/cart/items/${encodeURIComponent(sku)}`;

      if (quantity === null) {
        await apiMutation<unknown>(path, 'DELETE');
      } else {
        await apiMutation<unknown>(
          path,
          'PATCH',
          { quantity }
        );
      }

      if (!mountedRef.current) return;

      setFeedback({
        type: 'success',
        message: quantity === null
          ? 'Producto eliminado del carrito.'
          : 'Cantidad actualizada correctamente.',
      });
    } catch (error: unknown) {
      if (!mountedRef.current) return;

      setFeedback({
        type: 'error',
        message: getCartError(error, 'update'),
      });

      if (
        error instanceof ApiError &&
        error.status === 401
      ) {
        void refreshSession();
      }
    } finally {
      mutationLockRef.current = false;

      if (mountedRef.current) {
        setPendingSku(null);

        // Siempre consultar el estado del backend.
        // Incluso si la respuesta HTTP fue un error.
        setCart({ status: 'loading' });

        setReloadVersion(
          (previous) => previous + 1
        );
      }
    }
  }

  // ============================================
  // RENDERIZADO
  // ============================================

  if (cart.status === 'loading') {
    return (
      <section className="fs-section" aria-busy="true">
        <p className="fs-status" role="status">
          Cargando carrito…
        </p>
      </section>
    );
  }

  if (cart.status === 'error') {
    return (
      <section className="fs-section">
        <h2>No se pudo cargar el carrito</h2>

        <p className="fs-status" role="alert">
          {cart.message}
        </p>

        <button
          className="fs-button fs-button-secondary"
          type="button"
          onClick={reloadCart}
        >
          Reintentar
        </button>
      </section>
    );
  }

  const items = cart.items;

  // Total informativo: el backend calcula
  // los importes definitivos durante el checkout.
  const subtotal = items.reduce(
    (sum, item) =>
      sum + item.unitPrice * item.quantity,
    0
  );

  const totalUnits = items.reduce(
    (sum, item) => sum + item.quantity,
    0
  );

  const hasStockProblems = items.some(
    (item) =>
      item.quantity > item.availableQuantity
  );

  const canCheckout =
    items.length > 0 &&
    !hasStockProblems &&
    Number.isFinite(subtotal) &&
    pendingSku === null;

  return (
    <section
      className="fs-section"
      aria-busy={pendingSku !== null}
    >
      <div className="fs-section-heading">
        <div>
          <span className="fs-kicker">
            Tu cuenta
          </span>

          <h2>Carrito de compras</h2>

          <p>
            {totalUnits}{' '}
            {totalUnits === 1
              ? 'unidad'
              : 'unidades'}{' '}
            en tu carrito.
          </p>
        </div>

        <div>
          <span>Subtotal estimado</span>

          <strong>
            {formatMoney(subtotal)}
          </strong>
        </div>
      </div>

      {/* Mensajes de operación */}
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

      {/* Carrito vacío */}
      {items.length === 0 ? (
        <div className="fs-empty">
          <h3>Tu carrito está vacío</h3>

          <p>
            Explora nuestros productos y agrega
            lo que necesites.
          </p>

          <Link
            className="fs-button"
            href="/shop"
          >
            Ir a la tienda
          </Link>
        </div>
      ) : (
        <>
          <div className="fs-table-wrap">
            <table className="fs-table">
              <caption>
                Productos en tu carrito
              </caption>

              <thead>
                <tr>
                  <th scope="col">Producto</th>
                  <th scope="col">SKU</th>
                  <th scope="col">Precio</th>
                  <th scope="col">Cantidad</th>
                  <th scope="col">Total</th>
                  <th scope="col">Acciones</th>
                </tr>
              </thead>

              <tbody>
                {items.map((item) => (
                  <CartRow
                    key={`${item.sku}:${item.quantity}`}
                    item={item}
                    busy={pendingSku !== null}
                    onUpdate={async (sku, quantity) => {
                      await mutateCart(sku, quantity);
                    }}
                    onRemove={async (sku) => {
                      await mutateCart(sku, null);
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>

          {/* Problemas de disponibilidad */}
          {hasStockProblems && (
            <p className="fs-status" role="alert">
              Algunos productos no tienen stock
              suficiente. Actualiza las cantidades
              o elimina esos productos antes de
              continuar.
            </p>
          )}

          {/* Acciones */}
          <div className="fs-actions fs-actions-end">
            <button
              className="fs-button fs-button-secondary"
              type="button"
              disabled={pendingSku !== null}
              onClick={reloadCart}
            >
              Actualizar carrito
            </button>

            <Link
              className="fs-button fs-button-secondary"
              href="/shop"
            >
              Seguir comprando
            </Link>

            {canCheckout ? (
              <Link
                className="fs-button"
                href="/checkout"
              >
                Continuar al checkout
              </Link>
            ) : (
              <button
                className="fs-button"
                type="button"
                disabled
              >
                Continuar al checkout
              </button>
            )}
          </div>
        </>
      )}
    </section>
  );
}

// ============================================
// COMPONENTE PRINCIPAL
// ============================================

export default function CartClient() {
  const {
    session,
    loading,
    error,
    refresh,
  } = useSession();

  if (loading) {
    return (
      <p className="fs-status" role="status">
        Verificando sesión…
      </p>
    );
  }

  if (error) {
    return (
      <section className="fs-empty">
        <h2>No se pudo verificar tu sesión</h2>

        <p className="fs-status" role="alert">
          {error}
        </p>

        <button
          className="fs-button fs-button-secondary"
          type="button"
          onClick={() => void refresh()}
        >
          Reintentar
        </button>
      </section>
    );
  }

  if (!session?.authenticated) {
    return (
      <section className="fs-empty">
        <h2>
          Inicia sesión para usar tu carrito
        </h2>

        <p>
          Tu carrito está asociado a tu cuenta
          y se gestiona desde el backend.
        </p>

        <Link
          className="fs-button"
          href="/login"
        >
          Iniciar sesión
        </Link>
      </section>
    );
  }

  return (
    <AuthenticatedCart
      key={session.email ?? 'authenticated'}
      refreshSession={refresh}
    />
  );
}
