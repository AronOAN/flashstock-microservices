# ADD to the EXACT SAME Terraform directory/state as your phase 1 + phase 2.
# Opt-in: AWS Academy denied iam:CreateRole. No aws_iam_role resources are declared here.
variable "enable_permissions_lambda" {
  description = "Enable only when the laboratory permits lambda:CreateFunction, iam:PassRole for an existing role, and lambda:AddPermission."
  type        = bool
  default     = false
}
variable "permission_lambda_execution_role_arn" {
  description = "ARN of an EXISTING role trusted by lambda.amazonaws.com; no roles are created by this module."
  type        = string
  default     = ""
}

resource "aws_lambda_function" "permissions" {
  count            = var.enable_permissions_lambda ? 1 : 0
  function_name    = "${var.project_name}-${var.environment}-permissions"
  filename         = "${path.module}/lambda/permission-check.zip"
  source_code_hash = filebase64sha256("${path.module}/lambda/permission-check.zip")
  role             = var.permission_lambda_execution_role_arn
  runtime          = "python3.12"
  handler          = "handler.lambda_handler"
  timeout          = 5
  memory_size      = 128
  lifecycle {
    precondition {
      condition     = can(regex("^arn:aws:iam::[0-9]{12}:role/.+", var.permission_lambda_execution_role_arn))
      error_message = "Set an authorized EXISTING Lambda execution role ARN; IAM roles are not created here."
    }
  }
  environment {
    variables = {
      COGNITO_ISSUER_URL    = "https://${aws_cognito_user_pool.flashstock.endpoint}"
      COGNITO_APP_CLIENT_ID = aws_cognito_user_pool_client.frontend.id
    }
  }
}

resource "aws_apigatewayv2_integration" "permissions" {
  count                  = var.enable_permissions_lambda ? 1 : 0
  api_id                 = aws_apigatewayv2_api.flashstock.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.permissions[0].invoke_arn
  payload_format_version = "2.0"
}

resource "aws_lambda_permission" "permissions_from_apigw" {
  count         = var.enable_permissions_lambda ? 1 : 0
  statement_id  = "FlashStockAllowApiGatewayPermissions"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.permissions[0].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.flashstock.execution_arn}/*/GET/api/auth/permissions"
}

# API Gateway validates signature/JWKS, issuer, expiration and route openid scope.
# Lambda reads ONLY the validated requestContext.authorizer.jwt.claims.
resource "aws_apigatewayv2_route" "permissions" {
  count                = var.enable_permissions_lambda ? 1 : 0
  api_id               = aws_apigatewayv2_api.flashstock.id
  route_key            = "GET /api/auth/permissions"
  target               = "integrations/${aws_apigatewayv2_integration.permissions[0].id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = ["openid"]
  depends_on           = [aws_lambda_permission.permissions_from_apigw]
}

output "permissions_endpoint" {
  description = "Exists only when the permissions Lambda was authorized and deployed."
  value       = var.enable_permissions_lambda ? "${trimsuffix(aws_apigatewayv2_stage.default.invoke_url, "/")}/api/auth/permissions" : null
}
