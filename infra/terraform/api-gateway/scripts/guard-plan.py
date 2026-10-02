#!/usr/bin/env python3
"""Protección antes del apply: no destruye/reemplaza, ni permite colisiones de recursos FlashStock.
No importa automáticamente recursos sin ownership verificado, no comparte recursos de otro proyecto.
"""
import json
import os
import subprocess
import sys

EXPECTED_API = 'ly23z3a5hb'
EXPECTED_POOL = 'us-east-1_jkrNUk7yQ'
EXPECTED_DOMAIN = 'flashstock-dev-aron'
EXPECTED_ACCOUNT = os.getenv('FLASHSTOCK_EXPECTED_ACCOUNT', '823102413975')

class AWSFailure(RuntimeError):
    pass

class AWS:
    def __init__(self, region): self.region = region
    def get(self, *args, missing=()):
        cmd = ['aws', *args, '--region', self.region, '--output', 'json', '--no-cli-pager']
        p = subprocess.run(cmd, text=True, capture_output=True)
        if p.returncode:
            if any(code in p.stderr for code in missing): return {}
            raise AWSFailure('No fue posible verificar el recurso: ' + ' '.join(cmd[:3]) + '\n' + p.stderr.strip())
        if not p.stdout.strip(): return {}
        return json.loads(p.stdout)


def matching_name(items, name, field='Name'):
    return [v for v in items if v.get(field) == name and v.get('Status') not in ('INACTIVE', 'DELETED')]

def by_tag(items, name):
    return [x for x in items if next((t.get('Value') for t in x.get('Tags',[]) if t.get('Key')=='Name'), None) == name]

def unique(items, key, address):
    if not items: return None
    if len(items) != 1: raise AWSFailure(f'{address}: hay {len(items)} coincidencias AWS para {key}; revisión manual obligatoria')
    return items[0].get(key) or str(items[0])


