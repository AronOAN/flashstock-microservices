'use client';
import { useState,type FormEvent } from 'react';
import { apiRequest } from '@/lib/api-client';
import type { ShipmentTracking } from '@/types/domain';
export default function DeliveryClient(){const[trackingNumber,setTrackingNumber]=useState('');const[tracking,setTracking]=useState<ShipmentTracking|null>(null);const[status,setStatus]=useState('');
  async function submit(event:FormEvent){event.preventDefault();try{setTracking(await apiRequest<ShipmentTracking>(`/api/shipping/tracking/${encodeURIComponent(trackingNumber.trim())}`));setStatus('');}catch(e){setTracking(null);setStatus(e instanceof Error?e.message:'Tracking no disponible');}}
  return <section className="fs-section fs-narrow"><span className="fs-kicker">Delivery</span><h2>Seguimiento de envío</h2><form className="fs-inline-form" onSubmit={submit}><input className="fs-input" required value={trackingNumber} onChange={e=>setTrackingNumber(e.target.value)} placeholder="Número de tracking"/><button className="fs-button" type="submit">Consultar</button></form>{status&&<p className="fs-status">{status}</p>}{tracking&&<article className="fs-order-card"><h3>{tracking.trackingNumber}</h3><p>Pedido: {tracking.orderNumber}</p><p>Estado pedido: {tracking.orderStatus}</p><p>Estado envío: {tracking.shipmentStatus}</p><p>Repartidor: {tracking.courierName||'Por asignar'}</p><p>Progreso: {tracking.progressPercent??0}%</p><p>ETA: {tracking.remainingDurationText||'No disponible'}</p></article>}</section>;
}
