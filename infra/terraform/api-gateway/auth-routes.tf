# No wildcard API permissions. Only the Auth endpoints implemented in its AWS profile.
# Preserve the original request path through the existing VPC Link -> ALB listener.
locals {
  auth_routes = {
    "GET /api/auth/providers" = false
    "GET /api/maps/config"    = false
    "GET /api/auth/me"        = true
    "GET /api/admin/metrics"  = true
  }
}
resource "aws_apigatewayv2_integration" "auth" {
  count                  = var.enable_auth_routes ? 1 : 0
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
  depends_on = [aws_ecs_service.auth]
}
resource "aws_apigatewayv2_route" "auth" {
  for_each             = var.enable_auth_routes ? local.auth_routes : {}
  api_id               = aws_apigatewayv2_api.flashstock.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.auth[0].id}"
  authorization_type   = each.value ? "JWT" : "NONE"
  authorizer_id        = each.value ? aws_apigatewayv2_authorizer.cognito.id : null
  authorization_scopes = each.value ? ["aws.cognito.signin.user.admin"] : null
}
