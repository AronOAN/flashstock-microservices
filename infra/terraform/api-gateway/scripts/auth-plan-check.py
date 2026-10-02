#!/usr/bin/env python3
"""Fail-closed, stage-specific guard for the FlashStock Auth rollout.
Does NOT call terraform apply. Inspect `terraform show -no-color` as well.
Usage: python scripts/auth-plan-check.py foundation|service|routes FILE.tfplan [FULL_AUTH_IMAGE_URI]
"""
import json
import re
import subprocess
import sys

BASE = {
    "foundation": {
        'aws_security_group.auth_ecs[0]',
        'aws_vpc_security_group_egress_rule.auth_https[0]',
        'aws_vpc_security_group_egress_rule.auth_postgres[0]',
        'aws_vpc_security_group_ingress_rule.postgres_from_auth[0]',
        'aws_lb_target_group.auth[0]',
        'aws_cloudwatch_log_group.auth[0]',
    },
    "service": {
        'aws_lb_listener_rule.auth[0]',
        'aws_ecs_task_definition.auth[0]',
        'aws_ecs_service.auth[0]',
    },
    "routes": {
        'aws_apigatewayv2_integration.auth[0]',
    }
}
ROUTES = {
    'GET /api/auth/providers', 'GET /api/maps/config',
    'GET /api/auth/me', 'GET /api/admin/metrics'
}

def fail(msg):
    raise ValueError(msg)

def check_sg(change):
    if change['actions'] != ['update']:
        fail('ALB security group must ONLY update in place')
    old, new = change['before'], change['after']
    for key in old.keys() | new.keys():
        if key != 'egress' and old.get(key) != new.get(key):
            fail(f'Unexpected ALB SG field changed: {key}')
    old_eg = old.get('egress') or []
    new_eg = new.get('egress') or []
    def port(rule, value):
        return rule.get('from_port') == value and rule.get('to_port') == value and rule.get('protocol') == 'tcp'
    if not any(port(r, 8082) for r in old_eg):
        fail('Existing Inventory ALB egress (8082) missing')
    if len(new_eg) != len(old_eg) + 1:
        fail('ALB egress must add exactly one rule, without removing Inventory')
    def normalize(xs):
        return sorted(json.dumps(x, sort_keys=True) for x in xs)
    extras = new_eg[:]
    for item in old_eg:
        try: extras.remove(item)
        except ValueError: fail('ALB egress removes/changes existing rule')
    if len(extras) != 1 or not port(extras[0],8081):
        fail('Only ALB -> Auth TCP 8081 is allowed')
    # Terraform may mark the newly created Auth SG reference as unknown until apply.
    # If known, ensure it is a SG reference, not a blanket CIDR.
    extra=extras[0]
    if extra.get('cidr_blocks') or extra.get('ipv6_cidr_blocks') or extra.get('prefix_list_ids'):
        fail('ALB Auth egress must reference a security group, not a CIDR')

try:
    if len(sys.argv) not in (3,4):
        fail(__doc__.splitlines()[-1])
    stage, plan_path = sys.argv[1:3]
    if stage not in BASE:
        fail('Unknown stage')
    expected_image = sys.argv[3] if len(sys.argv) == 4 else None
    if stage == 'service' and not expected_image:
        fail('Service stage requires exact ECR Auth image URI as third argument')
    obj=json.loads(subprocess.check_output(['terraform','show','-json',plan_path],text=True))
    changed=[r for r in obj.get('resource_changes',[]) if r.get('mode')=='managed' and r['change']['actions']!=['no-op']]
    if not changed:
        fail('No resource changes; check whether this stage has already been applied')
    for item in changed:
        addr=item['address']; action=item['change']['actions']
        allowed = addr in BASE[stage]
        if stage == 'foundation':
            allowed |= bool(re.fullmatch(r'aws_vpc_security_group_ingress_rule\.auth_from_alb\["sg-[a-f0-9]+"\]',addr))
            allowed |= addr == 'aws_security_group.alb[0]'
        if stage == 'routes':
            match=re.fullmatch(r'aws_apigatewayv2_route\.auth\["(.+)"\]',addr)
            allowed |= bool(match and match.group(1) in ROUTES)
        if not allowed:
            fail(f'Unexpected resource changed: {addr}, actions={action}')
        if stage == 'foundation' and addr == 'aws_security_group.alb[0]':
            check_sg(item['change'])
        elif action != ['create']:
            fail(f'Unexpected action on {addr}: {action}. Existing resource may need reconciliation.')
        if stage == 'service' and addr == 'aws_ecs_task_definition.auth[0]':
            defs=item['change']['after']['container_definitions']
            defs=json.loads(defs) if isinstance(defs,str) else defs
            if len(defs)!=1 or defs[0]['name']!='auth' or defs[0]['image'] != expected_image:
                fail('Task definition image is NOT the exact expected Auth ECR image')
    if any('delete' in r['change']['actions'] for r in changed):
        fail('Deletion / replacement is never allowed by this rollout guard')
    print('AUTH PLAN VALIDATION PASSED:',stage)
    print('Allowed changes:')
    for item in changed: print(' ',item['address'],item['change']['actions'])
    print('Read terraform show -no-color before applying this same saved plan.')
except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as err:
    print('AUTH PLAN BLOCKED:',err,file=sys.stderr)
    sys.exit(1)
