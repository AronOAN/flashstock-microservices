# FlashStock · Orden (staged private AWS deployment)

This patch is based on the actual Terraform Inventory files in `flashstock-auth-terraform-context.tar.gz`, the previously delivered Auth Terraform patch, and the Orden Java repository files reviewed for this handoff. It does not include `.tfvars`, `.tfstate`, credentials, new IAM roles, a new ALB, RDS, VPC, Cognito, API Gateway, or public Orden routes.

**Important security blocker:** In the current Java implementation `GET /api/orders/{orderNumber}` is merely `authenticated()` and does not check the order owner; `POST /api/orders` trusts a user-provided `customerEmail` when nonempty; Cognito access tokens do not guarantee an `email` claim, which current customer history/receipt code expects. Therefore **do not add ANY public API Gateway route to Orden** until customer identity/ownership is fixed, retested, and an updated image is published. The internal ALB listener rule is private and cannot be used directly from the public Internet; only specific API Gateway routes determine external exposure. Receipt email sending should also validate recipients against server-generated order data and use an explicitly configured SMTP secret before activation.

## Preflight

From Git Bash at repo root:

```bash
cd ~/Desktop/Duoc/flashstock-microservices
aws sts get-caller-identity --query Account --output text      # must be 823102413975
aws ecr describe-images --repository-name flashstock-dev-orden --region us-east-1 --image-ids imageTag=dev-20261002155118 --query 'imageDetails[0].{Digest:imageDigest,Tags:imageTags}' --output json
```

## Install safely

1. Download the ZIP and put it in `~/Downloads` (or adapt the path).
2. From repo root **before unzipping**, check no pre-existing Orden Terraform files:

```bash
ls infra/terraform/api-gateway/orden-*.tf 2>/dev/null || true
unzip -l "$HOME/Downloads/flashstock-orden-terraform-patch.zip"
unzip -n "$HOME/Downloads/flashstock-orden-terraform-patch.zip" -d .
cd infra/terraform/api-gateway
python scripts/orden-sg-patch.py
terraform fmt
terraform validate
```

`unzip -n` does not overwrite existing files. `orden-sg-patch.py` only adds one *inline* ALB Security Group egress block to the existing Terraform `aws_security_group.alb` (not a separate conflicting standalone rule). It checks the installed Auth block before changing anything and creates a dated `.bak` copy of `inventory-security.tf`. If it refuses to run, inspect the local file and reconcile manually; do not force replacement. Keep backup `.bak` outside the project's committed Terraform directory if desired, and do not commit `.tfvars` or Terraform state.

## Stage 1: foundation only

In `terraform.tfvars`, keep every existing Inventory and Auth flag/image unchanged, and append:

```hcl
enable_orden_foundation = true
enable_orden_service    = false
```

Run in Git Bash:

```bash
terraform plan -out=orden-foundation.tfplan
terraform show -no-color orden-foundation.tfplan
python scripts/orden-plan-check.py foundation orden-foundation.tfplan
# Only if the manual inspection and the specific validator PASS:
terraform apply orden-foundation.tfplan
terraform output orden_ecs_security_group_id
terraform output orden_target_group_arn
```

Expected: exactly seven new Orden resources and ONE in-place ALB SG modification adding a TCP 8083 egress referencing only the new Orden ECS SG; no removals/changes to Auth/Inventory/RDS/ALB routing. If the plan suggests replacements or anything extra, do not apply. `safe-apply.sh` will block the ALB SG update by design; do not disable the generic guard. The specific `orden-plan-check.py` is a narrow allowlist for the saved plan, reviewed before direct apply.

## Stage 2: private ECS service

Verify DB `inventory`, `orders`, `shipments` schema exists as per the already executed migration. Orden `application-aws.properties` uses `ddl-auto=validate`, so it will fail closed if schema is absent. The example temporarily reuses the DEV RDS-managed master credential because the current stack does; production should use a separate least-privileged user and dedicated secret. No migrations are rerun by this patch.

Update only these variables:

```hcl
enable_orden_foundation = true
enable_orden_service    = true
orden_image_uri         = "823102413975.dkr.ecr.us-east-1.amazonaws.com/flashstock-dev-orden:dev-20261002155118"
```

Then:

```bash
terraform plan -out=orden-service.tfplan
terraform show -no-color orden-service.tfplan
python scripts/orden-plan-check.py service orden-service.tfplan "823102413975.dkr.ecr.us-east-1.amazonaws.com/flashstock-dev-orden:dev-20261002155118"
# Only if the plan and validator PASS:
terraform apply orden-service.tfplan
```

Check with AWS CLI in Debian WSL:

```bash
aws ecs describe-services --cluster flashstock-dev --services flashstock-dev-orden --query '{S:services[0].{State:status,Task:taskDefinition,Desired:desiredCount,Running:runningCount,Pending:pendingCount},Errors:failures}' --output json
aws ecs wait services-stable --cluster flashstock-dev --services flashstock-dev-orden
aws elbv2 describe-target-health --target-group-arn "ARN_DEL_TARGET_GROUP_OBTENIDO_EN_GIT_BASH" --query 'TargetHealthDescriptions[].{State:TargetHealth.State,Reason:TargetHealth.Reason}' --output table
aws logs tail /ecs/flashstock-dev/orden --since 20m
```

If `terraform` is not installed in WSL, copy the target group ARN from the Git Bash `terraform output` instead, then run the `aws elbv2` command with that ARN in Debian. The target group health checks `GET /actuator/health` on port 8083, expected `200`.

## Deliberately no public Orden routes in this patch

Do not set `enable_orden_routes`: it does not exist in this first patch. The existing API Gateway has no Order routes introduced here. `curl -i https://ly23z3a5hb.execute-api.us-east-1.amazonaws.com/api/orders` should return a gateway no-match response (typically 404), not be forwarded to Orden. This must be verified by inspecting actual route keys:

```bash
aws apigatewayv2 get-routes --api-id ly23z3a5hb --query 'Items[].RouteKey' --output table
```

Before a subsequent route patch, implement verified ownership for order number lookup and all mutations; ensure `POST /api/orders` never accepts `customerEmail` as the authenticated identity; key ownership by a stable server-verified identity (`sub`, coupled to the client/issuer) or a trustworthy backend identity mapping; fix the receipt sender and missing email claim assumptions; add tests for USER A versus USER B, ADMIN, invalid/ID JWT, and concurrent stock deduction; rebuild/push a NEW Orden image; THEN plan only explicit JWT routes and test with API Gateway + Spring Security. Do not leak JWT into frontend code or API output.

## Safety

No `terraform destroy`, no `terraform state rm`, no shared infrastructure recreation. Do not delete old Inventory/Auth resources. ECR image verification and local Terraform plan review cannot be performed by this generated ZIP. Each saved plan is applied only after human-readable inspection and narrow guard PASS. If AWS Academy `iam:PassRole` or resource quotas block deployment, inspect events rather than bypassing security.
