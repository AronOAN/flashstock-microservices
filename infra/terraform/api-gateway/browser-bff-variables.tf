variable "flashstock_bff_shared_secret_arn" {
  type = string
  description = "Secrets Manager ARN for a base64-encoded random 32+ byte BFF service secret; provision before enabling Auth issued tokens."
  default = ""
  validation {
    condition = !var.enable_flashstock_issued_tokens || (
      can(regex("^arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", var.flashstock_bff_shared_secret_arn)) &&
      can(regex("^https://[A-Za-z0-9.-]+$", var.vercel_site_url))
    )
    error_message = "Set flashstock_bff_shared_secret_arn and vercel_site_url to the exact HTTPS origin without trailing slash."
  }
}
