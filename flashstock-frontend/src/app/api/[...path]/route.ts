import { NextRequest, NextResponse } from 'next/server';
import { getSession, siteOrigin } from '@/lib/flashstock-session';

export const runtime='nodejs';
export const dynamic='force-dynamic';
const allowed = new Set(['auth','admin','inventory','orders','receipts','shipping','maps','cart','payments','coupons']);
const json = (status:number,message:string,data:unknown=null)=>NextResponse.json({message,data},{status,headers:{'Cache-Control':'no-store'}});
const anonymous = ()=>json(200,'Sesion anonima',{
  authenticated:false,admin:false,email:null,displayName:'Invitado',authorities:[],scopes:[],permissions:[]
});

type Context={params:Promise<{path:string[]}>};
async function proxy(req:NextRequest,ctx:Context):Promise<NextResponse> {
  const {path:segments}=await ctx.params;
  if (!segments || segments.length<1 || segments.some(s=>!s || s==='.' || s==='..' || /[\\/\x00-\x1f]/.test(s)) || !allowed.has(segments[0])) {
    return json(404,'Ruta no disponible');
  }
  if (!['GET','HEAD','OPTIONS'].includes(req.method)) {
    const origin = req.headers.get('origin');
    if (!origin || origin !== siteOrigin()) return json(403,'Origen no autorizado');
  }
  const path=`/api/${segments.map(encodeURIComponent).join('/')}`;
  const session=await getSession();
  if (path==='/api/auth/providers' && req.method==='GET') return json(200,'Proveedores',{
    google:false,microsoft:false,cognito:!!process.env.COGNITO_DOMAIN
  });
  if (path==='/api/auth/me' && req.method==='GET' && !session) return anonymous();
  const base=process.env.FLASHSTOCK_API_BASE_URL;
  if (!base || !/^https:\/\/[a-z0-9.-]+\/?$/i.test(base)) return json(503,'API AWS sin configurar');
  // Login session always goes through a JWT-protected route in API Gateway.
  const upstreamPath = path;
  const target=new URL(upstreamPath+req.nextUrl.search,base.endsWith('/')?base:`${base}/`);
  const headers=new Headers();
  if (session) headers.set('Authorization',`Bearer ${session.accessToken}`);
  const type=req.headers.get('content-type');
  if (type) headers.set('Content-Type',type);
  const accept=req.headers.get('accept');
  if (accept) headers.set('Accept',accept);
  const method=req.method;
  const length=Number(req.headers.get('content-length')||'0');
  if (length>1024*1024) return json(413,'Solicitud demasiado grande');
  try {
    const body=method==='GET'||method==='HEAD'?undefined:await req.arrayBuffer();
    if (body && body.byteLength>1024*1024) return json(413,'Solicitud demasiado grande');
    const response=await fetch(target,{method,headers,body,redirect:'manual',cache:'no-store',signal:AbortSignal.timeout(12000)});
    if (path === '/api/inventory' && response.status === 404) {
      return json(503,'Inventario pendiente: falta conectar la ruta de API Gateway a su backend');
    }
    const ct=response.headers.get('content-type')||'application/json';
    const bytes=await response.arrayBuffer();
    if (bytes.byteLength>4*1024*1024) return json(502,'Respuesta excede tamaño permitido');
    if (path==='/api/auth/me' && method==='GET') {
      if (response.status===401 || response.status===403) return anonymous();
      if (!response.ok) return json(503,'Endpoint de permisos no disponible');
      const payload=JSON.parse(Buffer.from(bytes).toString('utf8')) as {message:string,data:Record<string,unknown>};
      if (!payload || !payload.data || typeof payload.data !== 'object') return json(502,'Respuesta de permisos inválida');
      return json(200,payload.message,{...payload.data,email:session?.email??null,
        displayName:session?.name||payload.data.displayName});
    }
    const outgoing=new Headers({'Content-Type':ct,'Cache-Control':'no-store'});
    // Never forward upstream Set-Cookie, Location, or arbitrary cache headers to browser.
    return new NextResponse(bytes,{status:response.status,headers:outgoing});
  } catch {
    return json(502,'Gateway o backend no disponible');
  }
}
export const GET=proxy;
export const POST=proxy;
export const PUT=proxy;
export const PATCH=proxy;
export const DELETE=proxy;
