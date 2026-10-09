
'use client';

import { useEffect, useState } from 'react';

import { ApiError, apiRequest } from '@/lib/api-client';
import { useSession } from '@/components/session/SessionProvider';

type Shipment = {
  trackingNumber: string;
  orderNumber?: string | null;
  carrier?: string | null;
  courierName?: string | null;
  status?: string | null;
  eta?: string | null;
};

type ShipmentsState =
  | { status: 'loading' }
  | { status: 'success'; data: Shipment[] }
  | { status: 'error'; message: string };

function isShipment(value: unknown): value is Shipment {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }

  const row = value as Record<string, unknown>;

  const optionalText = [
    'orderNumber',
    'carrier',
    'courierName',
    'status',
    'eta',
  ];

  return (
    typeof row.trackingNumber === 'string' &&
    row.trackingNumber.trim().length > 0 &&
    optionalText.every(
      (key) =>
        row[key] == null ||
        typeof row[key] === 'string'
    )
  );
}

function getError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Tu sesión ha expirado.';
    }

    if (error.status === 403) {
      return 'No tienes permisos para consultar los envíos.';
    }
  }

  return 'No se pudieron cargar los envíos.';
}

function ShipmentsPanel() {
  const [state, setState] = useState<ShipmentsState>({
    status: 'loading',
  });

  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();

    async function load(): Promise<void> {
      try {
        const response = await apiRequest<unknown>(
          '/api/shipping',
          { signal: controller.signal }
        );

        if (!active) return;

        if (
          !Array.isArray(response) ||
          !response.every(isShipment)
        ) {
          throw new Error('Respuesta de Shipping inválida');
        }

        const trackingNumbers = new Set(
          response.map((row) => row.trackingNumber)
        );

        if (trackingNumbers.size !== response.length) {
          throw new Error('Números de tracking duplicados');
        }

        setState({
          status: 'success',
          data: response,
        });
      } catch (error: unknown) {
        if (!active) return;

        setState({
          status: 'error',
          message: getError(error),
        });
      }
    }

    void load();

    return () => {
      active = false;
      controller.abort();
    };
  }, [reload]);

  function retry(): void {
    setState({ status: 'loading' });
    setReload((previous) => previous + 1);
  }

  return (
    <section
      className="fs-section"
      aria-busy={state.status === 'loading'}
    >
      <div className="fs-section-heading">
        <div>
          <span className="fs-kicker">Admin</span>
          <h2>Administración de envíos</h2>
        </div>

        {state.status === 'success' && (
          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={retry}
          >
            Actualizar
          </button>
        )}
      </div>

      {state.status === 'loading' && (
        <p className="fs-status" role="status">
          Cargando envíos…
        </p>
      )}

      {state.status === 'error' && (
        <div>
          <p className="fs-status" role="alert">
            {state.message}
          </p>

          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={retry}
          >
            Reintentar
          </button>
        </div>
      )}

      {state.status === 'success' && (
        state.data.length === 0 ? (
          <div className="fs-empty">
            <h3>No hay envíos registrados</h3>
          </div>
        ) : (
          <div className="fs-card-list">
            {state.data.map((shipment) => (
              <article
                className="fs-order-card"
                key={shipment.trackingNumber}
              >
                <h3>{shipment.trackingNumber}</h3>

                <p>
                  <strong>Pedido:</strong>{' '}
                  {shipment.orderNumber || '-'}
                </p>

                <p>
                  <strong>Estado:</strong>{' '}
                  {shipment.status || 'Sin estado'}
                </p>

                <p>
                  <strong>Transportista:</strong>{' '}
                  {shipment.carrier || 'Por asignar'}
                </p>

                <p>
                  <strong>Repartidor:</strong>{' '}
                  {shipment.courierName || 'Por asignar'}
                </p>

                <p>
                  <strong>ETA:</strong>{' '}
                  {shipment.eta || 'No disponible'}
                </p>
              </article>
            ))}
          </div>
        )
      )}
    </section>
  );
}

export default function AdminShipmentsClient() {
  const { session, loading, error, refresh } = useSession();

  if (loading) {
    return (
      <p className="fs-status" role="status">
        Verificando permisos…
      </p>
    );
  }

  if (error) {
    return (
      <section className="fs-empty">
        <h2>No se pudo verificar la sesión</h2>
        <p className="fs-status" role="alert">
          No fue posible comprobar tus permisos.
        </p>
        <button
          className="fs-button"
          type="button"
          onClick={() => void refresh()}
        >
          Reintentar
        </button>
      </section>
    );
  }

  if (!session?.authenticated || !session.admin) {
    return (
      <section className="fs-empty">
        <h2>Acceso ADMIN requerido</h2>
        <p>No tienes permisos para consultar esta sección.</p>
      </section>
    );
  }

  return <ShipmentsPanel />;
}
