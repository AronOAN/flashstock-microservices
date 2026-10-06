'use client';
import { useEffect,useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { apiRequest } from '@/lib/api-client';
import type { Receipt } from '@/types/domain';
const money=(value:number|undefined)=>new Intl.NumberFormat('es-CL',{style:'currency',currency:'CLP',maximumFractionDigits:0}).format(Number(value||0));
export default function ReceiptClient(){const params=useSearchParams();const[receipt,setReceipt]=useState<Receipt|null>(null);const[status,setStatus]=useState('Cargando boleta…');const orderNumbers=params.get('orderNumbers')||'';
  useEffect(()=>{if(!orderNumbers)return;let active=true;apiRequest<Receipt>(`/api/receipts/from-orders?orderNumbers=${encodeURIComponent(orderNumbers)}`).then(data=>{if(!active)return;setReceipt(data);setStatus('');}).catch(e=>{if(active)setStatus(e instanceof Error?e.message:'No se pudo generar la boleta');});return()=>{active=false;};},[orderNumbers]);
  if(!orderNumbers)return <p className="fs-status">Faltan números de pedido.</p>;
  if(!receipt)return <p className="fs-status">{status}</p>;
  return <section className="fs-section"><div className="fs-section-heading"><div><span className="fs-kicker">Boleta</span><h2>{receipt.receiptNumber}</h2><p>{receipt.createdAt||''}</p></div><button className="fs-button fs-button-secondary" type="button" onClick={()=>window.print()}>Imprimir / guardar PDF</button></div>
    <div className="fs-order-card"><p><strong>Cliente:</strong> {[receipt.customerFirstName,receipt.customerLastName].filter(Boolean).join(' ')}</p><p><strong>Correo:</strong> {receipt.customerEmail}</p><p><strong>Dirección:</strong> {receipt.shippingAddress||'-'}</p></div>
    <div className="fs-table-wrap"><table className="fs-table"><thead><tr><th>Producto</th><th>SKU</th><th>Cantidad</th><th>Precio</th><th>Total</th></tr></thead><tbody>{(receipt.items||[]).map((item,index)=><tr key={`${item.sku||'item'}-${index}`}><td>{item.productName||'-'}</td><td>{item.sku||'-'}</td><td>{item.quantity||0}</td><td>{money(item.unitPrice)}</td><td>{money(item.lineTotal)}</td></tr>)}</tbody></table></div>
    <div className="fs-receipt-total"><span>Subtotal {money(receipt.subtotal)}</span><span>Envío {money(receipt.shipping)}</span><span>Descuento -{money(receipt.discount)}</span><strong>Total {money(receipt.total)}</strong></div></section>;
}
