'use client';
import { useEffect,useState } from 'react';
import { apiRequest } from '@/lib/api-client';
import { useSession } from '@/components/session/SessionProvider';
export default function AdminShipmentsClient(){const{session,loading}=useSession();const[rows,setRows]=useState<Array<Record<string,unknown>>>([]);const[status,setStatus]=useState('');useEffect(()=>{if(!session?.admin)return;void apiRequest<Array<Record<string,unknown>>>('/api/shipping').then(setRows).catch(e=>setStatus(e instanceof Error?e.message:'Envíos no disponibles'));},[session?.admin]);if(loading)return <p className="fs-status">Verificando permisos…</p>;if(!session?.admin)return <section className="fs-empty"><h2>Acceso ADMIN requerido</h2></section>;return <section className="fs-section"><span className="fs-kicker">Admin</span><h2>Envíos</h2>{status&&<p className="fs-status">{status}</p>}<div className="fs-card-list">{rows.map((row,index)=><article className="fs-order-card" key={String(row.trackingNumber||index)}>{Object.entries(row).slice(0,8).map(([key,value])=><p key={key}><strong>{key}:</strong> {String(value??'-')}</p>)}</article>)}</div></section>;
}
