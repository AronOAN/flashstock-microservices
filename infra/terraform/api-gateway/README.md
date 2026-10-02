# FlashStock · AWS HTTP API sin iam:CreateRole (fase 1 corregida)

Este parche **sustituye archivos** de la version anterior, en el **mismo directorio Terraform**, conservando el estado. Mantiene `aws_apigatewayv2_api.flashstock`, `aws_cloudwatch_log_group.api` y `aws_apigatewayv2_stage.default`. Elimina del codigo la Lambda de prueba, `aws_iam_role` y la integracion `/health` que no llegaron a desplegarse. El plan puede proponer **eliminar el log group de la Lambda de prueba** que sí se creo en el apply parcial. Verifica el plan.

## Recuperar el apply parcial sin duplicar recursos

1. Respaldar `terraform.tfstate*` y `terraform.tfvars` fuera del repositorio. No los subas a GitHub. **No elimines `.terraform`, el estado, ni hagas `terraform destroy`**. No reutilices `tfplan` anterior.
2. Reemplaza `main.tf`, `outputs.tf`, `versions.tf`, `variables.tf`, `README.md` del directorio actual con los archivos de este parche. Conserva **tu `terraform.tfvars` real**, región, cuenta, directorio y state.
3. Ejecuta:

```bash
aws sts get-caller-identity
terraform init
terraform fmt -recursive
terraform validate
terraform state list
terraform plan -out=tfplan-nuevo
terraform show tfplan-nuevo
```

4. Revisa si el plan mantiene la API y stage ya creados. Es esperable que se proponga destruir `aws_cloudwatch_log_group.health_lambda` (solo el grupo de prueba), **no** `aws_apigatewayv2_api.flashstock`, `aws_apigatewayv2_stage.default`, ni `aws_cloudwatch_log_group.api`. Si propone recrear la API o destruir algo imprevisto, PARA y revisa cuenta, región, variables y backend del state antes de aplicar.
5. Si el plan coincide, `terraform apply tfplan-nuevo`. Después `terraform output -raw api_base_url` y `terraform output -raw api_id`.
6. Puedes comprobar que API Gateway existe con `aws apigatewayv2 get-api --api-id "$(terraform output -raw api_id)" --region TU_REGION` y `aws apigatewayv2 get-stages --api-id "$(terraform output -raw api_id)" --region TU_REGION`.

**No habrá `/health` funcional ni `/api/inventory` aún.** HTTP API no admite integraciones MOCK. Una URL publicada no significa que los microservicios estén alojados. No cambies Vercel hasta conectar backend real.

## Cognito — opcional en una segunda aplicacion del mismo estado

`cognito.tf.example` es una plantilla **inactiva** que crea User Pool, App Client público, grupos `USER` y `ADMIN`, y un JWT authorizer sin Lambda ni rol IAM propio. No crea usuarios automáticamente ni cambia permisos AWS del operador. Comprueba permisos Cognito antes de renombrarla a `cognito.tf` y aplicar un nuevo plan. Si ya creaste un pool en la consola, NO la actives para duplicarlo; consulta/importa el existente primero. Usuarios se agregan desde Cognito > User Pools > Users > Create user y se asignan a Groups.

**Limitaciones importantes:** Cognito User Pool autentica a usuarios de FlashStock; no habilita `iam:CreateRole` ni reemplaza un execution role de Lambda. Los grupos `USER`/`ADMIN` no protegen automáticamente cada ruta: API Gateway verifica issuer/audience/scopes de JWT, y las reglas de grupo deben validarse explícitamente en el backend o materializarse en scopes y rutas. No conectes rutas de negocio sin integrar primero backend y la capa de autorización. Al migrar el auth actual (Google OAuth/Spring), hará falta adaptar login, JWT, sesión y cliente web; no es un cambio solo de infraestructura.

## Recursos posteriores sujetos a permisos de la cuenta AWS Academy

Alojar los microservicios Java requerirá cómputo/red/almacenamiento y posiblemente roles IAM de ejecución (ECS task execution role o similar). Cognito no evita ese requisito. Si el laboratorio bloquea tales permisos, utiliza únicamente roles ya preaprobados con `iam:PassRole` autorizado o solicita habilitación al administrador/instructor. No intentes evadir las restricciones.
