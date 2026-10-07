# Frontend FlashStock

Interfaz Next.js/React. Todas las páginas se prerenderizan; no hay Route Handlers de autenticación ni de negocio.

`src/lib/api-client.ts` es el cliente HTTP: mantiene el Access Token solo en memoria y lo envía al API Gateway como Bearer. Auth en Java maneja login, MFA, PKCE, refresh y logout. Los componentes muestran permisos entregados por el backend, que siempre vuelve a comprobarlos.

Copia `.env.example` a `.env.local`. Para desarrollo usa `FLASHSTOCK_API_BASE_URL=http://localhost:8080`; en Vercel configura el origen HTTPS de tu API Gateway. No agregues secretos de Cognito o JWT al frontend.

```bash
pnpm install --frozen-lockfile
pnpm dev
pnpm lint
pnpm test:security
pnpm build
```

Consulta [la guía de despliegue](../DEPLOYMENT.md) para la migración SQL, variables de Auth/ECS, rutas y comprobaciones. La regla `/api/*` en `next.config.ts` solo reenvía tráfico al Gateway; no renueva, firma ni interpreta tokens.
