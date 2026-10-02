# Register Orden on the existing FlashStock ECS cluster. It is NOT reachable from public API Gateway.
# AWS Academy existing ECS execution role; no new IAM Roles are created.
resource "aws_ecs_task_definition" "orden" {
  count                    = var.enable_orden_service ? 1 : 0
  family                   = "${local.inventory_prefix}-orden"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = var.ecs_task_execution_role_arn
  container_definitions = jsonencode([{
    name         = "orden"
    image        = var.orden_image_uri
    essential    = true
    portMappings = [{ containerPort = 8083, hostPort = 8083, protocol = "tcp" }]
    environment = [
      { name = "PORT", value = "8083" },
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "COGNITO_ISSUER_URL", value = "https://${aws_cognito_user_pool.flashstock.endpoint}" },
      { name = "COGNITO_APP_CLIENT_ID", value = aws_cognito_user_pool_client.frontend.id },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.inventory[0].address}:5432/${aws_db_instance.inventory[0].db_name}?sslmode=require" },
      { name = "SPRING_DATASOURCE_USERNAME", value = aws_db_instance.inventory[0].username },
      { name = "SPRING_DATASOURCE_DRIVER_CLASS_NAME", value = "org.postgresql.Driver" },
      { name = "CASHFLOW_UNIT_VALUE", value = "1" },
      { name = "ADMIN_EMAIL", value = "" },
      { name = "ADMIN_EMAIL_2", value = "" }
    ]
    secrets = [
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.inventory[0].master_user_secret[0].secret_arn}:password::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.orden[0].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "orden"
      }
    }
  }])
}
resource "aws_lb_listener_rule" "orden" {
  count        = var.enable_orden_service ? 1 : 0
  listener_arn = aws_lb_listener.http[0].arn
  priority     = 120 # Inventory=100, Auth=110. Internal only.
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.orden[0].arn
  }
  condition {
    path_pattern {
      values = ["/api/orders", "/api/orders/*", "/api/receipts", "/api/receipts/*"]
    }
  }
}
resource "aws_ecs_service" "orden" {
  count                              = var.enable_orden_service ? 1 : 0
  name                               = "${local.inventory_prefix}-orden"
  cluster                            = aws_ecs_cluster.flashstock[0].id
  task_definition                    = aws_ecs_task_definition.orden[0].arn
  desired_count                      = var.orden_desired_count
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  network_configuration {
    subnets          = aws_subnet.application[*].id
    security_groups  = [aws_security_group.orden_ecs[0].id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.orden[0].arn
    container_name   = "orden"
    container_port   = 8083
  }
  depends_on = [
    aws_lb_listener_rule.orden,
    aws_vpc_security_group_ingress_rule.orden_from_alb,
    aws_vpc_security_group_egress_rule.orden_https,
    aws_vpc_security_group_egress_rule.orden_postgres,
    aws_vpc_security_group_ingress_rule.postgres_from_orden,
    aws_route.app_nat
  ]
}
