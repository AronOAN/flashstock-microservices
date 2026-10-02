# FlashStock DEV — Terraform limpio, independiente de `solicitud-dev`

## Alcance real

El ZIP conserva las direcciones del Terraform previamente aplicado de FlashStock:
`aws_apigatewayv2_api.flashstock`, `aws_apigatewayv2_stage.default`,
`aws_cloudwatch_log_group.api`, y todos los recursos de `cognito.tf`. **No lo
apliques en un directorio nuevo ni con un estado vacío**: conserva el `terraform.tfstate`
existente (lineage de la copia recibida: `a2da2196-03ce-828f-f20c-e955f55cacfe`, serial 18).
API existente esperado `ly23z3a5hb`; User Pool esperado `us-east-1_jkrNUk7yQ`.
Comprobar SIEMPRE contra AWS en vivo, porque el snapshot enviado podría ser antiguo.

. El dominio Cognito ya figura configurado en el User Pool, pero no había una
instancia `aws_cognito_user_pool_domain` en el state; por eso esta entrega no intenta
crearlo de nuevo (solo conserva la URL en los outputs).

Elimina del paquete: `.terraform/` (incluidos ~843 MB de proveedores Windows),
`tfplan*` (planes caducados), `terraform.tfstate*` (privado), `terraform.tfvars`
(privado), `lambda/`, `health/`, pruebas antiguas y ejemplos obsoletos de Cognito.
`permissions-lambda.tf` se retira porque las direcciones opcionales tenían **0
instancias** en el estado recibido. `backend-routes.tf` ahora contiene SOLO inventory.
La Lambda `/api/auth/permissions` NO se implementa aquí: la UI de login puede necesitar
más adelante auth/permissions para reflejar sesión y roles, no se afirma que esté lista.

Infra compartida de FlashStock: VPC privada exclusiva, un ECS cluster, un ALB
interno y VPC Link propios, NAT único (DEV); cada microservicio futuro tendrá ECR,
Task Definition, ECS Service, Target Group, health check y rutas independientes.
La BD de inventory es privada (RDS con contraseña administrada por Secrets Manager).
No hace falta una VPC/ALB/API Gateway completo por cada microservicio ni usar recursos
existentes de `solicitud-dev`.

⚠️ Servicios como NAT, ALB, RDS, ECS, IP elástica y VPC Link pueden generar costo o
consumir el presupuesto/límites de AWS Academy. Esto NO puede garantizar pasar `apply`:
la cuenta `voclabs` puede carecer de permisos o cuota. NO crea roles IAM. Para migración
y servicio requieres un execution role **existente**, trust `ecs-tasks.amazonaws.com`,
`iam:PassRole` autorizado, `AmazonECSTaskExecutionRolePolicy` y
`secretsmanager:GetSecretValue` sobre el secreto RDS administrado (y `kms:Decrypt`
si se usa una clave KMS de cliente). Si falta, solicita el rol/permisos al laboratorio.
El rol Cognito ADMIN no sustituye IAM.

## 0. Preparar directorio existente — Git Bash

Desde el mismo directorio donde está tu estado actual (por ejemplo
`terraform/api-gateway`), ANTES DE SOBRESCRIBIR, ejecuta:

```bash
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=""
aws sts get-caller-identity
terraform init
terraform state list
terraform output -raw api_id
terraform output -raw cognito_user_pool_id
```

Ambos IDs deben ser exactamente los de arriba. Si no, **DETENERSE** (otro estado,
cuenta o región). Guarda una copia FUERA de la carpeta/REPO. No subas los backups:

```bash
mkdir -p "$HOME/flashstock-backups"
terraform state pull > "$HOME/flashstock-backups/state-$(date +%Y%m%d-%H%M%S).tfstate"
cp terraform.tfvars "$HOME/flashstock-backups/tfvars-$(date +%Y%m%d-%H%M%S).txt"
```

En el snapshot recibido estaban ausentes las **instancias** de Lambda y business
routes. Antes de retirar `permissions-lambda.tf`, comprueba de nuevo:

```bash
terraform state list | grep -E '(aws_lambda_function.permissions|aws_apigatewayv2_route.permissions|aws_apigatewayv2_route.business|aws_apigatewayv2_integration.business_alb)' || true
```

Si aparece alguna instancia, NO retires aún la configuración; hay que revisar su
estado/importación antes. Si no aparece, copia los .tf NUEVOS sobre los .tf anteriores,
y mueve fuera del directorio activo el archivo heredado `permissions-lambda.tf`:

```bash
[ ! -f permissions-lambda.tf ] || mv permissions-lambda.tf "$HOME/flashstock-backups/permissions-lambda.tf"
```

