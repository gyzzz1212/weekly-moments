#!/usr/bin/env python3
"""Small dependency-free build, test and sign pipeline using official Android SDK tools.

Usage: python3 tools/build_apk.py --jdk /path/to/jdk/Contents/Home --sdk /path/to/sdk
The signing identity is created locally once and is never included in deliverables.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import sys
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--jdk', type=Path, required=True)
parser.add_argument('--sdk', type=Path, required=True)
args = parser.parse_args()
jdk, sdk = args.jdk.resolve(), args.sdk.resolve()
bt = sdk / 'android-15'
if not bt.exists():
    bt = sdk / 'build-tools/35.0.0'
platform = sdk / 'android-34'
if not platform.exists():
    platform = sdk / 'platforms/android-34'
android = platform / 'android.jar'
java, javac, keytool = [jdk / 'bin' / x for x in ('java', 'javac', 'keytool')]
for path in (java, javac, keytool, bt / 'aapt2', bt / 'zipalign', bt / 'lib/d8.jar', android):
    if not path.exists():
        raise SystemExit(f'Missing build tool: {path}')

def run(*items):
    subprocess.run([str(x) for x in items], cwd=ROOT, check=True)

build = ROOT / 'build'
classes, generated, dex, testclasses = [build / x for x in ('classes', 'generated', 'dex', 'tests')]
for folder in (classes, generated, dex, testclasses):
    if folder.exists(): shutil.rmtree(folder)
for folder in (classes, generated, dex, testclasses, ROOT / 'dist'):
    folder.mkdir(parents=True, exist_ok=True)

sources = ROOT / 'app/src/main'
run(sys.executable, ROOT / 'tools/audit/privacy_audit.py')
for name, expected in json.loads((ROOT / 'tools/vendor-checksums.json').read_text()).items():
    actual = hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
    if actual != expected['sha256']:
        raise SystemExit(f'Vendor checksum mismatch: {name}')
aar = ROOT / 'app/libs/tesseract4android-4.9.0.aar'
library = build / 'ocr-classes.jar'
with zipfile.ZipFile(aar) as archive:
    library.write_bytes(archive.read('classes.jar'))
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest = ET.parse(sources / 'AndroidManifest.xml')
version = manifest.getroot().get('{http://schemas.android.com/apk/res/android}versionName')
manifest.getroot().set('package', 'cn.weeklymoments.guard')
manifest.write(build / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
run(bt / 'aapt2', 'compile', '--dir', sources / 'res', '-o', build / 'resources.zip')
run(bt / 'aapt2', 'link', '-o', build / 'resources.apk', '-I', android,
    '--manifest', build / 'AndroidManifest.xml', '--java', generated,
    '--min-sdk-version', '26', '--target-sdk-version', '34', '-A', sources / 'assets', build / 'resources.zip')
javafiles = sorted((sources / 'java').rglob('*.java')) + sorted(generated.rglob('*.java'))
run(javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', os.pathsep.join((str(bt / 'core-lambda-stubs.jar'), str(android))),
    '-classpath', library, '-d', classes, *javafiles)

# Meaningful scheduler and classifier tests use real JVM execution, no Android stubs.
pure = ['AccessPolicy', 'SessionPolicy', 'RoutineData', 'RoutineEngine', 'FocusQuotes', 'PageMatcher', 'LifeData', 'LifeEngine', 'LifeStats', 'TimetableParser', 'CoalescingTask', 'OverlayInteraction', 'CompanionFrames']
run(javac, '-encoding', 'UTF-8', '-d', testclasses,
    *(sources / f'java/cn/weeklymoments/guard/{name}.java' for name in pure),
    ROOT / 'tests/PolicyTests.java', ROOT / 'tests/LifeTests.java', ROOT / 'tests/RoutineTests.java',
    sources / 'java/cn/weeklymoments/guard/LocalHistory.java', ROOT / 'tests/LocalHistoryTests.java', ROOT / 'tests/ResponsivenessTests.java')
run(java, '-cp', testclasses, 'cn.weeklymoments.guard.PolicyTests')
run(java, '-cp', testclasses, 'cn.weeklymoments.guard.LifeTests')
run(java, '-cp', testclasses, 'cn.weeklymoments.guard.RoutineTests')
run(java, '-cp', testclasses, 'cn.weeklymoments.guard.LocalHistoryTests')
run(java, '-cp', testclasses, 'cn.weeklymoments.guard.ResponsivenessTests')
test_json = ROOT / 'tests/lib/json-20240303.jar'
test_path = os.pathsep.join(map(str, (test_json, classes, android, testclasses)))
run(javac, '-encoding', 'UTF-8', '-cp', test_path, '-d', testclasses, ROOT / 'tests/PersistenceTests.java', ROOT / 'tests/AccountTests.java')
run(java, '-cp', test_path, 'cn.weeklymoments.guard.PersistenceTests')
run(java, '-cp', test_path, 'cn.weeklymoments.guard.AccountTests')
run(java, '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', android,
    '--min-api', '26', '--output', dex, *sorted(classes.rglob('*.class')), library)
unsigned, aligned = build / 'unsigned.apk', build / 'aligned.apk'
shutil.copy2(build / 'resources.apk', unsigned)
with zipfile.ZipFile(unsigned, 'a') as archive:
    archive.write(dex / 'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_STORED)
    with zipfile.ZipFile(aar) as native:
        for member in native.namelist():
            if member.startswith('jni/arm64-v8a/') and member.endswith('.so'):
                archive.writestr(member.replace('jni/', 'lib/', 1), native.read(member), compress_type=zipfile.ZIP_DEFLATED)
run(bt / 'zipalign', '-f', '-p', '4', unsigned, aligned)

identity = ROOT / '.signing'
identity.mkdir(mode=0o700, exist_ok=True)
password, keystore = identity / 'password', identity / 'moments-guard.jks'
if (not password.exists() or not keystore.exists()) and any((ROOT / 'dist').glob('WeeklyMoments-*.apk')):
    raise SystemExit('Update signing identity is missing. Restore the original .signing directory; refusing to create an incompatible update.')
if not password.exists():
    password.write_text(secrets.token_urlsafe(40), encoding='utf-8')
    password.chmod(0o600)
if not keystore.exists():
    run(keytool, '-genkeypair', '-keystore', keystore, '-storepass:file', password,
        '-alias', 'moments-guard', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
        '-dname', 'CN=Weekly Moments Personal Build, O=Personal, C=CN')
    keystore.chmod(0o600)
apk = ROOT / f'dist/WeeklyMoments-{version}.apk'
run(java, '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', keystore,
    '--ks-pass', f'file:{password}', '--ks-key-alias', 'moments-guard', '--out', apk, aligned)
run(java, '-jar', bt / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', apk)
run(bt / 'zipalign', '-c', '-p', '4', apk)
run(bt / 'aapt', 'dump', 'badging', apk)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(ROOT / f'dist/{apk.name}.sha256').write_text(f'{digest}  {apk.name}\n')
print(f'APK ready: {apk}\nSHA-256: {digest}')
