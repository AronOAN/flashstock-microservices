
'use client';

import {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  apiMutation,
  apiRequest,
} from '@/lib/api-client';

import { useSession } from '@/components/session/SessionProvider';

import type { OrderHistoryItem } from '@/types/domain';

// Estados de carga de pedidos.
type OrdersState =
  | { status: 'loading' }
  | { status: 'success'; data: OrderHistoryItem[] }
  | { status: 'error'; message: string };

// Normalización de errores.
function getErrorMessage(
  error: unknown,
  fallback: string
): string {
  if (
    error instanceof Error &&
    error.message.trim().length > 0
  ) {
    return error.message;
  }

  return fallback;
}

// Componente exclusivo para usuarios autenticados.
function AuthenticatedOrderHistory() {
  const [ordersState, setOrdersState] = useState<OrdersState>({
    status: 'loading',
  });

  const [reloadVersion, setReloadVersion] = useState(0);

  const [confirmingOrder, setConfirmingOrder] = useState<
    string | null
  >(null);

  const [actionError, setActionError] = useState<
    string | null
  >(null);

  const [successMessage, setSuccessMessage] = useState<
    string | null
  >(null);

  // Evita confirmar múltiples pedidos simultáneamente.
  const confirmingRef = useRef(false);

  // Evita actualizaciones después del desmontaje.
  const mountedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  // Carga inicial y recargas posteriores.
  useEffect(() => {
    let active = true;

    async function loadOrders(): Promise<void> {
      try {
        const data = await apiRequest<OrderHistoryItem[]>(
          '/api/orders/my-history'
        );

        if (!active) return;

        if (!Array.isArray(data)) {
          throw new Error(
            'La respuesta del historial de pedidos no es válida'
          );
        }

        setOrdersState({
          status: 'success',
          data,
        });
      } catch (error: unknown) {
        if (!active) return;

        setOrdersState({
          status: 'error',
          message: getErrorMessage(
            error,
            'No se pudieron cargar tus pedidos'
          ),
        });
      }
    }

    void loadOrders();

    return () => {
      active = false;
    };
  }, [reloadVersion]);

  // Recarga sin duplicar la lógica HTTP.
  function reloadOrders(): void {
    setActionError(null);
    setOrdersState({ status: 'loading' });

    setReloadVersion((previous) => previous + 1);
  }

  // Confirma recepción mediante el backend.
  async function confirmReceived(
    orderNumber: string
  ): Promise<void> {
    const normalizedNumber = orderNumber.trim();

    if (!normalizedNumber || confirmingRef.current) {
      return;
    }

    confirmingRef.current = true;

    setConfirmingOrder(normalizedNumber);
    setActionError(null);
    setSuccessMessage(null);

    try {
      await apiMutation(
        `/api/orders/${encodeURIComponent(normalizedNumber)}/confirm-received`,
        'POST'
      );

      if (!mountedRef.current) return;

      setSuccessMessage(
        `Recepción del pedido ${normalizedNumber} confirmada.`
      );

      reloadOrders();
    } catch (error: unknown) {
      if (!mountedRef.current) return;

      setActionError(
        getErrorMessage(
          error,
          'No se pudo confirmar la recepción'
        )
      );
    } finally {
      confirmingRef.current = false;

      if (mountedRef.current) {
        setConfirmingOrder(null);
      }
    }
  }

  return (
    <section
      className="fs-section"
      aria-busy={ordersState.status === 'loading'}
    >
      <span className="fs-kicker">Pedidos</span>

      <h2>Historial y entrega</h2>

      {/* Confirmación exitosa */}
      {successMessage && (
        <p className="fs-status" role="status">
          {successMessage}
        </p>
      )}

      {/* Error al confirmar */}
      {actionError && (
        <p className="fs-status" role="alert">
          {actionError}
        </p>
      )}

      {/* Cargando historial */}
      {ordersState.status === 'loading' && (
        <p className="fs-status" role="status">
          Cargando tus pedidos…
        </p>
      )}

      {/* Error al obtener historial */}
      {ordersState.status === 'error' && (
        <div>
          <p className="fs-status" role="alert">
            {ordersState.message}
          </p>

          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={reloadOrders}
          >
            Reintentar
          </button>
        </div>
      )}

      {/* Historial cargado */}
      {ordersState.status === 'success' && (
        <>
          {ordersState.data.length === 0 ? (
            <div className="fs-empty">
              <h3>No tienes pedidos todavía</h3>
              <p>
                Tus pedidos aparecerán aquí cuando
                realices una compra.
              </p>
            </div>
          ) : (
            <>
              <button
                className="fs-button fs-button-secondary"
                type="button"
                onClick={reloadOrders}
                disabled={confirmingOrder !== null}
              >
                Actualizar pedidos
              </button>

              <div className="fs-card-list">
                {ordersState.data.map((order) => {
                  const isDelivered =
                    order.orderStatus
                      ?.trim()
                      .toUpperCase() === 'DELIVERED';

                  const isConfirming =
                    confirmingOrder === order.orderNumber;

                  return (
                    <article
                      className="fs-order-card"
                      key={order.orderNumber}
                    >
                      <div>
                        <strong>
                          {order.orderNumber}
                        </strong>

                        <span>
                          {order.orderStatus ||
                            'Sin estado'}
                        </span>
                      </div>

                      <p>
                        {order.shippingAddress ||
                          'Dirección no informada'}
                      </p>

                      <p>
                        Tracking:{' '}
                        {order.shipmentTrackingNumber ||
                          'Pendiente'}
                        {' · '}
                        {order.shipmentStatus ||
                          'Sin despacho'}
                      </p>

                      {!isDelivered && (
                        <button
                          className="fs-button fs-button-secondary"
                          type="button"
                          disabled={confirmingOrder !== null}
                          onClick={() => {
                            void confirmReceived(
                              order.orderNumber
                            );
                          }}
                        >
                          {isConfirming
                            ? 'Confirmando…'
                            : 'Confirmar recibido'}
                        </button>
                      )}
                    </article>
                  );
                })}
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}

// Componente principal con control de sesión.
export default function OrderStatusClient() {
  const {
    session,
    loading,
    error: sessionError,
  } = useSession();

  if (loading) {
    return (
      <p className="fs-status" role="status">
        Verificando sesión…
      </p>
    );
  }

  if (sessionError) {
    return (
      <section className="fs-empty">
        <h2>No se pudo verificar tu sesión</h2>
        <p className="fs-status" role="alert">
          {sessionError}
        </p>
      </section>
    );
  }

  if (!session?.authenticated) {
    return (
      <section className="fs-empty">
        <h2>
          Inicia sesión para revisar tus pedidos
        </h2>
      </section>
    );
  }

  return (
    <AuthenticatedOrderHistory
      key={session.email ?? 'authenticated'}
    />
  );
}
