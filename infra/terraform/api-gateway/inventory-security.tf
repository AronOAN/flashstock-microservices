# Política de ingreso por referencia a SG: API GW VPC Link -> ALB -> ECS -> RDS.
resource "aws_security_group" "vpc_link" {
  count       = var.enable_inventory_foundation ? 1 : 0
  name_prefix = "${local.inventory_prefix}-vpclink-"
  description = "FlashStock VPC Link egress to internal ALB port 80"
  vpc_id      = aws_vpc.flashstock[0].id
  egress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.flashstock_vpc_cidr]
  }
  tags = { Name = "${local.inventory_prefix}-vpclink" }
}
resource "aws_security_group" "alb" {
  count       = var.enable_inventory_foundation ? 1 : 0
  name_prefix = "${local.inventory_prefix}-alb-"
  description = "FlashStock internal ALB: VPC Link only"
  vpc_id      = aws_vpc.flashstock[0].id
  egress {
    from_port   = 8082
    to_port     = 8082
    protocol    = "tcp"
    cidr_blocks = [var.flashstock_vpc_cidr]
  }
  # Auth uses port 8081. Refer only to the Auth task SG; never allow broad ALB egress.
  dynamic "egress" {
    for_each = var.enable_auth_foundation ? [1] : []
    content {
      description     = "ALB to FlashStock Auth only"
      from_port       = 8081
      to_port         = 8081
      protocol        = "tcp"
      security_groups = [aws_security_group.auth_ecs[0].id]
    }
  }
  # Orden is private and uses TCP 8083 from the existing internal ALB only.
  dynamic "egress" {
    for_each = var.enable_orden_foundation ? [1] : []
    content {
      description     = "ALB to FlashStock Orden only"
      from_port       = 8083
      to_port         = 8083
      protocol        = "tcp"
      security_groups = [aws_security_group.orden_ecs[0].id]
    }
  }
  tags = { Name = "${local.inventory_prefix}-alb" }
}
resource "aws_security_group" "ecs" {
  count       = var.enable_inventory_foundation ? 1 : 0
  name_prefix = "${local.inventory_prefix}-inventory-"
  description = "FlashStock Inventory app, isolated ingress from ALB"
  vpc_id      = aws_vpc.flashstock[0].id
  egress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.flashstock_vpc_cidr]
  }
  # ECR/JWKS/Cognito/Secrets Manager/CloudWatch through NAT over TLS.
  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "${local.inventory_prefix}-inventory" }
}
resource "aws_security_group" "db" {
  count       = var.enable_inventory_foundation ? 1 : 0
  name_prefix = "${local.inventory_prefix}-db-"
  description = "FlashStock Inventory PostgreSQL, ECS only"
  vpc_id      = aws_vpc.flashstock[0].id
  egress      = []
  tags        = { Name = "${local.inventory_prefix}-db" }
}
resource "aws_vpc_security_group_ingress_rule" "alb_from_link" {
  count                        = var.enable_inventory_foundation ? 1 : 0
  security_group_id            = aws_security_group.alb[0].id
  referenced_security_group_id = aws_security_group.vpc_link[0].id
  ip_protocol                  = "tcp"
  from_port                    = 80
  to_port                      = 80
}
resource "aws_vpc_security_group_ingress_rule" "inventory_from_alb" {
  count                        = var.enable_inventory_foundation ? 1 : 0
  security_group_id            = aws_security_group.ecs[0].id
  referenced_security_group_id = aws_security_group.alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8082
  to_port                      = 8082
}
resource "aws_vpc_security_group_ingress_rule" "postgres_from_inventory" {
  count                        = var.enable_inventory_foundation ? 1 : 0
  security_group_id            = aws_security_group.db[0].id
  referenced_security_group_id = aws_security_group.ecs[0].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
