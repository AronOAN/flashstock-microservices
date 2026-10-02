output "orden_ecs_security_group_id" {
  value = try(aws_security_group.orden_ecs[0].id, null)
}
output "orden_target_group_arn" {
  value = try(aws_lb_target_group.orden[0].arn, null)
}
output "orden_log_group" {
  value = try(aws_cloudwatch_log_group.orden[0].name, null)
}
output "orden_task_definition_arn" {
  value = try(aws_ecs_task_definition.orden[0].arn, null)
}
