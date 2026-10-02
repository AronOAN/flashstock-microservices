output "flashstock_vpc_id" {
  value = var.enable_inventory_foundation ? aws_vpc.flashstock[0].id : null
}
output "inventory_ecr_repository_url" {
  value = var.enable_inventory_foundation ? aws_ecr_repository.inventory[0].repository_url : null
}
output "inventory_ecs_cluster" {
  value = var.enable_inventory_foundation ? aws_ecs_cluster.flashstock[0].name : null
}
output "inventory_private_subnet_a" {
  value = var.enable_inventory_foundation ? aws_subnet.application[0].id : null
}
output "inventory_private_subnet_b" {
  value = var.enable_inventory_foundation ? aws_subnet.application[1].id : null
}
output "inventory_ecs_security_group" {
  value = var.enable_inventory_foundation ? aws_security_group.ecs[0].id : null
}
output "inventory_migration_task_arn" {
  value = var.enable_inventory_migration_task ? aws_ecs_task_definition.inventory_migrate[0].arn : null
}
output "inventory_target_group_arn" {
  value = var.enable_inventory_foundation ? aws_lb_target_group.inventory[0].arn : null
}
output "inventory_vpc_link_id" {
  value = var.enable_inventory_foundation ? aws_apigatewayv2_vpc_link.flashstock[0].id : null
}
output "flashstock_internal_alb_arn" {
  value = var.enable_inventory_foundation ? aws_lb.flashstock[0].arn : null
}
output "inventory_rds_endpoint" {
  value = var.enable_inventory_foundation ? aws_db_instance.inventory[0].endpoint : null
}
output "inventory_rds_managed_secret_arn" {
  value       = var.enable_inventory_foundation ? aws_db_instance.inventory[0].master_user_secret[0].secret_arn : null
  description = "ARN only, no password. Execution role needs secretsmanager:GetSecretValue."
}
output "inventory_api_url" {
  value = var.enable_inventory_routes ? "${trimsuffix(aws_apigatewayv2_stage.default.invoke_url, "/")}/api/inventory" : null
}
