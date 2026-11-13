#!/usr/bin/env python3
"""Checks capability boundaries and accidental sensitive files, not a security certification."""
from pathlib import Path
import re, json, zipfile, sys, hashlib, base64, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
android='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
permissions={e.get(android+'name') for e in manifest.findall('uses-permission')}
allowed={'android.permission.POST_NOTIFICATIONS','android.permission.SCHEDULE_EXACT_ALARM','android.permission.RECEIVE_BOOT_COMPLETED','android.permission.INTERNET'}
assert permissions==allowed,permissions
assert manifest.find('application').get(android+'allowBackup')=='false'
service=ET.parse(root/'app/src/main/res/xml/accessibility_service.xml').getroot()
assert service.get(android+'canRetrieveWindowContent')=='false'
assert service.get(android+'accessibilityEventTypes')=='typeWindowStateChanged'
assert 'flagRetrieveInteractiveWindows' not in service.get(android+'accessibilityFlags','')
assert not service.get(android+'canTakeScreenshot') and not service.get(android+'canRequestFilterKeyEvents')
code=(root/'app/src/main/java/cn/weeklymoments/guard/MomentsGuardService.java').read_text()
for api in ('getRootInActiveWindow','getWindows(', 'getSource(', 'getText(', 'getContentDescription(', 'onKeyEvent(', 'takeScreenshot(', 'AccessibilityNodeInfo','AccessibilityWindowInfo','MediaProjection','InputConnection'):
    assert api not in code,api
allcode='\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.java'))
for api in ('OkHttpClient','WebView','MediaRecorder','AudioRecord','MediaProjection','takeScreenshot','NotificationListenerService','UsageStatsManager','AccessibilityNodeInfo'):
    assert api not in allcode,api
assert not re.search(r'\bSocket\s*\(',allcode)
for source in (root/'app/src/main/java').rglob('*.java'):
    value=source.read_text()
    if source.name!='CloudHttpTransport.java':
        assert 'openConnection(' not in value and 'HttpsURLConnection' not in value,source.name
transport=(root/'app/src/main/java/cn/weeklymoments/guard/CloudHttpTransport.java').read_text()
assert 'setInstanceFollowRedirects(false)' in transport
assert not any(api in transport for api in ('setHostnameVerifier','setSSLSocketFactory','TrustManager'))
assert 'getNoBackupFilesDir()' in (root/'app/src/main/java/cn/weeklymoments/guard/AccountVault.java').read_text()
config=json.loads((root/'app/src/main/assets/cloud/config.json').read_text())
assert bool(config['url'])==bool(config['publishableKey']),'Cloud settings must be complete or both empty'
if config['publishableKey']:
    assert re.fullmatch(r'https://[a-z0-9]+\.supabase\.co',config['url'])
    public_key=config['publishableKey']
    if not re.fullmatch(r'sb_publishable_[A-Za-z0-9_-]{10,}',public_key):
        parts=public_key.split('.')
        assert len(parts)==3,'Only publishable / anon keys may be bundled'
        payload=parts[1]+'='*((4-len(parts[1])%4)%4)
        assert json.loads(base64.urlsafe_b64decode(payload))['role']=='anon','Privileged keys cannot be shipped'

assert 'event.getPackageName()' in code and 'event.getClassName()' in code
assert 'remove("last_page")' in allcode
assets=root/'app/src/main/assets'
for p in assets.rglob('*'):
    if not p.is_file():continue
    raw=p.read_bytes()
    for forbidden in (b'/Users/',b'wxid_',b'xwechat_files',b'OPENAI_API_KEY',b'BEGIN PRIVATE KEY'):
        assert forbidden not in raw,(p.name,forbidden)
    assert p.suffix not in ('.jks','.keystore','.jpg'),p.name
art=json.loads((root/'design/asset-approvals.json').read_text())
assert set(art['sha256'])=={'assistant.png','assistant-blink.png','chibi.png','chibi-blink.png'}
for name,digest in art['sha256'].items():
    assert hashlib.sha256((assets/'character'/name).read_bytes()).hexdigest()==digest,name
assert hashlib.sha256((assets/'character'/art['poses']['file']).read_bytes()).hexdigest()==art['poses']['sha256']
assert hashlib.sha256((assets/'character'/art['standing']['file']).read_bytes()).hexdigest()==art['standing']['sha256']
for base,blink in [('assistant.png','assistant-blink.png'),('chibi.png','chibi-blink.png')]:
    assert (assets/'character'/base).read_bytes()[16:24]==(assets/'character'/blink).read_bytes()[16:24]
cards=json.loads((assets/'knowledge/cards.json').read_text())['cards']
assert len(cards)==42 and len({c['title'] for c in cards})==42
assert all(c['id']==i+1 and c['day']==i//3+1 and len(c['diagram'])==3 and len(c['body'])>=100 and c['question'] for i,c in enumerate(cards))
if len(sys.argv)>1:
    with zipfile.ZipFile(sys.argv[1]) as z:
        assert not any(re.search(r'(^|/)(\.signing|password|.*\.(jks|keystore)|593c205|039b7a44)',n) for n in z.namelist())
        assert not any('tests/' in n or 'json-20240303' in n for n in z.namelist())
print('PASS: permission allowlist, content/key/screenshot capability exclusions, service metadata-only APIs, networking limited to explicit cloud transport with TLS defaults; no recording/usage-history APIs, private-path/secret asset checks, 42 ordered original cards')
