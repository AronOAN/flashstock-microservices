'use client';
import { useCallback,useEffect,useState } from 'react';
import { apiMutation,apiRequest } from '@/lib/api-client';
import type { OrderHistoryItem } from '@/types/domain';
import { useSession } from '@/components/session/SessionProvider';
export default function OrderStatusClient(){const{session,loading}=useSession();const[orders,setOrders]=useState<OrderHistoryItem[]>([]);const[status,setStatus]=useState('');
  const load=useCallback(async()=>{if(!session?.authenticated)return;try{setOrders(await apiRequest<OrderHistoryItem[]>('/api/orders/my-history'));setStatus('');}catch(e){setStatus(e instanceof Error?e.message:'No se pudieron cargar tus pedidos');}},[session?.authenticated]);
  useEffect(()=>{if(!session?.authenticated)return;let active=true;apiRequest<OrderHistoryItem[]>('/api/orders/my-history').then(data=>{if(!active)return;setOrders(data);setStatus('');}).catch(e=>{if(active)setStatus(e instanceof Error?e.message:'No se pudieron cargar tus pedidos');});return()=>{active=false;};},[session?.authenticated]);
  async function confirmReceived(orderNumber:string){try{await apiMutation(`/api/orders/${encodeURIComponent(orderNumber)}/confirm-received`,'POST');await load();}catch(e){setStatus(e instanceof Error?e.message:'No se pudo confirmar la recepción');}}
  if(loading)return <p className="fs-status">Verificando sesión…</p>;if(!session?.authenticated)return <section className="fs-empty"><h2>Inicia sesión para revisar tus pedidos</h2></section>;
  return <section className="fs-section"><span className="fs-kicker">Pedidos</span><h2>Historial y entrega</h2>{status&&<p className="fs-status">{status}</p>}<div className="fs-card-list">{orders.map(order=><article className="fs-order-card" key={order.orderNumber}><div><strong>{order.orderNumber}</strong><span>{order.orderStatus}</span></div><p>{order.shippingAddress||'Dirección no informada'}</p><p>Tracking: {order.shipmentTrackingNumber||'Pendiente'} · {order.shipmentStatus||'Sin despacho'}</p>{order.orderStatus?.toUpperCase()!=='DELIVERED'&&<button className="fs-button fs-button-secondary" type="button" onClick={()=>void confirmReceived(order.orderNumber)}>Confirmar recibido</button>}</article>)}</div></section>;
}
