#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Independent build recipe for this prototype, using upstream build interfaces.
# Does not invoke moonlight-mobile-deps scripts or Opus autogen/model downloads.
set -euo pipefail

HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd -- "$HERE/.." && pwd)
NDK=${NDK:-"$ROOT/toolchain/android-ndk-r29"}
CMAKE=${CMAKE:-$(command -v cmake || printf '%s' "$HERE/tools/python/cmake/data/bin/cmake")}
JOBS=${JOBS:-3}
API=21
SOURCE_ROOT=${SOURCE_ROOT:-"$ROOT/native-sources"}
SOURCE_MANIFEST=${SOURCE_MANIFEST:-"$SOURCE_ROOT/SOURCE-FILES-SHA256SUMS"}
OPUS=${OPUS_SOURCE_DIR:-"$SOURCE_ROOT/opus"}
OPENSSL=${OPENSSL_SOURCE_DIR:-"$SOURCE_ROOT/openssl"}
NDK=$(cd -- "$NDK" && pwd)
OPUS=$(cd -- "$OPUS" && pwd)
OPENSSL=$(cd -- "$OPENSSL" && pwd)
OPUS_REV=22244de5a79bd1d6d623c32e72bf1954b56235be
OPENSSL_REV=f089acdf4bc7ba94a79f4bf6eb7362c3e7d14aa9
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

check_sources() {
  python3 - "$OPUS" "$OPENSSL" "$SOURCE_MANIFEST" "$OPUS_REV" "$OPENSSL_REV" <<'PY'
import hashlib, pathlib, subprocess, sys
opus, openssl, manifest, opus_rev, openssl_rev = sys.argv[1:]
for name, directory, revision in [('opus', opus, opus_rev), ('openssl', openssl, openssl_rev)]:
    root = pathlib.Path(directory)
    if (root / '.git').exists():
        actual = subprocess.check_output(['git', '-C', directory, 'rev-parse', 'HEAD'], text=True).strip()
        dirty = subprocess.check_output(['git', '-C', directory, 'status', '--porcelain'], text=True).strip()
        if actual != revision or dirty:
            raise SystemExit(f'Unexpected revision or source modifications: {name}')
        print(f'{name}: pristine pinned Git source verified')
        continue
    entries = {}
    for line in pathlib.Path(manifest).read_text().splitlines():
        digest, path = line.split('  ', 1)
        if path.startswith(name + '/'):
            relative = pathlib.PurePosixPath(path[len(name) + 1:])
            if relative.is_absolute() or '..' in relative.parts:
                raise SystemExit('Unsafe source-manifest path')
            entries[relative.as_posix()] = digest
    if not entries:
        raise SystemExit(f'No source hashes for {name}')
    actual_files = {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()}
    if actual_files != entries.keys():
        raise SystemExit(f'Source file inventory mismatch for {name}: missing={set(entries)-actual_files}, extra={actual_files-set(entries)}')
    for relative, expected in entries.items():
        if hashlib.sha256((root / relative).read_bytes()).hexdigest() != expected:
            raise SystemExit(f'Source hash mismatch: {name}/{relative}')
    print(f'{name}: {len(entries)} source files verified without Git metadata')
PY
}
check_sources
grep -q '^Pkg.Revision = 29.0.14206865$' "$NDK/source.properties"
CMAKE=$(command -v "$CMAKE")
[[ -x "$CMAKE" && -x "$BIN/clang" ]]
export ANDROID_NDK_ROOT="$NDK" ANDROID_NDK_HOME="$NDK"
export PATH="$BIN:$PATH" LC_ALL=C TZ=UTC
export SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-1787657915} # Pinned OpenSSL commit time.
if [[ ${1:-} == --check-inputs ]]; then
  echo 'Sources, NDK, and CMake verified; no build outputs changed.'
  exit 0
fi
mkdir -p "$HERE/build" "$HERE/artifacts" "$HERE/logs"

build_abi() {
  local abi=$1 openssl_target=$2
  local opus_build="$HERE/build/opus-$abi"
  local crypto_build="$HERE/build/openssl-$abi"
  local artifact="$HERE/artifacts/$abi"
  mkdir -p "$opus_build" "$crypto_build" "$artifact/include/openssl" "$artifact/include/opus"
  "$CMAKE" -S "$OPUS" -B "$opus_build" -G 'Unix Makefiles' \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$API" \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DCMAKE_DISABLE_FIND_PACKAGE_Git=TRUE -DOPUS_PACKAGE_VERSION=1.6.1 \
    -DBUILD_SHARED_LIBS=OFF -DOPUS_BUILD_SHARED_LIBRARY=OFF \
    -DBUILD_TESTING=OFF -DOPUS_BUILD_TESTING=OFF -DOPUS_BUILD_PROGRAMS=OFF \
    -DOPUS_DRED=OFF -DOPUS_DEEP_PLC=OFF -DOPUS_OSCE=OFF \
    -DOPUS_INSTALL_PKG_CONFIG_MODULE=OFF -DOPUS_INSTALL_CMAKE_CONFIG_MODULE=OFF
  "$CMAKE" --build "$opus_build" --parallel "$JOBS" --target opus
  cp "$opus_build/libopus.a" "$artifact/libopus.a"
  cp "$OPUS"/include/*.h "$artifact/include/opus/"
  echo "CHECKPOINT: $abi libopus.a built from source without neural models"
  (
    cd "$crypto_build"
    perl "$OPENSSL/Configure" "$openssl_target" \
      -D__ANDROID_API__="$API" no-shared no-tests no-apps no-legacy no-dso
    make -j"$JOBS" build_libs
  )
  cp "$crypto_build/libcrypto.a" "$artifact/libcrypto.a"
  # Combine original public headers with generated target-specific headers.
  find "$OPENSSL/include/openssl" -maxdepth 1 -type f -name '*.h' -exec cp '{}' "$artifact/include/openssl/" ';'
  find "$crypto_build/include/openssl" -maxdepth 1 -type f -name '*.h' -exec cp '{}' "$artifact/include/openssl/" ';'
  cp "$OPUS/COPYING" "$artifact/OPUS-COPYING"
  cp "$OPENSSL/LICENSE.txt" "$artifact/OPENSSL-LICENSE.txt"
  echo "CHECKPOINT: $abi libcrypto.a built from source with NDK29"
}

pids=()
for row in 'arm64-v8a android-arm64' 'armeabi-v7a android-arm' 'x86_64 android-x86_64'; do
  read -r abi target <<< "$row"
  build_abi "$abi" "$target" >"$HERE/logs/$abi.log" 2>&1 &
  pids+=("$!")
done
failed=0
for pid in "${pids[@]}"; do wait "$pid" || failed=1; done
[[ "$failed" == 0 ]] || { echo 'At least one ABI build failed; inspect logs/.' >&2; exit 1; }

check_sources
(
  cd "$HERE"
  sha256sum artifacts/*/libopus.a artifacts/*/libcrypto.a > ARCHIVE-SHA256SUMS
)
echo 'All six static libraries built. No upstream archive or core module was changed.'
