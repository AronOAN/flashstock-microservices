# Integration overlay — copy into the EXISTING flashstock-frontend (not a standalone Next project)

Your uploaded zip contains ONLY auth/inventory/orden/shipping; the original Next.js `flashstock-frontend` was inspected in the linked GitHub repository. Copy/merge these files with that existing project. Do not replace its package.json, pnpm lock or static assets.

1. Add Vercel Production env vars FLASHSTOCK_SITE_URL (real https:// frontend origin), FLASHSTOCK_API_BASE_URL (real AWS HTTP API URL), COGNITO_APP_CLIENT_ID, COGNITO_DOMAIN (after hosted domain creation), FLASHSTOCK_SESSION_KEY (base64 of exactly 32 random bytes, secret). Vercel's deployment must use this project's env vars, not only `--build-env`.
2. Terraform Cognito client callback must include EXACT `${FLASHSTOCK_SITE_URL}/auth/callback` and local callback `http://localhost:3000/auth/callback`.
3. Public login link is `/auth/login`; `src/app/login/page.tsx` uses an iframe and the link has target=_top (Cognito Hosted UI does not render in an iframe).
4. The frontend `window.FLASHSTOCK_API_BASE` is empty. All /api/* calls flow through the Next.js BFF; encrypted access token stays HttpOnly, not exposed in public JS or localStorage. Session expires with access token (15 minutes). NO REFRESH implemented in this phase, login again when expired.
5. `GET /api/auth/me` fetches JWT-authorized AWS `/api/auth/permissions`; if Lambda not enabled it returns 503, rather than falsely pretending auth works. With no local session it returns backward-compatible anonymous DTO.
6. `GET /api/inventory` and other business endpoints will 404 until the backend has been deployed and routes integrated. This overlay does NOT create ECS, VPC Link, ALB or a business backend.
7. Do not copy `next.config.ts` without the BFF route folder because that intentionally removes old rewrites. Do not deploy to production before API Gateway routes exist.
8. Review older static HTML links: replace links to `/oauth2/authorization/google`, `/logout`, and `http://localhost:3000/` with `/auth/login`, `/auth/logout`, `/` respectively. `public/static/login.html` is already replaced.
