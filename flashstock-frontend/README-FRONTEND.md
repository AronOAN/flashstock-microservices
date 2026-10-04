# Frontend FlashStock

El acceso está en `/login`: el navegador envía correo y contraseña por HTTPS a `POST /auth/password` del propio Next.js. Ese servidor llama a Cognito User Pools `InitiateAuth` y, si corresponde, `RespondToAuthChallenge` (contraseña temporal o MFA). Cognito valida la identidad y emite los tokens; Next.js consulta `GetUser` y guarda Access Token y Refresh Token en cookies cifradas **separadas**, HttpOnly, Secure en producción y SameSite=Lax. El navegador nunca recibe los tokens en una respuesta JSON ni los lee con JavaScript. La contraseña o su hash nunca se incluyen en los JWT.

## Configuración

En Vercel, define como variables de servidor: `FLASHSTOCK_SITE_URL=https://flashstock-microservices.vercel.app`, `FLASHSTOCK_API_BASE_URL` con el origen HTTPS real de tu HTTP API, `COGNITO_ISSUER_URL` y `COGNITO_APP_CLIENT_ID` desde los outputs del mismo estado Terraform, `COGNITO_DOMAIN_URL` desde el output `cognito_domain_url`, y `FLASHSTOCK_SESSION_KEY` (32 bytes aleatorios codificados en base64; por ejemplo `openssl rand -base64 32`). Mantén la clave fuera de Git. Para desarrollo, copia `.env.example` a `.env.local` y ajusta las variables.

Aplica antes el plan Terraform que añade `ALLOW_USER_PASSWORD_AUTH` al cliente y cambia a `aws.cognito.signin.user.admin` el scope de las rutas JWT de Auth e Inventory. Debe existir una ruta AWS para `GET /api/auth/me`; el backend comprueba firma, emisor, `client_id`, `token_use=access` y grupos de Cognito. Una cuenta debe ser creada en el User Pool por el administrador; el pool no admite autorregistro.

El Access Token dura 15 minutos. Cuando se acerca a su vencimiento, el BFF solicita tokens nuevos mediante `GetTokensFromRefreshToken`, comprueba con `GetUser` que el `sub` coincide con el de la sesión y reemplaza la cookie de acceso. El Refresh Token dura **un día**, conforme a `refresh_token_validity = 1` de `cognito.tf`; su renovación no extiende ese límite. Si caduca o es revocado hay que iniciar sesión otra vez. `GET /auth/logout` revoca el Refresh Token en Cognito y elimina las cookies locales. `GET /auth/login` redirige al formulario propio.

La opción «Acceder mediante Cognito» inicia `GET /auth/pkce`. El servidor genera `code_verifier` aleatorio y `state`, cifra ambos en una cookie temporal de cinco minutos y redirige al dominio Cognito con `code_challenge` S256. `GET /auth/callback` exige la cookie vigente y el `state` exacto, intercambia el código en `/oauth2/token` y almacena los tokens en las mismas cookies cifradas del BFF; el `code_verifier` y los tokens nunca se exponen al JavaScript del navegador. El formulario propio continúa usando `USER_PASSWORD_AUTH` sin PKCE; PKCE sólo protege el intercambio de códigos de autorización y no prolonga el Refresh Token.

Antes de usar PKCE, aplica el cambio en Terraform para permitir el scope `aws.cognito.signin.user.admin` en el app client y verifica en Cognito que el dominio exista, el callback HTTPS esté registrado y el app client tenga una página de inicio de sesión habilitada. Si aparece «Login pages unavailable», revisa la versión de branding del dominio y el estilo asignado al cliente en Cognito. No recrees el dominio existente desde Terraform sin importar primero el recurso al estado.

```bash
aws cognito-idp describe-user-pool-domain --domain flashstock-dev-aron \
  --query 'DomainDescription.{Pool:UserPoolId,Status:Status,Version:ManagedLoginVersion}'
aws cognito-idp describe-managed-login-branding-by-client \
  --user-pool-id us-east-1_jkrNUk7yQ --client-id 1v1gmjscc2qtpuerhs63v6taob \
  --query 'ManagedLoginBranding.ManagedLoginBrandingId'
```

Si el segundo comando indica que no existe un estilo, asígnalo al cliente desde Cognito > Managed login. Antes del despliegue, revisa con `terraform plan` que `aws_cognito_user_pool_client.frontend` sólo cambie los scopes esperados y que no se reemplace el pool.

Si la interfaz indica que no puede verificar la sesión justo después de iniciar sesión, comprueba `GET /api/auth/me` en Network: la ruta de AWS debe aceptar el Access Token y el servicio Auth debe responder `data.authenticated=true`. Un `401/403` de Auth ya no se presenta como sesión anónima. Comprueba también la existencia de `__Host-flashstock-session` y `__Host-flashstock-refresh` en las cookies del navegador **sin copiar sus valores**.

Orden, Boletas y Shipping siguen cerrados en el proxy mediante `FLASHSTOCK_ORDER_ROUTES_ENABLED=false`, y Terraform no publica sus rutas en API Gateway. Consulta `CAMBIOS-SEGURIDAD-LOGIN.md` en la raíz antes de habilitar cualquiera de ellas.




## Backend-owned RS256 admin-only
La autoridad de emisión/rotación/revocación está en Auth, `/api/auth/browser/*`. Los JWT crudos no se devuelven a JS; BFF solo reenvía de forma controlada cookies `HttpOnly` del backend. `/auth/logout` requiere POST con Origin del sitio. Activación/limitaciones: `AUTH-ONLY-DEPLOYMENT.md`. Cognito USER_PASSWORD_AUTH y PKCE de entrada siguen siendo transportados por Next, no autorizan roles ni emiten JWT de FlashStock.
