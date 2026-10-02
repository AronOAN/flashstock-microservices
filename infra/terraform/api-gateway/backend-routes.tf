# SOLO INVENTORY. No deja rutas huérfanas de auth/orden/shipping ni utiliza solicitud-dev.
# Activa enable_inventory_routes cuando el target ECS aparezca healthy y schema migrate tenga exitCode=0.
locals {
  inventory_route_auth = {
    "GET /api/inventory"          = false
    "GET /api/inventory/{proxy+}" = false
    "POST /api/inventory"         = true
    "ANY /api/inventory/{proxy+}" = true
  }
}
resource "aws_apigatewayv2_integration" "inventory" {
  count                  = var.enable_inventory_routes ? 1 : 0
  api_id                 = aws_apigatewayv2_api.flashstock.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = aws_lb_listener.http[0].arn
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.flashstock[0].id
  payload_format_version = "1.0"
  timeout_milliseconds   = 29000
  request_parameters = {
    "overwrite:path" = "$request.path"
  }
  depends_on = [aws_ecs_service.inventory]
}
resource "aws_apigatewayv2_route" "inventory" {
  for_each             = var.enable_inventory_routes ? local.inventory_route_auth : {}
  api_id               = aws_apigatewayv2_api.flashstock.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.inventory[0].id}"
  authorization_type   = each.value ? "JWT" : "NONE"
  authorizer_id        = each.value ? aws_apigatewayv2_authorizer.cognito.id : null
  authorization_scopes = each.value ? ["openid"] : null
}