Si la carpeta heredada sigue conteniendo `cognito.tf.example`, `health/`, `lambda/`,
`README-PATCH.md`, `terraform.tfvars.changes.example` u otros borradores antiguos,
muévelos a `$HOME/flashstock-backups/` para que el directorio quede limpio.
Elimina planes `tfplan*` caducados; NO borres `terraform.tfstate`,
`terraform.tfstate.backup`, `terraform.tfvars` ni `.terraform.lock.hcl` de tu máquina.
La carpeta `.terraform` es caché y no se envía en este ZIP: `terraform init`
recreará lo necesario. Nunca ejecutes `terraform destroy` para limpiar un proyecto.

Edita el `terraform.tfvars` EXISTENTE; conserva `aws_region`, `project_name`,
`environment`, `vercel_site_url`, `cognito_domain_prefix`. Borra las cinco variables
retiradas de fases previas (ya no se declaran):

```
enable_permissions_lambda
permission_lambda_execution_role_arn
enable_business_routes
existing_vpc_link_id
existing_alb_listener_arn
```

Agrega las nuevas variables exactamente UNA VEZ desde `terraform.tfvars.example`.
No copies el ejemplo entero encima del archivo real si cambiaste algún valor.

## 1. Fundación FlashStock (NO levanta todavía el servicio)

En `terraform.tfvars` pon:

```hcl
enable_inventory_foundation     = true
enable_inventory_migration_task = false
enable_inventory_service       = false
enable_inventory_routes        = false
```

Antes comprueba capacidad del laboratorio (`ec2:CreateVpc`, `ec2:CreateNatGateway`,
`rds:CreateDBInstance`, `ecs:CreateCluster`, `ecr:CreateRepository`,
`elasticloadbalancing:CreateLoadBalancer`, `apigateway:CreateVpcLink`). Verifica en
AWS que `10.72.0.0/16` no solapa redes que pretendas conectar a futuro.

```bash
terraform init
terraform fmt -recursive
terraform validate
terraform plan -out=plan-inventory-foundation
terraform show plan-inventory-foundation
```

**No aplicar si aparecen `destroy` o `replace` del API Gateway, Cognito o recursos
ajenos.** El plan debe proponer principalmente recursos NUEVOS con nombre
`flashstock-dev-*`. Si es así:

```bash
terraform apply plan-inventory-foundation
terraform output -raw inventory_ecr_repository_url
terraform output -raw inventory_rds_endpoint
terraform output -raw inventory_vpc_link_id
```

La VPC y subredes de FlashStock son independientes de `solicitud-dev`. Para DEV se
usa un NAT en una sola AZ (menor costo, sin tolerancia a fallo de esa AZ). RDS no
tiene IP pública ni ruta 0.0.0.0/0. Se exige `deletion_protection=true` y
`prevent_destroy` en la DB.

## 2. Construir la imagen y subirla a ECR — Git Bash

Desde raíz del repositorio (no desde `terraform/api-gateway`):

```bash
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=""
export ECR_URL=$(terraform -chdir=infra/terraform/api-gateway output -raw inventory_ecr_repository_url)
export TAG=$(git rev-parse --short HEAD)
export IMAGE_URI="$ECR_URL:$TAG"
export ECR_HOST="${ECR_URL%%/*}"

mvn -B -f inventory/pom.xml clean verify
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$ECR_HOST"
docker build --pull -f inventory/Dockerfile -t "$IMAGE_URI" inventory
docker push "$IMAGE_URI"
echo "inventory_image_uri = \"$IMAGE_URI\""
```

Copia el valor impreso al `terraform.tfvars`. No compartas credenciales ni subas
`terraform.tfvars` a Git.

## 3. Rol de ejecución ECS + migración SQL PRIVADA

Localiza un IAM role ya aprobado por Academy, por ejemplo consultando (si el laboratorio
lo permite):

```bash
aws iam list-roles --query 'Roles[].{Name:RoleName,Arn:Arn}' --output table
aws iam get-role --role-name NOMBRE_REAL --query 'Role.AssumeRolePolicyDocument' --output json
```

NO crees ningún rol ni reutilices ciegamente uno de `solicitud-dev`. Comprueba trust
`ecs-tasks.amazonaws.com`, `iam:PassRole` y permisos de ECR/logs/secret RDS. En
`terraform.tfvars`:

```hcl
ecs_task_execution_role_arn      = "arn:aws:iam::823102413975:role/ROL_APROBADO_REAL"
enable_inventory_migration_task = true
enable_inventory_service       = false
enable_inventory_routes        = false
```

```bash
terraform plan -out=plan-migration
terraform show plan-migration
terraform apply plan-migration
```