def get_existing(aws, resource):
    """Solo detecta identidades únicas verificables. No se usa para hacer cambios en AWS."""
    typ, addr, v = resource['type'], resource['address'], resource.get('change',{}).get('after') or {}
    name = v.get('name') or v.get('identifier') or (v.get('tags') or {}).get('Name')
    if typ == 'aws_cognito_user_pool_domain':
        raise AWSFailure('El dominio Cognito existente NO se crea de nuevo: elimina el .tf heredado e instala el paquete limpio.')
    if typ == 'aws_apigatewayv2_api':
        r = aws.get('apigatewayv2','get-api','--api-id',EXPECTED_API, missing=('NotFoundException',))
        if r and r.get('Name') != 'flashstock-dev-http-api': raise AWSFailure('El API ID esperado pertenece a otra API.')
        return r.get('ApiId')
    if typ == 'aws_apigatewayv2_stage':
        r = aws.get('apigatewayv2','get-stage','--api-id',EXPECTED_API,'--stage-name','$default', missing=('NotFoundException',))
        return r.get('StageName')
    if typ == 'aws_apigatewayv2_authorizer':
        r = aws.get('apigatewayv2','get-authorizers','--api-id',EXPECTED_API)
        a=matching_name(r.get('Items',[]), 'flashstock-dev-cognito-jwt')
        return unique(a,'AuthorizerId',addr)
    if typ == 'aws_apigatewayv2_route':
        r = aws.get('apigatewayv2','get-routes','--api-id',EXPECTED_API)
        a=[x for x in r.get('Items',[]) if x.get('RouteKey') == v.get('route_key')]
        return unique(a,'RouteId',addr)
    if typ == 'aws_apigatewayv2_integration':
        # No se identifica de modo fiable la integración a partir de un ARN aún desconocido.
        # Se impide importarla por conjetura; si las rutas ya existen lo detecta aws_apigatewayv2_route.
        r=aws.get('apigatewayv2','get-integrations','--api-id',EXPECTED_API)
        a=[x for x in r.get('Items',[]) if x.get('IntegrationType')=='HTTP_PROXY' and x.get('ConnectionType')=='VPC_LINK' and x.get('IntegrationUri') == v.get('integration_uri') and v.get('integration_uri')]
        return unique(a,'IntegrationId',addr)
    if typ == 'aws_cognito_user_pool':
        r=aws.get('cognito-idp','describe-user-pool','--user-pool-id',EXPECTED_POOL, missing=('ResourceNotFoundException',))
        if r.get('UserPool') and r['UserPool'].get('Name') != 'flashstock-dev-users': raise AWSFailure('Pool esperado no pertenece a FlashStock')
        return (r.get('UserPool') or {}).get('Id')
    if typ == 'aws_cognito_user_pool_client':
        r=aws.get('cognito-idp','list-user-pool-clients','--user-pool-id',EXPECTED_POOL,'--max-results','60')
        a=[x for x in r.get('UserPoolClients',[]) if x.get('ClientName') == 'flashstock-dev-frontend']
        return unique(a,'ClientId',addr)
    if typ == 'aws_cognito_user_group':
        group=v.get('name')
        r=aws.get('cognito-idp','get-group','--user-pool-id',EXPECTED_POOL,'--group-name',group,missing=('ResourceNotFoundException',))
        return (r.get('Group') or {}).get('GroupName')
    if typ == 'aws_cloudwatch_log_group':
        r=aws.get('logs','describe-log-groups','--log-group-name-prefix',name)
        a=[x for x in r.get('logGroups',[]) if x.get('logGroupName')==name]
        return unique(a,'arn',addr)
    if typ == 'aws_ecr_repository':
        r=aws.get('ecr','describe-repositories','--repository-names',name,missing=('RepositoryNotFoundException',))
        return unique(r.get('repositories',[]),'repositoryArn',addr)
    if typ == 'aws_ecs_cluster':
        r=aws.get('ecs','describe-clusters','--clusters',name)
        a=[x for x in r.get('clusters',[]) if x.get('clusterName')==name and x.get('status')=='ACTIVE']
        return unique(a,'clusterArn',addr)
    if typ == 'aws_ecs_service':
        r=aws.get('ecs','describe-services','--cluster','flashstock-dev','--services','flashstock-dev-inventory')
        a=[x for x in r.get('services',[]) if x.get('serviceName')=='flashstock-dev-inventory' and x.get('status')=='ACTIVE']
        return unique(a,'serviceArn',addr)
    if typ == 'aws_ecs_task_definition':
        # ECS admite registrar nuevas revisiones con una misma family: no es colisión.
        return None
    if typ == 'aws_db_instance':
        r=aws.get('rds','describe-db-instances','--db-instance-identifier',name,missing=('DBInstanceNotFound',))
        return unique(r.get('DBInstances',[]),'DBInstanceArn',addr)
    if typ == 'aws_db_subnet_group':
        r=aws.get('rds','describe-db-subnet-groups','--db-subnet-group-name',name,missing=('DBSubnetGroupNotFoundFault',))
        return unique(r.get('DBSubnetGroups',[]),'DBSubnetGroupArn',addr)
    if typ == 'aws_lb':
        r=aws.get('elbv2','describe-load-balancers','--names',name,missing=('LoadBalancerNotFound',))
        return unique(r.get('LoadBalancers',[]),'LoadBalancerArn',addr)
    if typ == 'aws_lb_target_group':
        r=aws.get('elbv2','describe-target-groups','--names',name,missing=('TargetGroupNotFound',))
        return unique(r.get('TargetGroups',[]),'TargetGroupArn',addr)
    if typ in ('aws_lb_listener','aws_lb_listener_rule'):
        # El ALB padre ha de existir para poder tener listener/reglas previas.
        alb=aws.get('elbv2','describe-load-balancers','--names','flashstock-dev-internal',missing=('LoadBalancerNotFound',))
        arns=[x['LoadBalancerArn'] for x in alb.get('LoadBalancers',[])]
        if not arns: return None
        r=aws.get('elbv2','describe-listeners','--load-balancer-arn',arns[0])
        listeners=[x for x in r.get('Listeners',[]) if x.get('Port')==80 and x.get('Protocol')=='HTTP']
        if typ=='aws_lb_listener': return unique(listeners,'ListenerArn',addr)
        if not listeners:return None
        rules=aws.get('elbv2','describe-rules','--listener-arn',listeners[0]['ListenerArn'])
        a=[x for x in rules.get('Rules',[]) if x.get('Priority')=='100']
        return unique(a,'RuleArn',addr)
    if typ == 'aws_apigatewayv2_vpc_link':
        r=aws.get('apigatewayv2','get-vpc-links')
        return unique(matching_name(r.get('Items',[]),name),'VpcLinkId',addr)
    ec2_config={
        'aws_vpc': ('describe-vpcs','Vpcs','VpcId'),
        'aws_internet_gateway': ('describe-internet-gateways','InternetGateways','InternetGatewayId'),
        'aws_subnet': ('describe-subnets','Subnets','SubnetId'),
        'aws_route_table': ('describe-route-tables','RouteTables','RouteTableId'),
        'aws_eip': ('describe-addresses','Addresses','AllocationId'),
        'aws_nat_gateway': ('describe-nat-gateways','NatGateways','NatGatewayId'),
        'aws_security_group': ('describe-security-groups','SecurityGroups','GroupId'),
    }
    if typ in ec2_config:
        op,field,idfield=ec2_config[typ]
        # describe-nat-gateways usa --filter (singular); los demás EC2 usan --filters.
        filter_flag = '--filter' if typ == 'aws_nat_gateway' else '--filters'
        r=aws.get('ec2',op,filter_flag,f'Name=tag:Name,Values={name}')
        a=by_tag(r.get(field,[]),name)
        if typ=='aws_nat_gateway': a=[x for x in a if x.get('State')!='deleted']
        return unique(a,idfield,addr)
    if typ == 'aws_route':
        table=v.get('route_table_id')
        if not table:return None # El padre nuevo aún no existe en AWS.
        r=aws.get('ec2','describe-route-tables','--route-table-ids',table)
        matches=[x for t in r.get('RouteTables',[]) for x in t.get('Routes',[]) if x.get('DestinationCidrBlock')==v.get('destination_cidr_block')]
        return unique(matches,'DestinationCidrBlock',addr)
    if typ == 'aws_route_table_association':
        subnet=v.get('subnet_id')
        if not subnet:return None
        r=aws.get('ec2','describe-route-tables','--filters',f'Name=association.subnet-id,Values={subnet}')
        matches=[x for t in r.get('RouteTables',[]) for x in t.get('Associations',[]) if x.get('SubnetId')==subnet and x.get('AssociationId')]
        return unique(matches,'AssociationId',addr)
    if typ == 'aws_vpc_security_group_ingress_rule':
        group=v.get('security_group_id')
        if not group:return None
        r=aws.get('ec2','describe-security-group-rules','--filters',f'Name=group-id,Values={group}')
        source=v.get('referenced_security_group_id')
        matches=[x for x in r.get('SecurityGroupRules',[]) if not x.get('IsEgress') and x.get('IpProtocol')==v.get('ip_protocol') and x.get('FromPort')==v.get('from_port') and x.get('ToPort')==v.get('to_port') and (not source or (x.get('ReferencedGroupInfo') or {}).get('GroupId')==source)]
        return unique(matches,'SecurityGroupRuleId',addr)
    raise AWSFailure(f'NO hay verificación de colisión para {addr} ({typ}). Operación detenida por seguridad.')


