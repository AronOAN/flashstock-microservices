# Recursos FlashStock NUEVOS, en VPC aislada. Nunca se importan ni referencian los de solicitud-dev.
locals { inventory_prefix = "${var.project_name}-${var.environment}" }
data "aws_availability_zones" "available" { state = "available" }

resource "aws_vpc" "flashstock" {
  count                = var.enable_inventory_foundation ? 1 : 0
  cidr_block           = var.flashstock_vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = "${local.inventory_prefix}-vpc" }
}
resource "aws_internet_gateway" "flashstock" {
  count  = var.enable_inventory_foundation ? 1 : 0
  vpc_id = aws_vpc.flashstock[0].id
  tags   = { Name = "${local.inventory_prefix}-igw" }
}
resource "aws_subnet" "public" {
  count                   = var.enable_inventory_foundation ? 2 : 0
  vpc_id                  = aws_vpc.flashstock[0].id
  cidr_block              = cidrsubnet(var.flashstock_vpc_cidr, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.inventory_prefix}-public-${count.index + 1}" }
}
resource "aws_subnet" "application" {
  count                   = var.enable_inventory_foundation ? 2 : 0
  vpc_id                  = aws_vpc.flashstock[0].id
  cidr_block              = cidrsubnet(var.flashstock_vpc_cidr, 8, count.index + 10)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.inventory_prefix}-app-private-${count.index + 1}" }
}
resource "aws_subnet" "database" {
  count                   = var.enable_inventory_foundation ? 2 : 0
  vpc_id                  = aws_vpc.flashstock[0].id
  cidr_block              = cidrsubnet(var.flashstock_vpc_cidr, 8, count.index + 20)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.inventory_prefix}-db-isolated-${count.index + 1}" }
}
resource "aws_route_table" "public" {
  count  = var.enable_inventory_foundation ? 1 : 0
  vpc_id = aws_vpc.flashstock[0].id
  tags   = { Name = "${local.inventory_prefix}-public" }
}
resource "aws_route" "public_internet" {
  count                  = var.enable_inventory_foundation ? 1 : 0
  route_table_id         = aws_route_table.public[0].id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.flashstock[0].id
}
resource "aws_route_table_association" "public" {
  count          = var.enable_inventory_foundation ? 2 : 0
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public[0].id
}
resource "aws_eip" "nat" {
  count  = var.enable_inventory_foundation ? 1 : 0
  domain = "vpc"
  tags   = { Name = "${local.inventory_prefix}-nat" }
}
resource "aws_nat_gateway" "flashstock" {
  count         = var.enable_inventory_foundation ? 1 : 0
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id
  depends_on    = [aws_route.public_internet]
  tags          = { Name = "${local.inventory_prefix}-nat" }
}
resource "aws_route_table" "application" {
  count  = var.enable_inventory_foundation ? 1 : 0
  vpc_id = aws_vpc.flashstock[0].id
  tags   = { Name = "${local.inventory_prefix}-app-private" }
}
resource "aws_route" "app_nat" {
  count                  = var.enable_inventory_foundation ? 1 : 0
  route_table_id         = aws_route_table.application[0].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.flashstock[0].id
}
resource "aws_route_table_association" "application" {
  count          = var.enable_inventory_foundation ? 2 : 0
  subnet_id      = aws_subnet.application[count.index].id
  route_table_id = aws_route_table.application[0].id
}
resource "aws_route_table" "database" {
  count  = var.enable_inventory_foundation ? 1 : 0
  vpc_id = aws_vpc.flashstock[0].id
  # SIN default route hacia internet / NAT.
  tags = { Name = "${local.inventory_prefix}-db-isolated" }
}
resource "aws_route_table_association" "database" {
  count          = var.enable_inventory_foundation ? 2 : 0
  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database[0].id
}
