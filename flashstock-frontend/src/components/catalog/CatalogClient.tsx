'use client';
import Image from 'next/image';
import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ApiError, apiMutation, apiRequest } from '@/lib/api-client';
import type { CatalogItem } from '@/types/domain';
import { useSession } from '@/components/session/SessionProvider';

function money(value: number | null | undefined): string {
  return new Intl.NumberFormat('es-CL', {style:'currency', currency:'CLP', maximumFractionDigits:0}).format(Number(value || 0));
}

function safeImageSource(value?: string | null): string {
  return value?.startsWith('/') ? value : '/static/img/vegetable-item-1.jpg';
}

export default function CatalogClient({ compact=false }: { compact?: boolean }) {
  const { session } = useSession();
  const [items, setItems] = useState<CatalogItem[]>([]);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('Cargando productos…');

  useEffect(() => {
    let active = true;
    apiRequest<CatalogItem[]>('/api/catalog').then(data => {
      if (!active) return;
      setItems(Array.isArray(data) ? data : []);
      setStatus('');
    }).catch(error => {
      if (active) setStatus(error instanceof Error ? error.message : 'Inventario no disponible');
    });
    return () => { active = false; };
  }, []);

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    const filtered = normalized ? items.filter(item => [item.name,item.sku,item.category,item.description]
      .filter(Boolean).some(value => String(value).toLowerCase().includes(normalized))) : items;
    return compact ? filtered.slice(0,8) : filtered;
  }, [items, query, compact]);

  async function addToCart(sku: string) {
    if (!session?.authenticated) { window.location.assign('/login'); return; }
    try {
      await apiMutation(`/api/cart/items/${encodeURIComponent(sku)}`, 'POST', {quantity:1});
      setStatus('Producto agregado al carrito.');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) { window.location.assign('/login'); return; }
      setStatus(error instanceof Error ? error.message : 'No se pudo actualizar el carrito');
    }
  }

  return <section className="fs-section">
    <div className="fs-section-heading"><div><span className="fs-kicker">Catálogo</span><h2>{compact?'Productos destacados':'Tienda FlashStock'}</h2></div>
      {!compact && <input className="fs-input fs-search" type="search" value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar por producto, SKU o categoría"/>}
    </div>
    {status && <p className="fs-status">{status}</p>}
    <div className="fs-product-grid">{visible.map(item => <article className="fs-product-card" key={item.sku}>
      <Image src={safeImageSource(item.imageUrl)} alt={item.name} width={640} height={480}/>
      <div className="fs-product-card-body"><span className="fs-chip">{item.category || 'Producto'}</span><h3>{item.name}</h3>
        <p>{item.description || `SKU ${item.sku}`}</p><div className="fs-product-meta"><strong>{money(item.unitPrice)}</strong><span>{item.availableQuantity} disponibles</span></div>
        <button className="fs-button" type="button" disabled={item.availableQuantity<=0} onClick={()=>void addToCart(item.sku)}>{item.availableQuantity>0?'Agregar al carrito':'Sin stock'}</button>
      </div></article>)}</div>
    {compact && <div className="fs-center"><Link className="fs-button fs-button-secondary" href="/shop">Ver tienda completa</Link></div>}
  </section>;
}
