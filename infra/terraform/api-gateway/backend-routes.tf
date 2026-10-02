# Optional next phase: DO NOT enable without provisioned and reachable ECS services,
# a private ALB listener that routes paths to corresponding target groups, and an
# ACTIVE API Gateway HTTP API VPC Link in the same AWS account/region.
# This file does not create ECS, load balancer, databases, a VPC Link, or IAM roles.
variable "enable_business_routes" {
  type        = bool
  default     = false
  description = "Enable only AFTER you have deployed four backends and tested private ALB targets."
}
variable "existing_vpc_link_id" {
  type    = string
  default = ""
}
variable "existing_alb_listener_arn" {
  type    = string
  default = ""
}

locals {
  # True = JWT required by API Gateway; ROLE_ADMIN/ROLE_USER must be checked by Spring.
  business_routes = {
    "GET /api/inventory"          = false
    "GET /api/inventory/{proxy+}" = false
    "POST /api/inventory"         = true
    "ANY /api/inventory/{proxy+}" = true
    "GET /api/auth/providers"     = false
    "GET /api/auth/me"            = true
    "GET /api/maps/config"        = false
    "ANY /api/admin/{proxy+}"     = true
    "GET /api/orders"             = true
    "POST /api/orders"            = true
    "ANY /api/orders/{proxy+}"    = true
    "GET /api/receipts/{proxy+}"  = true
    "ANY /api/receipts/{proxy+}"  = true
    "GET /api/shipping"           = true
    "POST /api/shipping"          = true
    "ANY /api/shipping/{proxy+}"  = true
  }
}

resource "aws_apigatewayv2_integration" "business_alb" {
  count                  = var.enable_business_routes ? 1 : 0
  api_id                 = aws_apigatewayv2_api.flashstock.id
  integration_type       = "HTTP_PROXY"
  integration_uri        = var.existing_alb_listener_arn
  connection_type        = "VPC_LINK"
  connection_id          = var.existing_vpc_link_id
  payload_format_version = "1.0"
  lifecycle {
    precondition {
      condition     = length(trimspace(var.existing_vpc_link_id)) > 0
      error_message = "An existing ACTIVE VPC Link ID is required when enabling business routes."
    }
    precondition {
      condition     = can(regex("^arn:aws:elasticloadbalancing:[a-z0-9-]+:[0-9]{12}:listener/", var.existing_alb_listener_arn))
      error_message = "An existing internal ALB listener ARN is required."
    }
  }
  request_parameters = {
    "overwrite:path" = "$request.path"
  }
}

resource "aws_apigatewayv2_route" "business" {
  for_each             = var.enable_business_routes ? local.business_routes : {}
  api_id               = aws_apigatewayv2_api.flashstock.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.business_alb[0].id}"
  authorization_type   = each.value ? "JWT" : "NONE"
  authorizer_id        = each.value ? aws_apigatewayv2_authorizer.cognito.id : null
  authorization_scopes = each.value ? ["openid"] : null
}
