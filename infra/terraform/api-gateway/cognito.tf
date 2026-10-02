# FlashStock · Fase 2. Añadir junto a main.tf/outputs.tf/variables.tf de fase 1.
# NO crea IAM Roles, Lambda, un nuevo API Gateway, rutas ni integraciones de backend.
# Cuenta AWS Academy: operaciones sujetas a permisos cognito-idp y apigateway.

resource "aws_cognito_user_pool" "flashstock" {
  name                     = "${var.project_name}-${var.environment}-users"
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]
  deletion_protection      = "ACTIVE"

  username_configuration {
    case_sensitive = false
  }

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = true
    temporary_password_validity_days = 7
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

# Cliente público sin secreto. El formulario propio llama a InitiateAuth desde Next.js;
# Cognito valida la contraseña y emite el Access Token con sus grupos.
resource "aws_cognito_user_pool_client" "frontend" {
  name                                 = "${var.project_name}-${var.environment}-frontend"
  user_pool_id                         = aws_cognito_user_pool.flashstock.id
  generate_secret                      = false
  prevent_user_existence_errors        = "ENABLED"
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls = concat(["http://localhost:3000/auth/callback"],
  var.vercel_site_url == "" ? [] : ["${trimsuffix(var.vercel_site_url, "/")}/auth/callback"])
  logout_urls = concat(["http://localhost:3000/"],
  var.vercel_site_url == "" ? [] : ["${trimsuffix(var.vercel_site_url, "/")}/"])

  enable_token_revocation = true

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]

  access_token_validity  = 15
  id_token_validity      = 15
  refresh_token_validity = 1

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}

# Grupos de aplicación. No llevan role_arn; no crean roles IAM ni conceden
# permisos de infraestructura de AWS.
resource "aws_cognito_user_group" "user" {
  name         = "USER"
  description  = "Clientes FlashStock"
  precedence   = 10
  user_pool_id = aws_cognito_user_pool.flashstock.id
}

resource "aws_cognito_user_group" "admin" {
  name         = "ADMIN"
  description  = "Administración de FlashStock; autorización pendiente en backend"
  precedence   = 0
  user_pool_id = aws_cognito_user_pool.flashstock.id
}

# El authorizer está creado, pero aún NO está asociado a rutas: faltan
# integraciones reales con los microservicios. No protegerá rutas por sí solo.
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.flashstock.id
  name             = "${var.project_name}-${var.environment}-cognito-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.frontend.id]
    issuer   = "https://${aws_cognito_user_pool.flashstock.endpoint}"
  }
}

output "cognito_user_pool_id" {
  description = "ID del User Pool nuevo. No es un secreto."
  value       = aws_cognito_user_pool.flashstock.id
}

output "cognito_app_client_id" {
  description = "ID del cliente público sin secreto."
  value       = aws_cognito_user_pool_client.frontend.id
}

output "cognito_issuer_url" {
  description = "Issuer de JWT de Cognito para este User Pool."
  value       = "https://${aws_cognito_user_pool.flashstock.endpoint}"
}

output "cognito_jwks_url" {
  description = "Claves públicas para verificar tokens de Cognito."
  value       = "https://${aws_cognito_user_pool.flashstock.endpoint}/.well-known/jwks.json"
}

output "cognito_authorizer_id" {
  description = "ID del JWT authorizer agregado a la HTTP API actual."
  value       = aws_apigatewayv2_authorizer.cognito.id
}

# Optional Cognito Hosted UI domain; exact prefix chosen in terraform.tfvars.
# El dominio flashstock-dev-aron ya existe en Cognito, pero NO aparece como recurso
# aws_cognito_user_pool_domain en el state adjunto. No recrearlo ni importarlo de
# forma automática para evitar conflictos o modificar Cognito durante esta fase.
# Si más adelante se quiere gestionarlo con Terraform, importar explícitamente
# el recurso y revisar el plan antes de volver a declararlo.
output "cognito_domain_url" {
  value = var.cognito_domain_prefix == "" ? null : "https://${var.cognito_domain_prefix}.auth.${var.aws_region}.amazoncognito.com"
}