def main(plan, aws=None):
    vars=plan.get('variables',{})
    def val(k): return (vars.get(k) or {}).get('value')
    for key, expected in [('aws_region','us-east-1'),('project_name','flashstock'),('environment','dev')]:
        actual=val(key) if val(key) is not None else expected  # defaults declarados en *.tf
        if actual != expected: raise AWSFailure(f'{key}={actual!r}; este guard solo acepta FlashStock dev ({expected!r}).')
    aws=aws or AWS('us-east-1')
    sts=aws.get('sts','get-caller-identity')
    if sts.get('Account') != EXPECTED_ACCOUNT: raise AWSFailure(f'Cuenta incorrecta: {sts.get("Account")}; esperada {EXPECTED_ACCOUNT}. No se toca solicitud-dev ni otra cuenta.')
    api=aws.get('apigatewayv2','get-api','--api-id',EXPECTED_API)
    if api.get('ApiId')!=EXPECTED_API or api.get('Name')!='flashstock-dev-http-api': raise AWSFailure('API Gateway FlashStock no corresponde al ID/nombre previsto.')
    pool=aws.get('cognito-idp','describe-user-pool','--user-pool-id',EXPECTED_POOL)
    if (pool.get('UserPool') or {}).get('Id')!=EXPECTED_POOL: raise AWSFailure('User Pool Cognito FlashStock no coincide.')
    domain=aws.get('cognito-idp','describe-user-pool-domain','--domain',EXPECTED_DOMAIN)
    if (domain.get('DomainDescription') or {}).get('UserPoolId') != EXPECTED_POOL: raise AWSFailure('Dominio Cognito inexistente o asignado a otro User Pool.')
    changes=plan.get('resource_changes',[])
    conflict=[]; created=0
    for r in changes:
        a=r.get('change',{}).get('actions',[])
        if 'delete' in a: raise AWSFailure(f'{r["address"]}: plan incluye destroy/reemplazo ({a}). No se aplica.')
        if 'update' in a and r.get('mode') == 'managed':
            raise AWSFailure(f'{r["address"]}: plan incluye una modificacion de recurso EXISTENTE ({a}). Revisa y reconcilia el cambio antes de desplegar la foundation.')
        if 'create' not in a: continue
        created+=1
        if r.get('mode')!='managed':continue
        found=get_existing(aws,r)
        if found: conflict.append((r['address'],found))
    if conflict:
        lines=['Se encontraron recursos YA EXISTENTES EN AWS pero NO administrados bajo estas direcciones del estado:',*('  '+a+' => '+str(i) for a,i in conflict),
        'No se recrearán ni se importarán a ciegas. Comprueba propiedad y utiliza terraform import DIRECCION ID; vuelve a ejecutar safe-apply.sh.']
        raise AWSFailure('\n'.join(lines))
    print(f'OK: cuenta {EXPECTED_ACCOUNT}, API/Pool/dominio verificados, {created} creaciones planificadas sin colisiones detectadas entre identidades verificables; 0 destroys.')
    if not created: print('El recurso que ya está en el state se reutiliza sin crear una segunda instancia.')
    return 0

if __name__=='__main__':
    if len(sys.argv)!=2: sys.exit('Uso: guard-plan.py RUTA_JSON_DE_TERRAFORM_SHOW')
    try:
        with open(sys.argv[1],encoding='utf-8') as f: p=json.load(f)
        sys.exit(main(p))
    except (AWSFailure, FileNotFoundError, ValueError) as ex:
        print('BLOQUEADO ANTES DEL APPLY: '+str(ex),file=sys.stderr); sys.exit(3)
