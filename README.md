# FlashStock — Login profesional y corrección de navegación OAuth

## Instalación segura (Git Bash en la raíz del repositorio)

1. Respaldar `flashstock-frontend/public/static/login.html` y `flashstock-frontend/src/app/login/page.tsx`.
2. Descomprimir `flashstock-login-patch.zip` en la raíz del proyecto (no dentro de `flashstock-frontend`).
3. `python scripts/fix-login-navigation.py` desde la raíz. Este script modifica enlaces de login en `public/static/*.html` para abrir `/login` fuera del iframe, confirma que `main.js` ya utiliza `userLink.target='_top'` y añade una guarda para que la tienda pública no consulte Inventory administrativo periódicamente.
4. `cd flashstock-frontend && pnpm install --frozen-lockfile && pnpm run lint && pnpm run build`.
5. `git add flashstock-frontend/public/static/login.html flashstock-frontend/public/static/css/flashstock-login.css flashstock-frontend/src/app/login/page.tsx flashstock-frontend/public/static/*.html && git commit ... && git push origin main`. No subas el archivo de script si no lo deseas.
6. Ejecutar el workflow manual de despliegue de Vercel.

## Comportamiento

- `/login` usa una página Next propia, con enlace HTML nativo a `/auth/login` (no `next/link`).
- `/static/login.html` presenta la misma interfaz para accesos antiguos.
- El enlace desde la tienda se abre con `target="_top"` porque la tienda está dentro de un iframe del mismo origen.
- `/auth/login` conserva el flujo de Cognito Authorization Code + PKCE actual; Cognito no se debe cargar dentro del iframe ni mediante fetch/RSC.
- Las credenciales se solicitan en Cognito, no en FlashStock.

## Inventory 401

Es un comportamiento correcto para un endpoint administrativo; no debe cambiarse a `permitAll()`. El instalador añade `if (!isAdminSession()) return;` al inicio de `refreshStorefrontInventory` si no estaba presente. La tienda estática no representa inventario en tiempo real ni deben habilitarse compras reales hasta agregar catálogo público seguro y validación de stock en backend.
