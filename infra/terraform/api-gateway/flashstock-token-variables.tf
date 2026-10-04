# Stage rollout: migrate DB -> provision Secrets Manager -> deploy auth -> healthy -> publish routes -> enable BFF.
variable "enable_flashstock_issued_tokens" {
  description = "Enable separate FlashStock RS256 access/refresh token issuer in auth ECS after DB/key provisioning."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_flashstock_issued_tokens || var.enable_auth_service
    error_message = "The FlashStock token issuer needs enable_auth_service=true."
  }
}
variable "enable_flashstock_token_routes" {
  description = "Expose only the three FlashStock token routes once the upgraded Auth ECS task is healthy."
  type        = bool
  default     = false
  validation {
    condition = !var.enable_flashstock_token_routes || (
      var.enable_flashstock_issued_tokens && var.enable_auth_routes
    )
    error_message = "Deploy the upgraded Auth service and enable_auth_routes before token routes."
  }
}
variable "flashstock_jwt_private_key_secret_arn" {
  description = "Existing Secrets Manager ARN for base64 PKCS#8 RSA private DER; do not place key material in Terraform state."
  type        = string
  default     = ""
  validation {
    condition = !var.enable_flashstock_issued_tokens || can(regex(
      "^arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$",
      var.flashstock_jwt_private_key_secret_arn
    ))
    error_message = "Set a valid existing Secrets Manager secret ARN for the FlashStock private key."
  }
}
variable "flashstock_jwt_public_key_secret_arn" {
  description = "Existing Secrets Manager ARN for base64 X.509 RSA public DER."
  type        = string
  default     = ""
  validation {
    condition = !var.enable_flashstock_issued_tokens || can(regex(
      "^arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$",
      var.flashstock_jwt_public_key_secret_arn
    ))
    error_message = "Set a valid existing Secrets Manager secret ARN for the FlashStock public key."
  }
}
