# Actualización continua de FlashStock (WSL)

Ejecuta estos comandos **desde la raíz del repositorio** en tu WSL, tras descomprimir el ZIP sobre tu checkout. La extracción no debe borrar tu `terraform.tfvars`, `terraform.tfstate` ni tus secretos locales. Revisa `git diff` antes del commit. Las rutas de Orden y el envío de boletas deben seguir cerrados.

## 1. Validación y CI

```bash
set -euo pipefail
for service in auth inventory orden shipping; do
  (cd "$service" && bash mvnw clean test jacoco:report)
done
(cd flashstock-frontend && pnpm install --frozen-lockfile && pnpm run lint && pnpm run build)
git status --short
git diff --check
git add .github/workflows/ci.yml auth inventory orden shipping flashstock-frontend \
  infra/terraform/api-gateway/*.tf README.md README-ORDEN.md \
  CAMBIOS-SEGURIDAD-LOGIN.md SONAR-Y-SEGURIDAD.md DESPLIEGUE-CONTINUO.md
git commit -m "fix: renovar sesiones Cognito y corregir hallazgos Sonar"
git push origin main
```

Espera que GitHub Actions (`FlashStock CI/CD Pipeline`) termine bien y que SonarCloud analice el nuevo commit. En SonarCloud comprueba las tres condiciones del Quality Gate (seguridad A, fiabilidad A y cobertura nueva ≥ 80 %); `SONAR-Y-SEGURIDAD.md` explica cómo revisar las excepciones CSRF con evidencia. **No publiques las imágenes si alguna condición permanece roja**: identifica las líneas descubiertas en New Code y amplía las pruebas pertinentes. Usa la misma revisión del commit para las dos imágenes.

## 2. Publicar imágenes inmutables de Auth e Inventory

```bash
set -euo pipefail
export AWS_DEFAULT_REGION=us-east-1 AWS_PAGER=""
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="$ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com"
TAG="$(git rev-parse --short=12 HEAD)"
aws ecr get-login-password --region "$AWS_DEFAULT_REGION" |
  docker login --username AWS --password-stdin "$REGISTRY"

for service in auth inventory; do
  repo="flashstock-dev-$service"
  image="$REGISTRY/$repo:$TAG"
  if aws ecr describe-images --repository-name "$repo" --image-ids "imageTag=$TAG" >/dev/null 2>&1; then
    echo "La imagen inmutable ya existe: $image"
  else
    docker build --pull --platform linux/amd64 -t "$image" "./$service"
    docker push "$image"
  fi
  aws ecr describe-images --repository-name "$repo" --image-ids "imageTag=$TAG" \
    --query 'imageDetails[0].{Digest:imageDigest,Tags:imageTags}' --output json
done
```

## 3. Aplicar Terraform sin publicar Orden

En tu archivo local `infra/terraform/api-gateway/terraform.tfvars`, reemplaza **sólo** `auth_image_uri` e `inventory_image_uri` por las URI con etiqueta `$TAG` recién subidas; conserva tus demás variables y deja `enable_orden_service`/rutas de Orden en su estado actual. Si Orden ya está desplegado, su código nuevo exigirá imagen propia tras las verificaciones de esquema y pertenencia; no actives rutas por este cambio.

```bash
set -euo pipefail
TF=infra/terraform/api-gateway
terraform -chdir="$TF" init -input=false
terraform -chdir="$TF" plan -out=flashstock-update.tfplan
terraform -chdir="$TF" show -no-color flashstock-update.tfplan | less
```

El plan esperado para esta actualización cambia las task definitions y servicios Auth/Inventory por sus imágenes nuevas. Si propone reemplazar Cognito User Pool, API Gateway, RDS, subredes, secretos, habilitar rutas de Orden o destruir recursos adicionales, **detén la aplicación y revisa las variables/estado**. Con el plan revisado:

```bash
terraform -chdir="$TF" apply flashstock-update.tfplan
aws ecs wait services-stable --cluster flashstock-dev \
  --services flashstock-dev-auth flashstock-dev-inventory
aws ecs describe-services --cluster flashstock-dev \
  --services flashstock-dev-auth flashstock-dev-inventory \
  --query 'services[].{Name:serviceName,Desired:desiredCount,Running:runningCount,Task:taskDefinition}' \
  --output table
```

Si Terraform incluye una **nueva** revisión de Orden por otros cambios de configuración, verifica antes que su tarea no publique rutas y que su migración SQL esté ejecutada. No fuerces `enable_orden_service=true` como parte de este despliegue de Auth/Inventory.

## 4. Comprobar protección y publicar Vercel

```bash
set -euo pipefail
API="$(terraform -chdir="$TF" output -raw api_base_url)"
for path in /api/auth/me /api/admin/metrics /api/inventory; do
  curl -sS -o /dev/null -w "$path HTTP %{http_code}\n" "${API%/}$path"
done
curl -sS -o /dev/null -w '/api/maps/config HTTP %{http_code}\n' "${API%/}/api/maps/config"
```

Sin credenciales deben devolver `401` las tres rutas protegidas y `200` Maps. En Vercel deben persistir las variables de servidor `FLASHSTOCK_SITE_URL`, `FLASHSTOCK_API_BASE_URL`, `COGNITO_ISSUER_URL`, `COGNITO_APP_CLIENT_ID`, `FLASHSTOCK_SESSION_KEY`; mantén `FLASHSTOCK_ORDER_ROUTES_ENABLED=false`. No cambies la clave de sesión en cada deploy o las cookies vigentes dejarán de poder descifrarse. Al tener API y ECS sanos, despliega mediante el workflow existente:

```bash
gh workflow run deploy.yml --ref main -f backend_ready=true
gh run watch
```

Tras iniciar sesión en el sitio, inspecciona Network: `GET /api/auth/me` debe responder `data.authenticated=true`. En Storage comprueba que existen las cookies `__Host-flashstock-session` y `__Host-flashstock-refresh`, **sin copiar ni compartir sus valores**. Una respuesta `502` identifica rechazo de Auth; `503` indica que Cognito o el backend no están disponibles. Para comprobar renovación sin esperar, espera a que transcurran los 15 minutos del Access Token y repite `GET /api/auth/me`; el Refresh Token dura un día y no renueva su propia fecha de caducidad.
