'use client';
import { useEffect,useState,type FormEvent } from 'react';
import { apiMutation,apiRequest } from '@/lib/api-client';
import type { CartItem } from '@/types/domain';
import { useSession } from '@/components/session/SessionProvider';
export default function CheckoutClient(){const{session,loading}=useSession();const[items,setItems]=useState<CartItem[]>([]);const[firstName,setFirstName]=useState('');const[lastName,setLastName]=useState('');const[address,setAddress]=useState('');const[status,setStatus]=useState('');
  useEffect(()=>{if(!session?.authenticated)return;void apiRequest<CartItem[]>('/api/cart').then(setItems).catch(e=>setStatus(e instanceof Error?e.message:'No se pudo cargar el carrito'));},[session?.authenticated]);
  async function submit(event:FormEvent){event.preventDefault();if(!session?.authenticated||items.length===0)return;setStatus('Creando pedido…');try{const created:Array<{orderNumber:string}>=[];for(const item of items){created.push(await apiMutation<{orderNumber:string}>('/api/orders','POST',{inventoryId:item.inventoryId,sku:item.sku,quantity:item.quantity,customerFirstName:firstName,customerLastName:lastName,customerEmail:session.email,shippingAddress:address}));}await apiMutation('/api/cart','DELETE');window.location.assign(`/boleta?orderNumbers=${encodeURIComponent(created.map(o=>o.orderNumber).join(','))}`);}catch(e){setStatus(e instanceof Error?e.message:'No se pudo crear el pedido');}}
  if(loading)return <p className="fs-status">Verificando sesión…</p>;if(!session?.authenticated)return <section className="fs-empty"><h2>Necesitas iniciar sesión</h2></section>;
  return <section className="fs-section fs-narrow"><span className="fs-kicker">Checkout</span><h2>Datos de entrega</h2><p>{items.length} producto(s) en tu carrito.</p><form className="fs-form" onSubmit={submit}>
    <label>Nombre<input className="fs-input" required value={firstName} onChange={e=>setFirstName(e.target.value)}/></label><label>Apellido<input className="fs-input" required value={lastName} onChange={e=>setLastName(e.target.value)}/></label>
    <label>Dirección de entrega<input className="fs-input" required value={address} onChange={e=>setAddress(e.target.value)}/></label><label>Correo verificado<input className="fs-input" value={session.email||''} readOnly/></label>{status&&<p className="fs-status">{status}</p>}<button className="fs-button" type="submit" disabled={items.length===0}>Crear pedido</button></form></section>;
}
