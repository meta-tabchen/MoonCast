"""Package corresponding source and pinned native runtimes, excluding local identities/build caches."""
from pathlib import Path
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
version = re.search(r"versionName\s+'([^']+)'", (root / "app/build.gradle").read_text()).group(1)
dest = root.parent / f"MoonCast-{version}-source.zip"
excluded = {".git", ".gradle", ".cxx", "build", "local.properties", ".idea", "__pycache__",
            "host-cert.pem", "host-key.pem", "paired-clients.json", ".env"}
if (root / '.git').exists():
    names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode('utf-8').split('\0')
    paths = [root / name for name in names if name]
else:
    # Source archives have no .git; still exclude known generated/private payloads.
    paths = root.rglob('*')
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
    for path in sorted(paths):
        if not path.is_file():
            continue
        rel = path.relative_to(root)
        if rel.parts[:4] == ('app','src','debug','jniLibs'):
            continue
        if any(part in excluded for part in rel.parts) or path.suffix in {".keystore", ".jks", ".pyc", ".log"} or path.name.startswith('.env.'):
            continue
        z.write(path, "MoonCast/" + rel.as_posix())
print(dest)
