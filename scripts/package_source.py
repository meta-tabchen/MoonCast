"""Package corresponding source and pinned native runtimes, excluding local identities/build caches."""
from pathlib import Path
import re
import zipfile

root = Path(__file__).resolve().parents[1]
version = re.search(r"versionName\s+'([^']+)'", (root / "app/build.gradle").read_text()).group(1)
dest = root.parent / f"MoonCast-{version}-source.zip"
excluded = {".git", ".gradle", ".cxx", "build", "local.properties", ".idea"}
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(root)
        if any(part in excluded for part in rel.parts) or path.suffix in {".keystore", ".jks"}:
            continue
        z.write(path, "MoonCast/" + rel.as_posix())
print(dest)
