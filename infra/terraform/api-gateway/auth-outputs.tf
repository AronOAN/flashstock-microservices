output "auth_ecs_security_group_id" {
  value = try(aws_security_group.auth_ecs[0].id, null)
}
output "auth_target_group_arn" {
  value = try(aws_lb_target_group.auth[0].arn, null)
}
output "auth_log_group" {
  value = try(aws_cloudwatch_log_group.auth[0].name, null)
}
output "auth_task_definition_arn" {
  value = try(aws_ecs_task_definition.auth[0].arn, null)
}
output "auth_api_me_url" {
  value = var.enable_auth_routes ? "${trimsuffix(aws_apigatewayv2_stage.default.invoke_url, "/")}/api/auth/me" : null
}
