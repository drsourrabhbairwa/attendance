#!/usr/bin/env python3
"""Stamp a new web version: version.json, the version inside index.html, and the ?v= on notify.js.
Run it as the last step before pushing a release:  python3 tools/release.py ["what changed, one line"] [--force]
Phones that have the app open see an "Update now" pop-up within about 5 minutes."""
import json, re, sys, datetime, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
vj = root / 'version.json'
cur = json.loads(vj.read_text())
today = datetime.date.today().isoformat()
n = int(cur['web'].split('.')[1]) + 1 if cur.get('web', '').startswith(today) else 1
ver = f'{today}.{n}'
args = [a for a in sys.argv[1:] if a != '--force']
cur['web'] = ver
cur['notes'] = args[0] if args else ''
if '--force' in sys.argv: cur['force'] = True
else: cur.pop('force', None)
vj.write_text(json.dumps(cur, ensure_ascii=False) + '\n')
ix = root / 'index.html'
s = ix.read_text()
s, a = re.subn(r"window\.AMK_PAGE_VERSION='[^']*'", f"window.AMK_PAGE_VERSION='{ver}'", s)
s, b = re.subn(r'src="notify\.js\?v=[^"]*"', f'src="notify.js?v={ver}"', s)
s, c = re.subn(r'src="appstore\.js\?v=[^"]*"', f'src="appstore.js?v={ver}"', s)
if a != 1 or b != 1 or c != 1: sys.exit('index.html is missing the version stamp')
ix.write_text(s)
print('released', ver)
