#!/usr/bin/env python3
"""One-time surgical modification of the existing, inline-managed ALB SG egress.
Run from infra/terraform/api-gateway AFTER extracting ZIP. Idempotent; refuses unexpected structure.
Never mix standalone aws_vpc_security_group_egress_rule and inline egress for the same ALB SG.
"""
from pathlib import Path
from datetime import datetime, timezone
import shutil
import sys

path = Path('inventory-security.tf')
source = path.read_text(encoding='utf-8')
block = '''  # Orden is private and uses TCP 8083 from the existing internal ALB only.
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
'''
anchor = '''  tags = { Name = "${local.inventory_prefix}-alb" }
}
resource "aws_security_group" "ecs" {'''

if block.strip() in source:
    print('ALB Orden SG rule already installed; no changes.')
    sys.exit(0)
if source.count(anchor) != 1:
    sys.exit('BLOCKED: expected exact ALB-SG anchor absent or repeated; review inventory-security.tf manually.')
if 'aws_security_group.auth_ecs[0].id' not in source or 'from_port       = 8081' not in source:
    sys.exit('BLOCKED: Auth ALB egress does not match previously reviewed patch.')
if 'aws_security_group.orden_ecs[0].id' in source:
    sys.exit('BLOCKED: different Orden SG rule already exists; do not duplicate.')
backup = path.with_name('inventory-security.tf.pre-orden-' + datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S') + '.bak')
shutil.copy2(path, backup)
path.write_text(source.replace(anchor, block + anchor), encoding='utf-8')
print('Installed inline Orden ALB egress (TCP 8083 -> Orden ECS SG).')
print('Backup:', backup)
