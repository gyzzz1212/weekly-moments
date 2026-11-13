#!/usr/bin/env python3
"""Read PUBLIC Supabase connection settings from stdin; reject privileged keys."""
import base64,json,re,sys
from pathlib import Path
from urllib.parse import urlsplit
root=Path(__file__).resolve().parents[1]
data=json.load(sys.stdin)
url=data.get('url','').strip().rstrip('/')
key=data.get('publishableKey','').strip()
parts=urlsplit(url)
if parts.scheme!='https' or not re.fullmatch(r'[a-z0-9]+\.supabase\.co',parts.netloc) or parts.path or parts.query or parts.fragment:
    raise SystemExit('Expected the HTTPS project URL, without paths or credentials.')
valid=bool(re.fullmatch(r'sb_publishable_[A-Za-z0-9_-]{10,}',key))
if not valid:
    try:
        token=key.split('.')[1];claims=json.loads(base64.urlsafe_b64decode(token+'='*((4-len(token)%4)%4)))
        valid=claims.get('role')=='anon' and len(key.split('.'))==3
    except Exception:pass
if not valid:raise SystemExit('Only a publishable or legacy anon key is allowed. Never use a service-role or secret key.')
target=root/'app/src/main/assets/cloud/config.json'
temp=target.with_suffix('.tmp');temp.write_text(json.dumps({'url':url,'publishableKey':key})+'\n');temp.replace(target)
print('Public cloud settings saved. Rebuild the app; complete the migration and email templates before real account tests.')
