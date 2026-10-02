#!/usr/bin/env python3
"""Fail-closed stage-specific check for FlashStock Orden saved Terraform plan.
Usage: python scripts/orden-plan-check.py foundation|service FILE.tfplan [FULL_ECR_IMAGE_URI]
Run `terraform show -no-color FILE.tfplan` manually and apply EXACTLY that reviewed saved plan.
"""
import json
import subprocess
import sys

BASE = {
    'foundation': {
        'aws_security_group.orden_ecs[0]',
        'aws_vpc_security_group_ingress_rule.orden_from_alb[0]',
        'aws_vpc_security_group_egress_rule.orden_https[0]',
        'aws_vpc_security_group_egress_rule.orden_postgres[0]',
        'aws_vpc_security_group_ingress_rule.postgres_from_orden[0]',
        'aws_lb_target_group.orden[0]',
        'aws_cloudwatch_log_group.orden[0]',
        'aws_security_group.alb[0]',
    },
    'service': {
        'aws_ecs_task_definition.orden[0]',
        'aws_lb_listener_rule.orden[0]',
        'aws_ecs_service.orden[0]',
    }
}
OUTPUTS = {
    'foundation': {'orden_ecs_security_group_id', 'orden_target_group_arn', 'orden_log_group'},
    'service': {'orden_task_definition_arn'},
}

def fail(message):
    raise ValueError(message)

def norm(xs):
    return sorted(json.dumps(x, sort_keys=True) for x in xs)

def same_other_attributes(c):
    before, after = c['before'], c['after']
    for key in set(before) | set(after):
        if key != 'egress' and before.get(key) != after.get(key):
            fail(f'Existing ALB SG changed unexpected field: {key}')

def inspect_alb(change):
    if change['actions'] != ['update']:
        fail('ALB SG must update in place; replacement is forbidden')
    same_other_attributes(change)
    old = change['before'].get('egress') or []
    new = change['after'].get('egress') or []
    if len(new) != len(old) + 1:
        fail('ALB egress must add exactly one rule and preserve all old rules')
    leftovers = list(new)
    for old_rule in old:
        try:
            leftovers.remove(old_rule)
        except ValueError:
            fail('An existing Inventory/Auth ALB egress was removed or changed')
    if len(leftovers) != 1:
        fail('Unexpected ALB egress delta')
    rule = leftovers[0]
    if (rule.get('from_port'), rule.get('to_port'), rule.get('protocol')) != (8083, 8083, 'tcp'):
        fail('Only TCP 8083 added ALB egress is allowed')
    if rule.get('cidr_blocks') or rule.get('ipv6_cidr_blocks') or rule.get('prefix_list_ids'):
        fail('Orden ALB egress must reference its task SG, not an IP range')
    ids = rule.get('security_groups') or []
    if len(ids) > 1:
        fail('Only one Orden SG destination is allowed')
    sg = change.get('after_unknown', {}).get('egress')
    # The SG ID can be unknown in this same plan because it is being created.
    if ids and not (ids[0].startswith('sg-') and len(ids[0]) >= 5):
        fail('Invalid destination SG reference')
    if not any((r.get('from_port'),r.get('to_port'))==(8081,8081) for r in old):
        fail('Auth 8081 egress not in the previous ALB state')
    if not any((r.get('from_port'),r.get('to_port'))==(8082,8082) for r in old):
        fail('Inventory 8082 egress not in the previous ALB state')

def get_containers(raw):
    return json.loads(raw) if isinstance(raw, str) else raw

def check_service(changes, expected):
    if not expected or not expected.startswith('823102413975.dkr.ecr.us-east-1.amazonaws.com/flashstock-dev-orden:'):
        fail('Provide the exact FlashStock Orden image URI as fourth argument')
    task = changes['aws_ecs_task_definition.orden[0]']['change']
    containers = get_containers(task['after']['container_definitions'])
    if len(containers) != 1:
        fail('Expected one Orden container only')
    container = containers[0]
    if container.get('name') != 'orden' or container.get('image') != expected:
        fail('Container name or ECR image differs from expected')
    if len(container.get('portMappings', [])) != 1 or container['portMappings'][0]['containerPort'] != 8083:
        fail('Wrong Orden port mapping')
    env = {x['name']: x['value'] for x in container.get('environment', [])}
    if env.get('SPRING_PROFILES_ACTIVE') != 'aws' or env.get('PORT') != '8083':
        fail('Missing AWS profile / PORT')
    if env.get('COGNITO_APP_CLIENT_ID') != '1v1gmjscc2qtpuerhs63v6taob':
        fail('Unexpected Cognito App Client')
    secret_names = {x['name'] for x in container.get('secrets', [])}
    if secret_names != {'SPRING_DATASOURCE_PASSWORD'}:
        fail('Unexpected secret injection configuration')
    service = changes['aws_ecs_service.orden[0]']['change']['after']
    if service.get('name') != 'flashstock-dev-orden' or service.get('assign_public_ip'):
        fail('Unexpected ECS service parameters')
    # Public assignment is contained under network_configuration in AWS provider.
    nets = service.get('network_configuration') or []
    if len(nets) != 1 or nets[0].get('assign_public_ip') is not False:
        fail('Orden must use private Fargate subnets with assign_public_ip=false')
    listener = changes['aws_lb_listener_rule.orden[0]']['change']['after']
    if listener.get('priority') != 120:
        fail('Incorrect private listener priority')

try:
    if len(sys.argv) not in (3, 4):
        fail(__doc__.splitlines()[1])
    stage, path = sys.argv[1:3]
    if stage not in BASE:
        fail('Choose foundation or service')
    expected_uri = sys.argv[3] if len(sys.argv) == 4 else None
    if stage == 'service' and not expected_uri:
        fail('service stage requires exact ECR image URI')
    obj = json.loads(subprocess.check_output(['terraform', 'show', '-json', path], text=True))
    changes = {r['address']:r for r in obj.get('resource_changes',[])
               if r.get('mode') == 'managed' and r['change']['actions'] != ['no-op']}
    if set(changes) != BASE[stage]:
        fail(f'Expected {sorted(BASE[stage])}; actual changes: {sorted(changes)}')
    for address, item in changes.items():
        c = item['change']
        if stage == 'foundation' and address == 'aws_security_group.alb[0]':
            inspect_alb(c)
        elif c['actions'] != ['create']:
            fail(f'Only new Orden resources allowed: {address} {c["actions"]}')
        if 'delete' in c['actions']:
            fail(f'Delete/replacement forbidden: {address}')
    outputs = {name for name, change in obj.get('output_changes',{}).items()
               if change.get('actions') != ['no-op']}
    if not outputs.issubset(OUTPUTS[stage]):
        fail(f'Unexpected Terraform outputs changed: {sorted(outputs)}')
    if stage == 'service':
        check_service(changes, expected_uri)
    print('ORDEN PLAN VALIDATION PASSED:', stage)
    for address, item in changes.items():
        print(' ', address, item['change']['actions'])
    print('Inspect the human-readable saved plan before applying exactly this file.')
except (ValueError, KeyError, TypeError, IndexError, OSError, subprocess.CalledProcessError) as err:
    print('ORDEN PLAN BLOCKED:', err, file=sys.stderr)
    sys.exit(1)