Ejecuta UNA VEZ la task `inventory-migrate` en la VPC privada, sin exposición de RDS:

```bash
CLUSTER=$(terraform output -raw inventory_ecs_cluster)
MIGRATION=$(terraform output -raw inventory_migration_task_arn)
SUBNET_A=$(terraform output -raw inventory_private_subnet_a)
SUBNET_B=$(terraform output -raw inventory_private_subnet_b)
SG=$(terraform output -raw inventory_ecs_security_group)
NET="awsvpcConfiguration={subnets=[$SUBNET_A,$SUBNET_B],securityGroups=[$SG],assignPublicIp=DISABLED}"
TASK=$(aws ecs run-task --cluster "$CLUSTER" --launch-type FARGATE \
  --task-definition "$MIGRATION" --network-configuration "$NET" \
  --query 'tasks[0].taskArn' --output text)
if [ -z "$TASK" ] || [ "$TASK" = "None" ]; then echo 'ERROR: tarea no creada; revisar permisos/eventos ECS'; exit 1; fi
aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$TASK"
aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK" \
  --query 'tasks[0].{Reason:stoppedReason,Exit:containers[0].exitCode,ContainerReason:containers[0].reason}' --output table
aws logs tail "/ecs/flashstock-dev/inventory" --since 20m
```

**NO continúes hasta obtener Exit=0**. Si falla, revisar rol/Secrets Manager,
NAT, RDS y `schema-inventory.sql`. El SQL refleja las cuatro entidades JPA
actuales (`inventory`, `cart_items`, `orders`, `shipments`) y es exclusivamente
el bootstrap de una BD DEV vacía. No se concede red pública a PostgreSQL.

## 4. Servicio de Inventory (Spring Boot 8082)

En `terraform.tfvars` mantén foundation=true y el ARN de IAM ya validado, además:

```hcl
enable_inventory_service = true
inventory_image_uri      = "URI_REAL_COPIADA_DEL_PUSH_ECR"
enable_inventory_routes  = false
```

```bash
terraform plan -out=plan-service
terraform show plan-service
terraform apply plan-service
aws ecs wait services-stable --cluster "$(terraform output -raw inventory_ecs_cluster)" --services flashstock-dev-inventory
aws elbv2 describe-target-health --target-group-arn "$(terraform output -raw inventory_target_group_arn)" \
  --query 'TargetHealthDescriptions[].{State:TargetHealth.State,Reason:TargetHealth.Reason}' --output table
aws logs tail /ecs/flashstock-dev/inventory --since 15m
```

Esperado: `State=healthy`. `application-aws.properties` mantiene
`spring.jpa.hibernate.ddl-auto=validate` y `legacy-admin-email-enabled=false`.
La cuenta de email configurada NO obtiene admin solo por coincidir con el correo;
Cognito debe asignar el grupo `ADMIN` y Spring comprobará `ROLE_ADMIN`.

Esta fase DEV inyecta el usuario maestro administrado por RDS al contenedor.
Antes de PROD, crea un usuario SQL exclusivo de aplicación con permisos mínimos,
su propio secreto, migraciones versionadas y estrategia de rotación/redeploy.
La rotación del secreto maestro RDS requiere redeplegar tareas que capturaron
el valor antiguo en variables de entorno.

## 5. Conectar exclusivamente inventario al API Gateway EXISTENTE

Solamente después de recibir `healthy`:

```hcl
enable_inventory_routes = true
```

```bash
terraform plan -out=plan-route
terraform show plan-route
terraform apply plan-route
terraform output -raw api_id
terraform output -raw inventory_api_url
aws apigatewayv2 get-routes --api-id ly23z3a5hb \
  --query 'Items[].{Key:RouteKey,Auth:AuthorizationType}' --output table
curl -i https://ly23z3a5hb.execute-api.us-east-1.amazonaws.com/api/inventory
curl -i https://flashstock-microservices.vercel.app/api/inventory
```

Esperado `HTTP 200` y JSON de inventario. BD vacía devuelve lista vacía
legítimamente, no productos inventados: crea productos con POST autorizado
usando un Access Token real de Cognito perteneciente al grupo ADMIN.
El navegador usa Vercel/Next BFF como origen; en Vercel configura
`FLASHSTOCK_API_BASE_URL=https://ly23z3a5hb.execute-api.us-east-1.amazonaws.com`.

**Límite actual:** Terraform de esta entrega implementa `/api/inventory` y NO
el endpoint `/api/auth/permissions` usado por el BFF para `/api/auth/me`. Ese
endpoint requerirá desplegar el servicio auth o integrar una solución de permisos
que valide JWT. No se declara que órdenes, shipping, carrito o login completo estén
listos, ni se cambia nada de `solicitud-dev`.
