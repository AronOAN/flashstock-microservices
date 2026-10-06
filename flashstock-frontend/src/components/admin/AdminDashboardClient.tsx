'use client';
import { useEffect,useState } from 'react';
import Link from 'next/link';
import { apiRequest } from '@/lib/api-client';
import { useSession } from '@/components/session/SessionProvider';
export default function AdminDashboardClient(){const{session,loading}=useSession();const[metrics,setMetrics]=useState<Record<string,unknown>|null>(null);const[status,setStatus]=useState('');useEffect(()=>{if(!session?.admin)return;void apiRequest<Record<string,unknown>>('/api/admin/metrics').then(setMetrics).catch(e=>setStatus(e instanceof Error?e.message:'Métricas no disponibles'));},[session?.admin]);if(loading)return <p className="fs-status">Verificando permisos…</p>;if(!session?.admin)return <section className="fs-empty"><h2>Acceso ADMIN requerido</h2></section>;return <section className="fs-section"><span className="fs-kicker">Administración</span><h2>Panel FlashStock</h2>{status&&<p className="fs-status">{status}</p>}<div className="fs-feature-grid">{metrics?Object.entries(metrics).slice(0,8).map(([key,value])=><article key={key}><strong>{key}</strong><span>{String(value??'-')}</span></article>):<article><strong>Métricas</strong><span>Cargando…</span></article>}</div><div className="fs-actions"><Link className="fs-button" href="/admin/inventory">Inventario</Link><Link className="fs-button fs-button-secondary" href="/admin/shipments">Envíos</Link></div></section>;
}
