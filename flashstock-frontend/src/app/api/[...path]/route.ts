import { NextRequest, NextResponse } from 'next/server';
import { attachSessionCookies, resolveSession } from '@/lib/cognito-password';
import { callOwned } from '@/lib/auth-backend';
import { siteOrigin } from '@/lib/flashstock-session';

export const runtime='nodejs';
export const dynamic='force-dynamic';
const allowed = new Set(['auth','admin','catalog','inventory','orders','receipts','shipping','maps','cart','payments','coupons']);
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
  // Token operations require dedicated server-only BFF handlers, never generic proxying.
  // Only the identity endpoint is intentionally reachable through this generic proxy.
  // The backend-owned browser token operations are NEVER browser-proxyable.
  if (segments[0]==='auth' && !(path==='/api/auth/me' && req.method==='GET')) return json(404,'Ruta no disponible');
  // Keep all order-related APIs unpublished until backend ownership and shipping checks pass.
  if (['orders','receipts','shipping'].includes(segments[0])
      && process.env.FLASHSTOCK_ORDER_ROUTES_ENABLED !== 'true') {
    return json(503,'Pedidos temporalmente no disponibles');
  }
  if (path === '/api/receipts/send-email') return json(404,'Ruta no disponible');
  const resolved=await resolveSession();
  const finish=(response:NextResponse)=>attachSessionCookies(response,resolved);
  if (resolved.unavailable) return finish(json(503,'No se pudo renovar la sesión con Cognito'));
  const session=resolved.session;
  if (process.env.FLASHSTOCK_ISSUED_TOKENS_ENABLED === 'true'
      && !['/api/auth/providers','/api/maps/config','/api/catalog'].includes(path) && session) {
    const allowed = await callOwned('authorize',session.accessToken);
    if (!allowed.ok) return finish(json(allowed.status===403?403:401,'Sesión administrativa no autorizada'));
  }
  if (process.env.FLASHSTOCK_ISSUED_TOKENS_ENABLED === 'true'
      && !['/api/auth/providers','/api/maps/config','/api/catalog'].includes(path) && !session
      && path!=='/api/auth/me') return finish(json(401,'Inicio de sesión administrativo requerido'));
  if (path==='/api/auth/me' && req.method==='GET' && !session) return finish(anonymous());
  const base=process.env.FLASHSTOCK_API_BASE_URL;
  if (!base || !/^https:\/\/[a-z0-9.-]+\/?$/i.test(base)) return finish(json(503,'API AWS sin configurar'));
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
  if (length>1024*1024) return finish(json(413,'Solicitud demasiado grande'));
  try {
    const body=method==='GET'||method==='HEAD'?undefined:await req.arrayBuffer();
    if (body && body.byteLength>1024*1024) return finish(json(413,'Solicitud demasiado grande'));
    const response=await fetch(target,{method,headers,body,redirect:'manual',cache:'no-store',signal:AbortSignal.timeout(12000)});
    if (path === '/api/inventory' && response.status === 404) {
      return finish(json(503,'Inventario pendiente: falta conectar la ruta de API Gateway a su backend'));
    }
    const ct=response.headers.get('content-type')||'application/json';
    const bytes=await response.arrayBuffer();
    if (bytes.byteLength>4*1024*1024) return finish(json(502,'Respuesta excede tamaño permitido'));
    if (path==='/api/auth/me' && method==='GET') {
      // An existing cookie must never be reported as "anonymous" because Auth rejected it.
      if (response.status===401 || response.status===403) return finish(json(502,'El servicio Auth rechazó el Access Token'));
      if (!response.ok) return finish(json(503,'Endpoint de permisos no disponible'));
      const payload=JSON.parse(Buffer.from(bytes).toString('utf8')) as {message:string,data:Record<string,unknown>};
      if (!payload || !payload.data || typeof payload.data !== 'object' || payload.data.authenticated !== true)
        return finish(json(502,'Respuesta de permisos inválida'));
      return finish(json(200,payload.message,{...payload.data,email:session?.email??null,
        displayName:session?.name||payload.data.displayName}));
    }
    const outgoing=new Headers({'Content-Type':ct,'Cache-Control':'no-store'});
    // Never forward upstream Set-Cookie, Location, or arbitrary cache headers to browser.
    return finish(new NextResponse(bytes,{status:response.status,headers:outgoing}));
  } catch {
    return finish(json(502,'Gateway o backend no disponible'));
  }
}
export const GET=proxy;
export const POST=proxy;
export const PUT=proxy;
export const PATCH=proxy;
export const DELETE=proxy;
