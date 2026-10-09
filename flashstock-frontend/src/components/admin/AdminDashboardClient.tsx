
'use client';

import Link from 'next/link';

import { useEffect, useState } from 'react';

import { ApiError, apiRequest } from '@/lib/api-client';
import { useSession } from '@/components/session/SessionProvider';

// Campos oficiales del DTO AdminMetricsResponse.
const METRIC_FIELDS = [
  { key: 'inventorySkuCount', label: 'Productos registrados' },
  { key: 'totalStock', label: 'Unidades en inventario' },
  { key: 'lowRiskSkuCount', label: 'Productos con stock bajo' },
  { key: 'criticalRiskSkuCount', label: 'Productos críticos' },
  { key: 'totalOrders', label: 'Total de pedidos' },
  { key: 'createdOrders', label: 'Pedidos creados' },
  { key: 'completedOrders', label: 'Pedidos completados' },
  { key: 'cancelledOrders', label: 'Pedidos cancelados' },
  { key: 'totalShipments', label: 'Total de envíos' },
  { key: 'preparingShipments', label: 'En preparación' },
  { key: 'inTransitShipments', label: 'En tránsito' },
  { key: 'deliveredShipments', label: 'Entregados' },
] as const;

type MetricKey = (typeof METRIC_FIELDS)[number]['key'];

type AdminMetrics = Partial<
  Record<MetricKey, number | null>
> & {
  timestamp?: string | null;
};

type DashboardState =
  | { status: 'loading' }
  | { status: 'success'; data: AdminMetrics }
  | { status: 'error'; message: string };

const NUMBER_FORMATTER = new Intl.NumberFormat('es-CL');

function isAdminMetrics(
  value: unknown
): value is AdminMetrics {
  if (
    !value ||
    typeof value !== 'object' ||
    Array.isArray(value)
  ) {
    return false;
  }

  const record = value as Record<string, unknown>;

  const validNumbers = METRIC_FIELDS.every(({ key }) => {
    const metric = record[key];

    return (
      metric == null ||
      (
        typeof metric === 'number' &&
        Number.isFinite(metric) &&
        Number.isInteger(metric) &&
        metric >= 0
      )
    );
  });

  const hasMetrics = METRIC_FIELDS.some(
    ({ key }) => typeof record[key] === 'number'
  );

  const validTimestamp =
    record.timestamp == null ||
    typeof record.timestamp === 'string';

  return validNumbers && hasMetrics && validTimestamp;
}

function getError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Tu sesión ha expirado.';
    }

    if (error.status === 403) {
      return 'No tienes permisos para consultar las métricas.';
    }

    if (error.status === 429) {
      return 'Demasiadas solicitudes. Intenta más tarde.';
    }
  }

  return 'No se pudieron obtener las métricas.';
}

function DashboardPanel() {
  const [state, setState] = useState<DashboardState>({
    status: 'loading',
  });

  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();

    async function loadMetrics(): Promise<void> {
      try {
        const response = await apiRequest<unknown>(
          '/api/admin/metrics',
          { signal: controller.signal }
        );

        if (!active) return;

        if (!isAdminMetrics(response)) {
          throw new Error('Contrato de métricas inválido');
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

    void loadMetrics();

    return () => {
      active = false;
      controller.abort();
    };
  }, [reload]);

  function refreshMetrics(): void {
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
          <span className="fs-kicker">
            Administración
          </span>

          <h2>Panel FlashStock</h2>

          <p>
            Resumen de inventario, pedidos y envíos.
          </p>
        </div>

        {state.status === 'success' && (
          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={refreshMetrics}
          >
            Actualizar métricas
          </button>
        )}
      </div>

      {/* Cargando */}
      {state.status === 'loading' && (
        <p className="fs-status" role="status">
          Cargando métricas…
        </p>
      )}

      {/* Error */}
      {state.status === 'error' && (
        <div>
          <p className="fs-status" role="alert">
            {state.message}
          </p>

          <button
            className="fs-button fs-button-secondary"
            type="button"
            onClick={refreshMetrics}
          >
            Reintentar
          </button>
        </div>
      )}

      {/* Métricas */}
      {state.status === 'success' && (
        <>
          <div className="fs-feature-grid">
            {METRIC_FIELDS.map(({ key, label }) => {
              const value = state.data[key];

              return (
                <article key={key}>
                  <strong>{label}</strong>

                  <span>
                    {typeof value === 'number'
                      ? NUMBER_FORMATTER.format(value)
                      : 'No disponible'}
                  </span>
                </article>
              );
            })}
          </div>

          {state.data.timestamp && (
            <p className="fs-status">
              Marca de tiempo del servidor:{' '}
              {state.data.timestamp}
            </p>
          )}
        </>
      )}

      {/* Navegación administrativa */}
      <div className="fs-actions">
        <Link
          className="fs-button"
          href="/admin/inventory"
        >
          Administrar inventario
        </Link>

        <Link
          className="fs-button fs-button-secondary"
          href="/admin/shipments"
        >
          Consultar envíos
        </Link>
      </div>
    </section>
  );
}

export default function AdminDashboardClient() {
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

        <p>
          Esta sección es exclusiva para administradores.
        </p>
      </section>
    );
  }

  return <DashboardPanel />;
}
