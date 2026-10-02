# Private Orden foundation. Shares ONLY resources managed by FlashStock's existing Terraform state.
# No new VPC/RDS/ALB/API Gateway/Cognito/IAM roles; does not touch solicitud-dev.
resource "aws_security_group" "orden_ecs" {
  count       = var.enable_orden_foundation ? 1 : 0
  name        = "${local.inventory_prefix}-orden-ecs"
  description = "Security Group for private FlashStock Orden Fargate"
  vpc_id      = aws_vpc.flashstock[0].id
  tags = {
    Project     = var.project_name
    Environment = var.environment
    Service     = "orden"
  }
}
resource "aws_vpc_security_group_ingress_rule" "orden_from_alb" {
  count                        = var.enable_orden_foundation ? 1 : 0
  security_group_id            = aws_security_group.orden_ecs[0].id
  referenced_security_group_id = aws_security_group.alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8083
  to_port                      = 8083
  description                  = "FlashStock ALB to Orden TCP 8083 only"
}
resource "aws_vpc_security_group_egress_rule" "orden_https" {
  count             = var.enable_orden_foundation ? 1 : 0
  security_group_id = aws_security_group.orden_ecs[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "Orden TLS outbound to JWKS/AWS via existing NAT"
}
resource "aws_vpc_security_group_egress_rule" "orden_postgres" {
  count                        = var.enable_orden_foundation ? 1 : 0
  security_group_id            = aws_security_group.orden_ecs[0].id
  referenced_security_group_id = aws_security_group.db[0].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "Orden to private FlashStock PostgreSQL"
}
resource "aws_vpc_security_group_ingress_rule" "postgres_from_orden" {
  count                        = var.enable_orden_foundation ? 1 : 0
  security_group_id            = aws_security_group.db[0].id
  referenced_security_group_id = aws_security_group.orden_ecs[0].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "Private FlashStock RDS ingress from Orden"
}
resource "aws_lb_target_group" "orden" {
  count       = var.enable_orden_foundation ? 1 : 0
  name        = "${local.inventory_prefix}-orden"
  port        = 8083
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
    Service     = "orden"
  }
}
resource "aws_cloudwatch_log_group" "orden" {
  count             = var.enable_orden_foundation ? 1 : 0
  name              = "/ecs/${local.inventory_prefix}/orden"
  retention_in_days = var.log_retention_days
}
