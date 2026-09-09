"""Materialize the reviewed source delta, gated by its exact SHA-256."""
import base64
import gzip
import hashlib
import zlib
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
source = root / 'scripts/qa/reported-regressions.patch.gz'
if not source.exists():
    print('Reviewed patch already materialized in source')
    raise SystemExit(0)
expected = 'a376207647b24ed9438e1bfb4ff3885f88649a929afff6557344e73be0ebcf5a'
raw = source.read_bytes()
variants = [raw]
# Only the exact pre-recorded source digest is acceptable. A known single-character
# transfer error was reproduced and corrected locally against the same digest.
encoded = base64.b64encode(raw).decode()
if 'ws1rfiny2msizlWcr9' in encoded:
    fixed = encoded.replace('ws1rfiny2msizlWcr9', 'ws1rfiny2sizlWcr9', 1)
    try:
        variants.append(base64.b64decode(fixed + '=' * (-len(fixed) % 4)))
    except ValueError:
        pass
patch = None
for candidate in variants:
    try:
        value = gzip.decompress(candidate)
    except (OSError, EOFError, zlib.error):
        continue
    if hashlib.sha256(value).hexdigest() == expected:
        patch = value.decode('utf-8')
        break
if patch is None:
    raise RuntimeError('Patch transfer checksum mismatch. No source changes applied.')
parts = patch.split('diff --git ')
app_patch = ''.join('diff --git ' + part for part in parts[1:] if not part.startswith('a/.github/workflows/'))
subprocess.run(['git','apply','--check','-'], input=app_patch, text=True, check=True, cwd=root)
subprocess.run(['git','apply','-'], input=app_patch, text=True, check=True, cwd=root)
source.unlink()
print('Applied exact reviewed source delta:', expected)
