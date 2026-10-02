locals {
  api_name = "${var.project_name}-${var.environment}-http-api"
}

# Esta configuracion no crea IAM Roles, Lambda ni integraciones de negocio.
# Conserva las direcciones Terraform ya presentes en el estado anterior.
resource "aws_apigatewayv2_api" "flashstock" {
  name          = local.api_name
  protocol_type = "HTTP"
  description   = "Entrada HTTPS FlashStock. Integraciones de microservicios pendientes."
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/apigateway/${local.api_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.flashstock.id
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api.arn
    format = jsonencode({
      requestId               = "$context.requestId"
      requestTime             = "$context.requestTime"
      httpMethod              = "$context.httpMethod"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
      responseLength          = "$context.responseLength"
      responseLatency         = "$context.responseLatency"
      integrationErrorMessage = "$context.integrationErrorMessage"
    })
  }
}
