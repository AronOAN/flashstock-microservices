# Etapas independientes: INFRA -> MIGRACION SQL -> ECS -> RUTAS.
# El valor false no crea recursos nuevos en AWS y conserva API Gateway/Cognito existentes.
variable "enable_inventory_foundation" {
  type        = bool
  default     = false
  description = "Crea SOLO la red/ECR/ALB/VPC Link/ECS cluster/RDS de FlashStock (no toca solicitud-dev)."
}
variable "enable_inventory_migration_task" {
  type        = bool
  default     = false
  description = "Registra tarea one-off psql. Ejecucion manual con aws ecs run-task antes de arrancar inventory."
  validation {
    condition     = !var.enable_inventory_migration_task || var.enable_inventory_foundation
    error_message = "Habilita primero enable_inventory_foundation."
  }
}
variable "enable_inventory_service" {
  type        = bool
  default     = false
  description = "Crea la task definition Java y el servicio Fargate después de ejecutar schema-inventory.sql."
  validation {
    condition     = !var.enable_inventory_service || var.enable_inventory_foundation
    error_message = "La infraestructura foundation debe estar habilitada."
  }
}
variable "enable_inventory_routes" {
  type        = bool
  default     = false
  description = "Publica exclusivamente /api/inventory cuando los targets ECS estén HEALTHY."
  validation {
    condition     = !var.enable_inventory_routes || (var.enable_inventory_foundation && var.enable_inventory_service)
    error_message = "Habilita infrastructure y servicio antes de activar las rutas."
  }
}
variable "flashstock_vpc_cidr" {
  type        = string
  default     = "10.72.0.0/16"
  description = "CIDR exclusivo de FlashStock; usa dos subnets /24 por cada capa. No se usa solicitud-dev."
  validation {
    condition     = can(cidrnetmask(var.flashstock_vpc_cidr)) && can(regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}/16$", var.flashstock_vpc_cidr))
    error_message = "Usa un CIDR IPv4 /16 válido, por ejemplo 10.72.0.0/16."
  }
}
variable "inventory_db_instance_class" {
  type        = string
  default     = "db.t3.micro"
  description = "Debe estar admitido por us-east-1 y por tu cuota AWS Academy."
}
variable "inventory_db_engine_version" {
  type        = string
  default     = null
  nullable    = true
  description = "null: versión PostgreSQL predeterminada disponible en RDS; definir si se requiere versión específica."
}
variable "inventory_image_uri" {
  type        = string
  default     = ""
  description = "Imagen ECR inmutable con etiqueta SHA creada y subida DESPUÉS de foundation."
  validation {
    condition     = !var.enable_inventory_service || can(regex("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-zA-Z0-9_./-]+:[a-zA-Z0-9_.-]+$", var.inventory_image_uri))
    error_message = "Para ECS proporciona la URI ECR con tag SHA (no valores ficticios)."
  }
}
variable "ecs_task_execution_role_arn" {
  type        = string
  default     = ""
  description = "Rol EXISTENTE autorizado para ecs-tasks.amazonaws.com con ECR, CloudWatch, Secrets Manager y iam:PassRole. NO se crea IAM."
  validation {
    condition     = !(var.enable_inventory_service || var.enable_inventory_migration_task) || can(regex("^arn:aws:iam::[0-9]{12}:role/.+", var.ecs_task_execution_role_arn))
    error_message = "Debes proporcionar un rol ECS existente y autorizado con iam:PassRole."
  }
}
variable "inventory_desired_count" {
  type    = number
  default = 1
  validation {
    condition     = var.inventory_desired_count >= 1 && floor(var.inventory_desired_count) == var.inventory_desired_count
    error_message = "El servicio activo debe tener al menos una tarea ECS."
  }
}
variable "inventory_admin_email" {
  type        = string
  default     = "aron83353@gmail.com"
  description = "Solo para satisfacer placeholder legacy: la autorización administrativa AWS exige ROLE_ADMIN Cognito."
}
