#!/usr/bin/env python3
"""Run from repository root after extracting the login patch; idempotent."""
from pathlib import Path
import re

root = Path('flashstock-frontend/public/static')
if not root.is_dir():
    raise SystemExit('ERROR: ejecuta este script desde la raiz del repositorio')
updated = []
for page in root.glob('*.html'):
    source = page.read_text(encoding='utf-8')
    def force_top(match):
        tag = match.group(0)
        if re.search(r'\btarget\s*=', tag, flags=re.I):
            return tag
        return tag[:-1] + ' target="_top">'
    result = re.sub(r'<a\b[^>]*\bhref\s*=\s*[\'\"]/(?:login|auth/login)[\'\"][^>]*>', force_top, source, flags=re.I)
    if result != source:
        page.write_text(result, encoding='utf-8')
        updated.append(str(page))
print('Updated login navigation:', ', '.join(updated) if updated else 'already correct')
# Current dynamic navigation in main.js already assigns userLink.target = '_top'. Confirm it.
script = root / 'js/main.js'
if script.exists():
    source = script.read_text(encoding='utf-8')
    if "userLink.target = '_top'" not in source:
        raise SystemExit("ATENCION: main.js necesita userLink.target = '_top' en attachSessionUi()")
    anchor = '    async function refreshStorefrontInventory() {\n'
    guard = '        // Inventory is ADMIN-only; never poll it from the public storefront.\n        if (!isAdminSession()) return;\n'
    if anchor not in source:
        raise SystemExit('ATENCION: No se encontró refreshStorefrontInventory; revisar manualmente.')
    before, after = source.split(anchor, 1)
    if guard not in after[:350]:
        source = before + anchor + guard + after
        script.write_text(source, encoding='utf-8')
        print('Guard installed: storefront no longer polls administrative Inventory anonymously.')
    else:
        print('Storefront Inventory guard already installed.')
