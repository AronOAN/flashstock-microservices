# Auth reuses the existing FlashStock networking, RDS and Cognito. All stages default OFF.
variable "enable_auth_foundation" {
  description = "Add private Auth security group, RDS permission, target group and log group. Requires Inventory foundation."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_auth_foundation || var.enable_inventory_foundation
    error_message = "enable_auth_foundation requires existing enable_inventory_foundation=true."
  }
}
variable "enable_auth_service" {
  description = "Register Auth task definition and run Fargate after verifying shared RDS schema."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_auth_service || var.enable_auth_foundation
    error_message = "First enable the Auth foundation."
  }
}
variable "enable_auth_routes" {
  description = "Publish selected Auth routes after the Auth target group becomes healthy."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_auth_routes || (var.enable_auth_foundation && var.enable_auth_service)
    error_message = "Auth foundation and service must be enabled before Auth routes."
  }
}
variable "auth_image_uri" {
  description = "Exactly one tagged Auth image already pushed to FlashStock ECR; never a secret."
  type        = string
  default     = ""
  validation {
    condition = !var.enable_auth_service || can(regex(
      "^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-zA-Z0-9_./-]+:[a-zA-Z0-9_.-]+$",
      var.auth_image_uri
    ))
    error_message = "Set auth_image_uri to the exact ECR image with immutable tag."
  }
}
variable "auth_desired_count" {
  description = "Auth ECS desired count once enabled."
  type        = number
  default     = 1
  validation {
    condition     = var.auth_desired_count >= 1 && floor(var.auth_desired_count) == var.auth_desired_count
    error_message = "auth_desired_count must be a positive integer."
  }
}
