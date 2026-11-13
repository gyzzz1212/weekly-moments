#!/usr/bin/env python3
"""Explicit source allowlist; no toolchains, build outputs, signing keys or user originals."""
from pathlib import Path
import zipfile
ROOT=Path(__file__).resolve().parents[1]
import xml.etree.ElementTree as ET
version=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
out=ROOT/f'dist/WeeklyMoments-{version}-source.zip'
files=[ROOT/p for p in ('README.md','build.gradle','settings.gradle','.gitignore')]
for folder in ('app','tests','tools','design','cloud'):
    files.extend(p for p in (ROOT/folder).rglob('*') if p.is_file() and not any(x in p.parts for x in ('build','.gradle','__pycache__')) and p.name!='.DS_Store')
files.extend((ROOT/'dist').glob('*.md'))
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
    for p in sorted(set(files)):
        assert p.suffix not in ('.apk','.jks','.keystore') and '.signing' not in p.parts
        archive.write(p,p.relative_to(ROOT))
with zipfile.ZipFile(out) as archive:
    names=archive.namelist()
    assert not any('.signing/' in n or '.toolchain/' in n or '/.git/' in n or 'xwechat_files' in n or 'wxid_' in n for n in names)
print(f'Source archive: {out.name}; {len(names)} files; signing identity and user original photos excluded')
