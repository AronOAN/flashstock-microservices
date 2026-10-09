
'use client';

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

import type { InventoryItem } from '@/types/domain';

const MAX_QUANTITY = 2_147_483_647;

type InventoryState =
  | { status: 'loading' }
  | { status: 'success'; data: InventoryItem[] }
  | { status: 'error'; message: string };

type Feedback = {
  type: 'success' | 'error';
  message: string;
};

function isInventoryItem(
  value: unknown
): value is InventoryItem {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }

  const item = value as Record<string, unknown>;

  return (
    typeof item.sku === 'string' &&
    item.sku.trim().length > 0 &&
    typeof item.name === 'string' &&
    Number.isInteger(item.stock) &&
    Number.isInteger(item.quantity) &&
    typeof item.stock === 'number' &&
    typeof item.quantity === 'number' &&
    item.stock >= 0 &&
    item.quantity >= 0
  );
}

function getError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400:
      case 422:
        return 'Cantidad inválida o no permitida.';

      case 401:
        return 'Tu sesión ha expirado.';

      case 403:
        return 'No tienes permisos para modificar inventario.';

      case 404:
        return 'El producto ya no existe.';

      case 409:
        return 'El inventario cambió. Actualiza los datos.';

      default:
        return 'No se pudo completar la operación.';
    }
  }

  return 'No se pudo completar la operación.';
}

type InventoryRowProps = {
  item: InventoryItem;
  busy: boolean;
  onSave: (sku: string, quantity: number) => Promise<void>;
};

function InventoryRow({
  item,
  busy,
  onSave,
}: InventoryRowProps) {
  const [draft, setDraft] = useState(String(item.stock));
  const [error, setError] = useState<string | null>(null);

  function submit(): void {
    const value = draft.trim();
    const quantity = Number(value);

    if (
      value.length === 0 ||
      !Number.isSafeInteger(quantity) ||
      quantity < 0 ||
      quantity > MAX_QUANTITY
    ) {
      setError('Ingresa un entero válido mayor o igual a cero.');
      return;
    }

    if (quantity === item.stock) {
      setError(null);
      return;
    }

    setError(null);
    void onSave(item.sku, quantity);
  }

  return (
    <tr>
      <td>{item.sku}</td>
      <td>{item.name}</td>
      <td>{item.quantity}</td>
      <td>{item.stock}</td>

      <td>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <div className="fs-cart-quantity">
            <input
              className="fs-qty"
              type="number"
              name="stock"
              aria-label={`Stock total de ${item.name}`}
              min={0}
              max={MAX_QUANTITY}
              step={1}
              required
              disabled={busy}
              value={draft}
              onChange={(event) => {
                setDraft(event.target.value);
                setError(null);
              }}
            />

            <button
              className="fs-button fs-button-secondary"
              type="submit"
              disabled={busy || draft.trim() === String(item.stock)}
            >
              Guardar
            </button>
          </div>
        </form>

        {error && (
          <p className="fs-status" role="alert">
            {error}
          </p>
        )}
      </td>
    </tr>
  );
}

function InventoryPanel() {
  const [state, setState] = useState<InventoryState>({
    status: 'loading',
  });

  const [reload, setReload] = useState(0);
  const [savingSku, setSavingSku] = useState<string | null>(null);

  const [feedback, setFeedback] = useState<Feedback | null>(
    null
  );

  const savingRef = useRef(false);
  const mountedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();

    async function load(): Promise<void> {
      try {
        const response = await apiRequest<unknown>(
          '/api/inventory',
          { signal: controller.signal }
        );

        if (!active) return;

        if (
          !Array.isArray(response) ||
          !response.every(isInventoryItem)
        ) {
          throw new Error('Respuesta de inventario inválida');
        }

        const skus = new Set(
          response.map((item) => item.sku)
        );

        if (skus.size !== response.length) {
          throw new Error('SKU duplicado');
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

  function reloadInventory(): void {
    if (savingRef.current) return;

    setState({ status: 'loading' });
    setReload((previous) => previous + 1);
  }

  async function updateStock(
    sku: string,
    quantity: number
  ): Promise<void> {
    if (
      savingRef.current ||
      state.status !== 'success'
    ) {
      return;
    }

    const item = state.data.find(
      (value) => value.sku === sku
    );

    if (
      !item ||
      !Number.isSafeInteger(quantity) ||
      quantity < 0 ||
      quantity > MAX_QUANTITY
    ) {
      setFeedback({
        type: 'error',
        message: 'Datos de inventario inválidos.',
      });
      return;
    }

    if (quantity === item.stock) return;

    savingRef.current = true;
    setSavingSku(sku);
    setFeedback(null);

    try {
      await apiMutation(
        `/api/inventory/${encodeURIComponent(sku)}/quantity/${quantity}`,
        'PATCH'
      );

      if (!mountedRef.current) return;

      setFeedback({
        type: 'success',
        message: `Stock del producto ${sku} actualizado.`,
      });
    } catch (error: unknown) {
      if (!mountedRef.current) return;

      setFeedback({
        type: 'error',
        message: getError(error),
      });
    } finally {
      savingRef.current = false;

      if (mountedRef.current) {
        setSavingSku(null);

        // Reconciliar el estado con Inventory
        // incluso después de un fallo HTTP.
        setState({ status: 'loading' });
        setReload((previous) => previous + 1);
      }
    }
  }

  return (
    <section
      className="fs-section"
      aria-busy={
        state.status === 'loading' ||
        savingSku !== null
      }
    >
      <div className="fs-section-heading">
        <div>
          <span className="fs-kicker">Admin</span>
          <h2>Gestión de inventario</h2>
        </div>

        {state.status === 'success' && (
          <button
            className="fs-button fs-button-secondary"
            type="button"
            disabled={savingSku !== null}
            onClick={reloadInventory}
          >
            Actualizar inventario
          </button>
        )}
      </div>

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

      {state.status === 'loading' && (
        <p className="fs-status" role="status">
          Cargando inventario…
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
            onClick={reloadInventory}
          >
            Reintentar
          </button>
        </div>
      )}

      {state.status === 'success' && (
        state.data.length === 0 ? (
          <div className="fs-empty">
            <h3>No hay productos registrados</h3>
          </div>
        ) : (
          <div className="fs-table-wrap">
            <table className="fs-table">
              <caption>
                Administración de existencias
              </caption>

              <thead>
                <tr>
                  <th scope="col">SKU</th>
                  <th scope="col">Producto</th>
                  <th scope="col">Disponible</th>
                  <th scope="col">Stock total</th>
                  <th scope="col">Nuevo stock</th>
                </tr>
              </thead>

              <tbody>
                {state.data.map((item) => (
                  <InventoryRow
                    key={`${item.sku}:${item.stock}`}
                    item={item}
                    busy={savingSku !== null}
                    onSave={updateStock}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </section>
  );
}

export default function AdminInventoryClient() {
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
        <p>No tienes permisos para modificar inventario.</p>
      </section>
    );
  }

  return <InventoryPanel />;
}
