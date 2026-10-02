# Frontend FlashStock

El acceso está en `/login`: el navegador envía correo y contraseña por HTTPS a `POST /auth/password` del propio Next.js. Ese servidor llama a Cognito User Pools `InitiateAuth` y, si corresponde, `RespondToAuthChallenge` (contraseña temporal o MFA). Cognito valida la identidad y emite el Access Token; Next.js consulta `GetUser` y guarda el token únicamente en una cookie cifrada HttpOnly, Secure en producción y SameSite=Lax. El navegador nunca recibe el JWT en la respuesta JSON.

## Configuración

En Vercel, define como variables de servidor: `FLASHSTOCK_SITE_URL=https://flashstock-microservices.vercel.app`, `FLASHSTOCK_API_BASE_URL` con el origen HTTPS real de tu HTTP API, `COGNITO_ISSUER_URL` y `COGNITO_APP_CLIENT_ID` desde los outputs del mismo estado Terraform, y `FLASHSTOCK_SESSION_KEY` (32 bytes aleatorios codificados en base64; por ejemplo `openssl rand -base64 32`). Mantén la clave fuera de Git. `COGNITO_DOMAIN` no es necesario. Para desarrollo, copia `.env.example` a `.env.local` y ajusta las variables.

Aplica antes el plan Terraform que añade `ALLOW_USER_PASSWORD_AUTH` al cliente y cambia a `aws.cognito.signin.user.admin` el scope de las rutas JWT de Auth e Inventory. Debe existir una ruta AWS para `GET /api/auth/me`; el backend comprueba firma, emisor, `client_id`, `token_use=access` y grupos de Cognito. Una cuenta debe ser creada en el User Pool por el administrador; el pool no admite autorregistro.

La sesión caduca con el Access Token (configurado a 15 minutos en `cognito.tf`); hay que iniciar sesión de nuevo. Para salir, `/auth/logout` elimina cookies de sesión y desafío. `GET /auth/login` redirige al formulario propio; `GET /auth/callback` ya no intercambia códigos.

Orden, Boletas y Shipping siguen cerrados en el proxy mediante `FLASHSTOCK_ORDER_ROUTES_ENABLED=false`, y Terraform no publica sus rutas en API Gateway. Consulta `CAMBIOS-SEGURIDAD-LOGIN.md` en la raíz antes de habilitar cualquiera de ellas.
