variable "aws_region" {
  description = "Region AWS elegida para API Gateway."
  type        = string

  validation {
    condition     = can(regex("^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$", var.aws_region))
    error_message = "Introduce una region AWS con formato valido, por ejemplo us-east-1 o sa-east-1."
  }
}

variable "project_name" {
  description = "Prefijo de los recursos."
  type        = string
  default     = "flashstock"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,30}$", var.project_name))
    error_message = "Utiliza entre 2 y 31 caracteres en minuscula, numeros o guiones, comenzando por letra."
  }
}

variable "environment" {
  description = "Entorno, p. ej. dev."
  type        = string
  default     = "dev"
}

variable "log_retention_days" {
  description = "Dias de retencion de logs del gateway."
  type        = number
  default     = 14
}

# Exact frontend origin: production Vercel or custom domain, with https, no slash/path.
variable "vercel_site_url" {
  type    = string
  default = ""
  validation {
    condition     = var.vercel_site_url == "" || can(regex("^https://[a-zA-Z0-9.-]+$", var.vercel_site_url))
    error_message = "Use https://HOSTNAME, without path or trailing slash."
  }
}
variable "cognito_domain_prefix" {
  description = "An available globally unique Cognito hosted domain prefix, without .auth.us-east-1.amazoncognito.com"
  type        = string
  default     = ""
}
