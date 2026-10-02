# FlashStock Microservices

Este repositorio reúne el frontend Next.js, Auth, Inventory, Orden, Shipping y la infraestructura Terraform de FlashStock.

## Inicio de sesión

`/login` muestra un formulario propio de correo y contraseña. Next.js envía las credenciales desde el servidor a Amazon Cognito User Pools; Cognito valida la cuenta, emite el Access Token y determina los grupos `USER`/`ADMIN`. El token queda cifrado en una cookie HttpOnly del mismo origen. Las APIs protegidas de AWS validan el JWT y sus permisos. `/auth/login` solo redirige al formulario propio; el dominio Hosted UI no participa en el flujo.

Lee [CAMBIOS-SEGURIDAD-LOGIN.md](CAMBIOS-SEGURIDAD-LOGIN.md) para configurar Cognito, API Gateway, Vercel y la migración de Orden en el orden correcto. Las rutas de Orden, Boletas y Shipping permanecen cerradas por defecto.

## Desarrollo

El frontend usa `flashstock-frontend/.env.example` como guía. Crea `flashstock-frontend/.env.local` con valores de desarrollo y una clave aleatoria nueva. Ejecuta `pnpm --dir flashstock-frontend install --frozen-lockfile` y `pnpm --dir flashstock-frontend dev`. Cada servicio Java tiene su propio `pom.xml` y `mvnw`.
