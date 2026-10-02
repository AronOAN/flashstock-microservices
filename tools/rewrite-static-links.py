#!/usr/bin/env python3
"""Run ONCE after reviewing diffs. Rewrites only simple legacy href URLs in site's own HTML."""
from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else 'flashstock-frontend/public/static')
if not root.is_dir(): raise SystemExit(f'No static HTML folder: {root}')
replacements={
    'http://localhost:3000/':'/',
    'http://localhost:8081/oauth2/authorization/google':'/auth/login',
    '/oauth2/authorization/google':'/auth/login',
    'http://localhost:8081/oauth2/authorization/microsoft':'/auth/login',
    '/oauth2/authorization/microsoft':'/auth/login',
    'href="/logout"':'href="/auth/logout"',
    "href='/logout'":"href='/auth/logout'",
}
changed=0
for p in root.glob('*.html'):
    old=p.read_text(encoding='utf-8-sig'); new=old
    for before,after in replacements.items(): new=new.replace(before,after)
    if old!=new:
        p.write_text(new,encoding='utf-8');changed+=1;print('updated',p)
print('Updated files:',changed,'(review git diff before committing)')
