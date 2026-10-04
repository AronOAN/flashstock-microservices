# Auth container. Existing Academy execution role is reused; no IAM roles are created here.
# Apply ONLY after schema-inventory.sql has succeeded in the shared private database.
resource "aws_ecs_task_definition" "auth" {
  count                    = var.enable_auth_service ? 1 : 0
  family                   = "${local.inventory_prefix}-auth"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = var.ecs_task_execution_role_arn
  container_definitions = jsonencode([{
    name         = "auth"
    image        = var.auth_image_uri
    essential    = true
    portMappings = [{ containerPort = 8081, hostPort = 8081, protocol = "tcp" }]
    environment = [
      { name = "PORT", value = "8081" },
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "COGNITO_ISSUER_URL", value = "https://${aws_cognito_user_pool.flashstock.endpoint}" },
      { name = "COGNITO_APP_CLIENT_ID", value = aws_cognito_user_pool_client.frontend.id },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.inventory[0].address}:5432/${aws_db_instance.inventory[0].db_name}?sslmode=require" },
      { name = "SPRING_DATASOURCE_USERNAME", value = aws_db_instance.inventory[0].username },
      { name = "SPRING_DATASOURCE_DRIVER_CLASS_NAME", value = "org.postgresql.Driver" },
      { name = "FLASHSTOCK_TOKENS_ENABLED", value = tostring(var.enable_flashstock_issued_tokens) },
      { name = "CASHFLOW_UNIT_VALUE", value = "1" },
      { name = "FLASHSTOCK_SITE_URL", value = var.vercel_site_url },
      # Legacy email-based ADMIN escalation is explicitly disabled in application-aws.properties.
      { name = "ADMIN_EMAIL", value = "" },
      { name = "ADMIN_EMAIL_2", value = "" }
    ]
    secrets = concat(
      [{ name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.inventory[0].master_user_secret[0].secret_arn}:password::" }],
      var.enable_flashstock_issued_tokens ? [
        { name = "FLASHSTOCK_JWT_PRIVATE_KEY_DER_BASE64", valueFrom = var.flashstock_jwt_private_key_secret_arn },
        { name = "FLASHSTOCK_JWT_PUBLIC_KEY_DER_BASE64", valueFrom = var.flashstock_jwt_public_key_secret_arn },
        { name = "FLASHSTOCK_BFF_SHARED_SECRET", valueFrom = var.flashstock_bff_shared_secret_arn }
      ] : []
    )
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.auth[0].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "auth"
      }
    }
  }])
}

# Attach the new target group to the EXISTING private listener before starting the ECS service.
resource "aws_lb_listener_rule" "auth" {
  count        = var.enable_auth_service ? 1 : 0
  listener_arn = aws_lb_listener.http[0].arn
  priority     = 110 # Inventory retains priority 100.
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.auth[0].arn
  }
  condition {
    path_pattern {
      values = ["/api/auth", "/api/auth/*", "/api/admin", "/api/admin/*", "/api/maps/config"]
    }
  }
}

resource "aws_ecs_service" "auth" {
  count                              = var.enable_auth_service ? 1 : 0
  name                               = "${local.inventory_prefix}-auth"
  cluster                            = aws_ecs_cluster.flashstock[0].id
  task_definition                    = aws_ecs_task_definition.auth[0].arn
  desired_count                      = var.auth_desired_count
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  network_configuration {
    subnets          = aws_subnet.application[*].id
    security_groups  = [aws_security_group.auth_ecs[0].id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.auth[0].arn
    container_name   = "auth"
    container_port   = 8081
  }
  depends_on = [
    aws_lb_listener_rule.auth,
    aws_vpc_security_group_ingress_rule.auth_from_alb,
    aws_vpc_security_group_egress_rule.auth_https,
    aws_vpc_security_group_egress_rule.auth_postgres,
    aws_vpc_security_group_ingress_rule.postgres_from_auth,
    aws_route.app_nat
  ]
}
