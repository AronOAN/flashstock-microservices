# Auth RS256 / Cognito ADMIN-only (versión actual)

La documentación de despliegue y los cuatro endpoints permitidos se encuentran en `../AUTH-ONLY-DEPLOYMENT.md`. El documento anterior que describía `/api/auth/tokens/*` y devolución de JWTs crudos ya no aplica. La firma y la rotación del refresh ocurren en Auth, no en Vercel; solo administradores miembros del grupo Cognito `ADMIN` pueden intercambiar o renovar tokens FlashStock.

## Generación local de pareja RSA (solo un ejemplo offline)

```bash
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out flashstock-private.pem
openssl pkcs8 -topk8 -nocrypt -in flashstock-private.pem -outform DER | openssl base64 -A > flashstock-private.der.b64
openssl pkey -in flashstock-private.pem -pubout -outform DER | openssl base64 -A > flashstock-public.der.b64
```

Almacena cada DER base64 en Secrets Manager distinto y NO subas nunca archivos de claves a GitHub. Mantén el identificador `kid` y las claves estables entre reinicios; rota con procedimientos controlados. Auth valida que ambas claves sean pareja. El `sub` es el identificador Cognito, no el correo. Las credenciales/contraseñas y refresh Cognito NO se incorporan al JWT propio. El public key de FlashStock solo se distribuye a resource servers que explícitamente soporten su emisor/audiencia.

## Contrato

* `POST /api/auth/browser/exchange`: Cognito ADMIN access JWT, BFF secret, Origin; entrega dos cookies HttpOnly, sin JWT JSON.
* `POST /api/auth/browser/authorize`: Cognito ADMIN access JWT y cookie access de FlashStock; valida firmas, issuer, audiencia, `sub` concordante y rol.
* `POST /api/auth/browser/refresh`: Cognito ADMIN access JWT reciente, cookie refresh firmado del mismo `sub`, BFF secret y Origin. DB consume token de refresh de uso único; nuevo par en cookies.
* `POST /api/auth/browser/revoke`: BFF secret, Origin, cookie refresh firmado; revoca familia, limpia cookies.

`auth/src/main/resources/db/flashstock_refresh_sessions.sql` se aplica antes de habilitar el emisor. Se requieren controles de revocación por deshabilitación de cuenta, bloqueo distribuido de refresco, WAF/rate-limiting, auditoría sin JWT y pruebas AWS/Vercel antes de producción.
