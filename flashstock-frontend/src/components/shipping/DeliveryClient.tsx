
'use client';

import { useState } from 'react';
import { apiRequest } from '@/lib/api-client';
import type { ShipmentTracking } from '@/types/domain';

export default function DeliveryClient() {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [tracking, setTracking] = useState<ShipmentTracking | null>(null);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit() {
    const number = trackingNumber.trim();

    if (!number || loading) return;

    setLoading(true);
    setStatus('');
    setTracking(null);

    try {
      const result = await apiRequest<ShipmentTracking>(
        `/api/shipping/tracking/${encodeURIComponent(number)}`
      );

      setTracking(result);
    } catch (error: unknown) {
      setStatus(
        error instanceof Error
          ? error.message
          : 'Tracking no disponible'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="fs-section fs-narrow">
      <span className="fs-kicker">Delivery</span>

      <h2>Seguimiento de envío</h2>

      <form
        className="fs-inline-form"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <input
          className="fs-input"
          type="text"
          required
          value={trackingNumber}
          onChange={(event) => setTrackingNumber(event.target.value)}
          placeholder="Número de tracking"
          aria-label="Número de tracking"
        />

        <button
          className="fs-button"
          type="submit"
          disabled={loading}
        >
          {loading ? 'Consultando...' : 'Consultar'}
        </button>
      </form>

      {status && (
        <p className="fs-status" role="alert">
          {status}
        </p>
      )}

      {tracking && (
        <article className="fs-order-card">
          <h3>{tracking.trackingNumber}</h3>
          <p>Pedido: {tracking.orderNumber}</p>
          <p>Estado pedido: {tracking.orderStatus}</p>
          <p>Estado envío: {tracking.shipmentStatus}</p>
          <p>
            Repartidor: {tracking.courierName || 'Por asignar'}
          </p>
          <p>
            Progreso: {tracking.progressPercent ?? 0}%
          </p>
          <p>
            ETA: {tracking.remainingDurationText || 'No disponible'}
          </p>
        </article>
      )}
    </section>
  );
}
