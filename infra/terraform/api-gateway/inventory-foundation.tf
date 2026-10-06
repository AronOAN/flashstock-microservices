# Costos: ALB + NAT + RDS + ECS + VPC Link. Verifica presupuesto de Academy.
resource "aws_ecr_repository" "inventory" {
  count                = var.enable_inventory_foundation ? 1 : 0
  name                 = "${local.inventory_prefix}-inventory"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false
  image_scanning_configuration { scan_on_push = true }
}
resource "aws_ecs_cluster" "flashstock" {
  count = var.enable_inventory_foundation ? 1 : 0
  name  = local.inventory_prefix
}
resource "aws_cloudwatch_log_group" "inventory" {
  count             = var.enable_inventory_foundation ? 1 : 0
  name              = "/ecs/${local.inventory_prefix}/inventory"
  retention_in_days = var.log_retention_days
}
resource "aws_db_subnet_group" "inventory" {
  count      = var.enable_inventory_foundation ? 1 : 0
  name       = "${local.inventory_prefix}-inventory"
  subnet_ids = aws_subnet.database[*].id
  tags       = { Name = "${local.inventory_prefix}-db-private" }
}
resource "aws_db_instance" "inventory" {
  count                       = var.enable_inventory_foundation ? 1 : 0
  identifier                  = "${local.inventory_prefix}-inventory"
  engine                      = "postgres"
  engine_version              = var.inventory_db_engine_version
  instance_class              = var.inventory_db_instance_class
  allocated_storage           = 20
  max_allocated_storage       = 100
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "flashstock_inventory"
  username                    = "flashstock_admin"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.inventory[0].name
  vpc_security_group_ids      = [aws_security_group.db[0].id]
  publicly_accessible         = false
  multi_az                    = false
  backup_retention_period     = 1
  deletion_protection         = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "${local.inventory_prefix}-inventory-final"
  apply_immediately           = false
  depends_on                  = [aws_vpc_security_group_ingress_rule.postgres_from_inventory]
  lifecycle { prevent_destroy = true }
  tags = { Name = "${local.inventory_prefix}-inventory-private-db" }
}
resource "aws_lb" "flashstock" {
  count                      = var.enable_inventory_foundation ? 1 : 0
  name                       = "${local.inventory_prefix}-internal"
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb[0].id]
  subnets                    = aws_subnet.application[*].id
  drop_invalid_header_fields = true
  tags                       = { Name = "${local.inventory_prefix}-internal" }
}
resource "aws_lb_target_group" "inventory" {
  count       = var.enable_inventory_foundation ? 1 : 0
  name        = "${local.inventory_prefix}-inventory"
  port        = 8082
  protocol    = "HTTP"
  vpc_id      = aws_vpc.flashstock[0].id
  target_type = "ip"
  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    path                = "/actuator/health"
    matcher             = "200"
  }
}
resource "aws_lb_listener" "http" {
  count             = var.enable_inventory_foundation ? 1 : 0
  load_balancer_arn = aws_lb.flashstock[0].arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Route not configured"
      status_code  = "404"
    }
  }
}
resource "aws_lb_listener_rule" "inventory" {
  count        = var.enable_inventory_foundation ? 1 : 0
  listener_arn = aws_lb_listener.http[0].arn
  priority     = 100
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.inventory[0].arn
  }
  condition {
    path_pattern {
      values = ["/api/catalog", "/api/inventory", "/api/inventory/*", "/api/cart", "/api/cart/*"]
    }
  }
}
resource "aws_apigatewayv2_vpc_link" "flashstock" {
  count              = var.enable_inventory_foundation ? 1 : 0
  name               = "${local.inventory_prefix}-vpclink"
  security_group_ids = [aws_security_group.vpc_link[0].id]
  subnet_ids         = aws_subnet.application[*].id
}
