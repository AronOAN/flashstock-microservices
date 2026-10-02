#!/usr/bin/env bash
# Ejecutar DESDE infra/terraform/api-gateway: bash scripts/safe-apply.sh [--plan-only]

#ESte Script se creó con la finalidad de verificar si ya existen recursos dentro - 
# de aws para que no de error al hacer un deploy con el mismo proyecto o script, 
# avisando así si ya existe el resource para que terraform no lo cree otra vez.



set -euo pipefail
if [[ ! -f main.tf || ! -f cognito.tf || ! -f inventory-foundation.tf ]]; then
  echo 'ERROR: no estas en el directorio Terraform limpio de FlashStock' >&2; exit 2
fi
for old in permissions-lambda.tf 'cognito-domain.tf' 'business-routes.tf'; do
  if [[ -e "$old" ]]; then echo "ERROR: archivo experimental heredado $old; ejecuta el instalador con respaldo" >&2; exit 2; fi
done
if grep -Eq '^resource[[:space:]]+"aws_cognito_user_pool_domain"' ./*.tf; then
  echo 'ERROR: el dominio existente no debe declararse para crearlo por segunda vez.' >&2; exit 2
fi
: "${FLASHSTOCK_EXPECTED_ACCOUNT:=823102413975}"
export FLASHSTOCK_EXPECTED_ACCOUNT
terraform init -input=false
terraform fmt
terraform validate
planfile="$(pwd)/flashstock-safe.tfplan"
jsonfile="$(pwd)/flashstock-safe-plan.json"
trap 'rm -f "$planfile" "$jsonfile"' EXIT
terraform plan -input=false -out="$planfile"
terraform show -json "$planfile" > "$jsonfile"
python_cmd=python3; command -v python3 >/dev/null 2>&1 || python_cmd=python
"$python_cmd" scripts/guard-plan.py "$jsonfile"
if [[ "${1:-}" == '--plan-only' ]]; then
  echo 'Plan revisado y protegido. No se ha aplicado nada.'
  terraform show "$planfile"
  exit 0
fi
if [[ "${1:-}" != '' ]]; then echo 'Uso: bash scripts/safe-apply.sh [--plan-only]' >&2; exit 2; fi
backup_dir="$HOME/flashstock-backups"
umask 077
mkdir -p "$backup_dir"
terraform state pull > "$backup_dir/flashstock-before-apply-$(date +%Y%m%d-%H%M%S).tfstate"
echo "Respaldo state creado en $backup_dir (contiene datos sensibles; no subir a Git)."
terraform apply -input=false "$planfile"
