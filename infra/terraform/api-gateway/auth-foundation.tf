# FlashStock Auth foundation. Reuses EXACTLY the existing VPC, ALB, RDS and ECS cluster.
# Resource names auth_ecs/auth_from_alb/auth_https/auth match the previously supplied draft.
# If that draft is already in state, review the plan carefully: do not import or recreate it.
resource "aws_security_group" "auth_ecs" {
  count       = var.enable_auth_foundation ? 1 : 0
  name        = "${local.inventory_prefix}-auth-ecs"
  description = "Security Group for FlashStock Auth ECS"
  vpc_id      = aws_vpc.flashstock[0].id
  # Egress is exclusively managed by the independent rules below.
  tags = {
    Project     = var.project_name
    Environment = var.environment
    Service     = "auth"
  }
}

resource "aws_vpc_security_group_ingress_rule" "auth_from_alb" {
  # Stable for_each key: existing FlashStock ALB security group ID.
  for_each = var.enable_auth_foundation ? toset([aws_security_group.alb[0].id]) : toset([])

  security_group_id            = aws_security_group.auth_ecs[0].id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 8081
  to_port                      = 8081
  description                  = "ALB to Auth on 8081 only"
}

resource "aws_vpc_security_group_egress_rule" "auth_https" {
  count             = var.enable_auth_foundation ? 1 : 0
  security_group_id = aws_security_group.auth_ecs[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "TLS outbound to JWKS and AWS endpoints via existing NAT"
}

resource "aws_vpc_security_group_egress_rule" "auth_postgres" {
  count                        = var.enable_auth_foundation ? 1 : 0
  security_group_id            = aws_security_group.auth_ecs[0].id
  referenced_security_group_id = aws_security_group.db[0].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "Auth outbound to the FlashStock private RDS SG"
}

resource "aws_vpc_security_group_ingress_rule" "postgres_from_auth" {
  count                        = var.enable_auth_foundation ? 1 : 0
  security_group_id            = aws_security_group.db[0].id
  referenced_security_group_id = aws_security_group.auth_ecs[0].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "Auth to the existing private FlashStock RDS"
}

resource "aws_lb_target_group" "auth" {
  count       = var.enable_auth_foundation ? 1 : 0
  name        = "${local.inventory_prefix}-auth"
  port        = 8081
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
  tags = {
    Project     = var.project_name
    Environment = var.environment
    Service     = "auth"
  }
}

resource "aws_cloudwatch_log_group" "auth" {
  count             = var.enable_auth_foundation ? 1 : 0
  name              = "/ecs/${local.inventory_prefix}/auth"
  retention_in_days = var.log_retention_days
}
