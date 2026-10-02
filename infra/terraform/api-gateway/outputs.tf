output "api_id" {
  description = "ID de la HTTP API administrada."
  value       = aws_apigatewayv2_api.flashstock.id
}

output "api_base_url" {
  description = "URL base HTTPS del stage default. Aun sin rutas de negocio."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "api_gateway_cloudwatch_log_group" {
  description = "Nombre del grupo de logs de la HTTP API."
  value       = aws_cloudwatch_log_group.api.name
}
