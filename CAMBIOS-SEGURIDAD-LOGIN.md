# FlashStock: inicio de sesión y aislamiento de pedidos

Este paquete parte del árbol de `AronOAN/flashstock-microservices` obtenido el 2 de octubre de 2026. Conserva los fuentes y los recursos estáticos; no incluye archivos generados (`__pycache__`, logs diagnósticos duplicados ni ZIP/tar de respaldo). No contiene `.env.local`, estado Terraform, credenciales, `node_modules` ni artefactos de compilación.

## Orden de aplicación

1. Revisa el contenido y descomprime en una copia limpia del repositorio. Conserva tu estado Terraform y las variables privadas fuera de este ZIP.
2. Antes de desplegar el frontend, ejecuta `terraform plan` en `infra/terraform/api-gateway` con tus variables existentes. Confirma que se actualiza el cliente Cognito existente para permitir `ALLOW_USER_PASSWORD_AUTH` y que las rutas JWT de Auth e Inventory exigen `aws.cognito.signin.user.admin`; no aceptes reemplazos de User Pool, API Gateway o bases de datos. Aplica solo un plan revisado. No necesitas el dominio Hosted UI para este formulario.
3. Configura en Vercel `FLASHSTOCK_SITE_URL`, `FLASHSTOCK_API_BASE_URL`, `COGNITO_ISSUER_URL`, `COGNITO_APP_CLIENT_ID` y una clave nueva `FLASHSTOCK_SESSION_KEY` de 32 bytes. `FLASHSTOCK_SITE_URL` de producción debe ser el origen HTTPS exacto, sin ruta: `https://flashstock-microservices.vercel.app`. Mantén `FLASHSTOCK_ORDER_ROUTES_ENABLED=false`. Vuelve a desplegar después de guardar estas variables.
4. Verifica un usuario existente, una contraseña errónea, contraseña temporal y MFA si están habilitados, y un usuario del grupo ADMIN. `GET /api/auth/me` con la sesión debe devolver sus grupos; sin cookie debe devolver anónimo. Prueba que un ID Token, token inválido y acceso anónimo no entren en rutas protegidas del API Gateway.

## Pedidos

`orden/db/migrations/20261002_add_customer_sub.sql` añade la columna `customer_sub` a PostgreSQL; debe ejecutarse antes de arrancar la nueva imagen Orden, pues el perfil AWS valida el esquema. El valor de cada nuevo pedido se deriva exclusivamente del `sub` del Access Token de Cognito validado por Spring. Las consultas, confirmaciones y boletas filtran por ese identificador. Los pedidos anteriores quedan sin propietario verificable para clientes: no les asignes `customer_sub` por coincidencia de correo sin una revisión de propiedad.

El endpoint que aceptaba destinatario y boleta del cliente está retirado. Las rutas de Orden, Boletas y Shipping no están publicadas en este paquete; Shipping aún necesita comprobaciones de pertenencia y los importes de boleta deben persistirse al comprar antes de ofrecer recibos definitivos. El frontend rechaza el pago si Orden no está disponible. No establezcas `FLASHSTOCK_ORDER_ROUTES_ENABLED=true` mientras esas comprobaciones no estén hechas.

## Comprobaciones locales

Con las dependencias instaladas, ejecuta `pnpm --dir flashstock-frontend install --frozen-lockfile`, `pnpm --dir flashstock-frontend run lint`, `pnpm --dir flashstock-frontend run build`, y `./mvnw test` dentro de `orden`. Antes de publicar rutas nuevas comprueba usuario A/B sobre pedidos distintos, ADMIN, pedido antiguo sin `customer_sub`, boleta mixta y compras concurrentes de la última unidad.

Este ZIP no modifica AWS, Vercel ni PostgreSQL automáticamente. La autenticación real depende de aplicar la configuración y de que las rutas/backends existentes estén sanos.
