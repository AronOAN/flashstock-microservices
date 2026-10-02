# FlashStock Orden - staged private deployment. Routes are intentionally NOT published.
variable "enable_orden_foundation" {
  description = "Private ECS security group, ALB egress, PostgreSQL ingress, target group, logs."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_orden_foundation || (var.enable_inventory_foundation && var.enable_auth_foundation)
    error_message = "Keep Inventory and Auth foundation enabled before adding Orden."
  }
}
variable "enable_orden_service" {
  description = "Deploy Orden Fargate ONLY after the foundation and DB schema checks. Does NOT publish API routes."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_orden_service || var.enable_orden_foundation
    error_message = "Enable Orden foundation first."
  }
}
variable "orden_image_uri" {
  description = "Existing immutable FlashStock Orden ECR image, including tag."
  type        = string
  default     = ""
  validation {
    condition = !var.enable_orden_service || can(regex(
      "^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/flashstock-[a-zA-Z0-9_.-]+-orden:[a-zA-Z0-9_.-]+$",
      var.orden_image_uri
    ))
    error_message = "Set orden_image_uri to a fully qualified ECR Orden image with a concrete tag."
  }
}
variable "orden_desired_count" {
  description = "Desired Orden tasks once enabled."
  type        = number
  default     = 1
  validation {
    condition     = var.orden_desired_count >= 1 && floor(var.orden_desired_count) == var.orden_desired_count
    error_message = "orden_desired_count must be a positive integer."
  }
}
