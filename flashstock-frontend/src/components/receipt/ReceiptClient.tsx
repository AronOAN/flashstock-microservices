
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';

import { apiRequest } from '@/lib/api-client';
import type { Receipt } from '@/types/domain';

// Formateador reutilizable para pesos chilenos.
const CLP_FORMATTER = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

function formatMoney(
  value: number | null | undefined
): string {
  const amount =
    typeof value === 'number' && Number.isFinite(value)
      ? value
      : 0;

  return CLP_FORMATTER.format(amount);
}

// Estados posibles de la consulta.
type ReceiptState =
  | { status: 'loading' }
  | { status: 'success'; data: Receipt }
  | { status: 'error'; message: string };

type ReceiptDetailsProps = {
  orderNumbers: string;
};

function getErrorMessage(error: unknown): string {
  if (
    error instanceof Error &&
    error.message.trim().length > 0
  ) {
    return error.message;
  }

  return 'No se pudo generar la boleta';
}

function ReceiptDetails({
  orderNumbers,
}: ReceiptDetailsProps) {
  const [state, setState] = useState<ReceiptState>({
    status: 'loading',
  });

  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;

    async function loadReceipt(): Promise<void> {
      try {
        const query = new URLSearchParams({
          orderNumbers,
        });

        const data = await apiRequest<Receipt>(
          `/api/receipts/from-orders?${query.toString()}`
        );

        if (!active) return;

        setState({
          status: 'success',
          data,
        });
      } catch (error: unknown) {
        if (!active) return;

        setState({
          status: 'error',
          message: getErrorMessage(error),
        });
      }
    }

    void loadReceipt();

    return () => {
      active = false;
    };
  }, [orderNumbers, attempt]);

  function retry(): void {
    setState({ status: 'loading' });
    setAttempt((previous) => previous + 1);
  }

  // Estado de carga.
  if (state.status === 'loading') {
    return (
      <section className="fs-section">
        <p className="fs-status" role="status">
          Cargando boleta…
        </p>
      </section>
    );
  }

  // Estado de error.
  if (state.status === 'error') {
    return (
      <section className="fs-section">
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
      </section>
    );
  }

  const receipt = state.data;

  const customerName = [
    receipt.customerFirstName,
    receipt.customerLastName,
  ]
    .filter(Boolean)
    .join(' ') || 'No disponible';

  const items = receipt.items ?? [];

  return (
    <section className="fs-section">
      <div className="fs-section-heading">
        <div>
          <span className="fs-kicker">
            Boleta
          </span>

          <h2>{receipt.receiptNumber}</h2>

          {receipt.createdAt && (
            <p>{receipt.createdAt}</p>
          )}
        </div>

        <button
          className="fs-button fs-button-secondary"
          type="button"
          onClick={() => window.print()}
        >
          Imprimir / guardar PDF
        </button>
      </div>

      {/* Información del cliente */}
      <div className="fs-order-card">
        <p>
          <strong>Cliente:</strong> {customerName}
        </p>

        <p>
          <strong>Correo:</strong>{' '}
          {receipt.customerEmail || 'No disponible'}
        </p>

        <p>
          <strong>Dirección:</strong>{' '}
          {receipt.shippingAddress || '-'}
        </p>
      </div>

      {/* Productos */}
      <div className="fs-table-wrap">
        <table className="fs-table">
          <thead>
            <tr>
              <th scope="col">Producto</th>
              <th scope="col">SKU</th>
              <th scope="col">Cantidad</th>
              <th scope="col">Precio</th>
              <th scope="col">Total</th>
            </tr>
          </thead>

          <tbody>
            {items.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  No hay productos asociados a la boleta.
                </td>
              </tr>
            ) : (
              items.map((item, index) => (
                <tr
                  key={`${item.sku || 'item'}-${index}`}
                >
                  <td>{item.productName || '-'}</td>
                  <td>{item.sku || '-'}</td>
                  <td>{item.quantity ?? 0}</td>
                  <td>{formatMoney(item.unitPrice)}</td>
                  <td>{formatMoney(item.lineTotal)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Totales calculados por backend */}
      <div className="fs-receipt-total">
        <span>
          Subtotal: {formatMoney(receipt.subtotal)}
        </span>

        <span>
          Envío: {formatMoney(receipt.shipping)}
        </span>

        <span>
          Descuento: -{formatMoney(receipt.discount)}
        </span>

        <strong>
          Total: {formatMoney(receipt.total)}
        </strong>
      </div>
    </section>
  );
}

export default function ReceiptClient() {
  const params = useSearchParams();

  if (params === null) {
    return (
      <p className="fs-status" role="status">
        Cargando boleta…
      </p>
    );
  }

  const orderNumbers =
    params.get('orderNumbers')?.trim() ?? '';

  if (!orderNumbers) {
    return (
      <p className="fs-status" role="alert">
        Faltan números de pedido.
      </p>
    );
  }

  return (
    <ReceiptDetails
      key={orderNumbers}
      orderNumbers={orderNumbers}
    />
  );
}
