# AWS Academy: NO IAM Roles are created. Existing execution role MUST trust ecs-tasks.amazonaws.com,
# allow ECR pull + CloudWatch write + Secrets Manager GetSecretValue, and be passable by current user.
resource "aws_ecs_task_definition" "inventory_migrate" {
  count                    = var.enable_inventory_migration_task ? 1 : 0
  family                   = "${local.inventory_prefix}-inventory-migrate"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = var.ecs_task_execution_role_arn
  container_definitions = jsonencode([{
    name      = "inventory-migrate"
    image     = "public.ecr.aws/docker/library/postgres:17-alpine"
    essential = true
    environment = [
      { name = "PGHOST", value = aws_db_instance.inventory[0].address },
      { name = "PGPORT", value = "5432" },
      { name = "PGDATABASE", value = aws_db_instance.inventory[0].db_name },
      { name = "PGUSER", value = aws_db_instance.inventory[0].username },
      { name = "PGSSLMODE", value = "require" },
      { name = "INVENTORY_SCHEMA_B64", value = filebase64("${path.module}/schema-inventory.sql") }
    ]
    secrets = [
      { name = "PGPASSWORD", valueFrom = "${aws_db_instance.inventory[0].master_user_secret[0].secret_arn}:password::" }
    ]
    command = ["sh", "-ec", "printf '%s' \"$INVENTORY_SCHEMA_B64\" | base64 -d | psql -X -v ON_ERROR_STOP=1 -f -"]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.inventory[0].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "migration"
      }
    }
  }])
}
resource "aws_ecs_task_definition" "inventory" {
  count                    = var.enable_inventory_service ? 1 : 0
  family                   = "${local.inventory_prefix}-inventory"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = var.ecs_task_execution_role_arn
  container_definitions = jsonencode([{
    name         = "inventory"
    image        = var.inventory_image_uri
    essential    = true
    portMappings = [{ containerPort = 8082, hostPort = 8082, protocol = "tcp" }]
    environment = [
      { name = "PORT", value = "8082" },
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "COGNITO_ISSUER_URL", value = "https://${aws_cognito_user_pool.flashstock.endpoint}" },
      { name = "COGNITO_APP_CLIENT_ID", value = aws_cognito_user_pool_client.frontend.id },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.inventory[0].address}:5432/${aws_db_instance.inventory[0].db_name}?sslmode=require" },
      { name = "SPRING_DATASOURCE_USERNAME", value = aws_db_instance.inventory[0].username },
      { name = "SPRING_DATASOURCE_DRIVER_CLASS_NAME", value = "org.postgresql.Driver" },
      { name = "CASHFLOW_UNIT_VALUE", value = "1" },
      { name = "ADMIN_EMAIL", value = var.inventory_admin_email },
      { name = "ADMIN_EMAIL_2", value = "" }
    ]
    secrets = [
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.inventory[0].master_user_secret[0].secret_arn}:password::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.inventory[0].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "inventory"
      }
    }
  }])
  # Conserva ddl-auto=validate del perfil aws. Ejecuta primero la migración one-off.
}
resource "aws_ecs_service" "inventory" {
  count                              = var.enable_inventory_service ? 1 : 0
  name                               = "${local.inventory_prefix}-inventory"
  cluster                            = aws_ecs_cluster.flashstock[0].id
  task_definition                    = aws_ecs_task_definition.inventory[0].arn
  desired_count                      = var.inventory_desired_count
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 200
  network_configuration {
    subnets          = aws_subnet.application[*].id
    security_groups  = [aws_security_group.ecs[0].id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.inventory[0].arn
    container_name   = "inventory"
    container_port   = 8082
  }
  depends_on = [
    aws_lb_listener_rule.inventory,
    aws_vpc_security_group_ingress_rule.inventory_from_alb,
    aws_vpc_security_group_ingress_rule.postgres_from_inventory,
    aws_route.app_nat
  ]
}
