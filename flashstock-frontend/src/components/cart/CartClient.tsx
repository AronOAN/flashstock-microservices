'use client';
import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { apiMutation, apiRequest } from '@/lib/api-client';
import type { CartItem } from '@/types/domain';
import { useSession } from '@/components/session/SessionProvider';
const money=(value:number)=>new Intl.NumberFormat('es-CL',{style:'currency',currency:'CLP',maximumFractionDigits:0}).format(value);
export default function CartClient() {
  const {session,loading:sessionLoading}=useSession();
  const [items,setItems]=useState<CartItem[]>([]);
  const [status,setStatus]=useState('');

  const load=useCallback(async()=>{
    if(!session?.authenticated)return;
    try{
      setItems(await apiRequest<CartItem[]>('/api/cart'));
      setStatus('');
    }catch(e){
      setStatus(e instanceof Error?e.message:'No se pudo cargar el carrito');
    }
  },[session?.authenticated]);

  useEffect(()=>{
    if(!session?.authenticated)return;
    let active=true;
    apiRequest<CartItem[]>('/api/cart').then(data=>{
      if(!active)return;
      setItems(data);
      setStatus('');
    }).catch(e=>{
      if(active)setStatus(e instanceof Error?e.message:'No se pudo cargar el carrito');
    });
    return()=>{active=false;};
  },[session?.authenticated]);

  const total=useMemo(()=>items.reduce((sum,item)=>sum+Number(item.unitPrice)*item.quantity,0),[items]);
  async function setQuantity(sku:string,quantity:number){try{if(quantity<=0)await apiMutation(`/api/cart/items/${encodeURIComponent(sku)}`,'DELETE');else await apiMutation(`/api/cart/items/${encodeURIComponent(sku)}`,'PATCH',{quantity});await load();}catch(e){setStatus(e instanceof Error?e.message:'No se pudo actualizar el carrito');}}
  if(sessionLoading)return <p className="fs-status">Verificando sesión…</p>;
  if(!session?.authenticated)return <section className="fs-empty"><h2>Inicia sesión para usar tu carrito</h2><p>El carrito ya no se usa como identidad ni autorización en localStorage.</p><Link className="fs-button" href="/login">Iniciar sesión</Link></section>;
  return <section className="fs-section"><div className="fs-section-heading"><div><span className="fs-kicker">Tu cuenta</span><h2>Carrito</h2></div><strong>{money(total)}</strong></div>
    {status&&<p className="fs-status">{status}</p>}{items.length===0?<div className="fs-empty"><h3>Tu carrito está vacío</h3><Link className="fs-button" href="/shop">Ir a la tienda</Link></div>:<>
      <div className="fs-table-wrap"><table className="fs-table"><thead><tr><th>Producto</th><th>SKU</th><th>Precio</th><th>Cantidad</th><th>Total</th><th/></tr></thead><tbody>{items.map(item=><tr key={item.sku}><td>{item.productName}</td><td>{item.sku}</td><td>{money(Number(item.unitPrice))}</td><td><input className="fs-qty" type="number" min={1} max={Math.max(1,item.availableQuantity)} value={item.quantity} onChange={e=>void setQuantity(item.sku,Number(e.target.value))}/></td><td>{money(Number(item.unitPrice)*item.quantity)}</td><td><button className="fs-link-button fs-danger" type="button" onClick={()=>void setQuantity(item.sku,0)}>Quitar</button></td></tr>)}</tbody></table></div>
      <div className="fs-actions fs-actions-end"><Link className="fs-button fs-button-secondary" href="/shop">Seguir comprando</Link><Link className="fs-button" href="/checkout">Continuar al checkout</Link></div></>}
  </section>;
}
