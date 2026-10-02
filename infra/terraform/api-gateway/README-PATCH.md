# Terraform phase 3 overlay — apply to your EXISTING state only

Existing state MUST already contain:
- `aws_apigatewayv2_api.flashstock` API `ly23z3a5hb`, stage `$default` in us-east-1.
- Cognito user pool `us-east-1_jkrNUk7yQ`, client `1v1gmjscc2qtpuerhs63v6taob`, and authorizer `mdwebu` as resources from phase 2.

Copy `cognito.tf`, `variables.tf`, `permissions-lambda.tf`, `backend-routes.tf`, and `lambda/` into the **same** directory that has phase1 main.tf/outputs.tf and the same Terraform state. `cognito.tf` and `variables.tf` replace only our known phase 2 versions; if you edited those files, merge the differences manually. Do not copy any .tfstate or terraform.tfvars from this package.

`terraform.tfvars.changes.example` shows NEW variable names; append your own true values to your existing terraform.tfvars (don't duplicate variables). Set a unique Cognito domain prefix and real production Vercel origin before testing hosted login.

Run `aws sts get-caller-identity`, `terraform state list`, `terraform output api_id`, `terraform fmt`, `terraform init`, `terraform validate`, `terraform plan -out=tfplan-aws3`, and inspect the whole plan before `terraform apply tfplan-aws3`. If Terraform wants to recreate the existing API, pool or client, STOP; don't apply. Your `terraform output api_id` must stay `ly23z3a5hb`.

**Lambda prerequisite:** Cognito groups are not Lambda execution roles. Set enable_permissions_lambda=true ONLY after checking an existing role trusted by lambda.amazonaws.com and permissions lambda:CreateFunction, iam:PassRole on that ARN, lambda:AddPermission, and apigateway route/integration create. Example check: `aws iam get-role --role-name LabRole --query 'Role.Arn' --output text` (the role might not exist or access might be denied). Set ARN only if authorized. No aws_iam_role is created by this package. If no execution role is available, Lambda deployment remains blocked; leave false and do not advertise /api/auth/permissions as operational.

**Security:** JWT Authorizer validates JWT cryptographically. Route GET /api/auth/permissions requires `openid` scope. Lambda uses ONLY `event.requestContext.authorizer.jwt.claims`, and maps Cognito groups exactly `USER` => `ROLE_USER`, `ADMIN` => `ROLE_ADMIN`. It returns a permission summary; it DOES NOT guard other routes. Spring AWS profile enforces resource-specific admin/user rules and validates JWT again; never treat UI visibility or a Lambda summary as authorization.

**Business routes:** enabling enable_business_routes before the four Java services, private ALB target groups and VPC Link exist is unsafe/will fail. Path rules at ALB must map `/api/auth/*`, `/api/admin/*`, `/api/maps/*` -> auth:8081; `/api/inventory*` -> inventory:8082; `/api/orders*`, `/api/receipts*` -> orden:8083; `/api/shipping*` -> shipping:8084. Missing cart/payments/coupons implementations are NOT invented. JWT authorizer validates tokens on protected routes; the Spring AWS profile validates ROLE_ADMIN or ROLE_USER.

Cognito access token generally does NOT carry email by default. Legacy order ownership/history is currently keyed by email; must migrate to stable `sub` or add a verified userinfo resolution path. Until completed, don't claim order history/checkout are production-ready. Public GET inventory can work without a login once business routes exist.
