#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Rebuild JNI/protocol objects against our source-built dependency archives.
set -euo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(cd -- "$HERE/.." && pwd)
NDK=${NDK:-"$ROOT/toolchain/android-ndk-r29"}
UPSTREAM_JNI=${UPSTREAM_JNI:-"$ROOT/upstream/app/src/main/jni"}
DEPS=${DEPS:-"$ROOT/dependency-rebuild/artifacts"}
JOBS=${JOBS:-4}
for ABI in arm64-v8a armeabi-v7a x86_64; do
  STAGE="$HERE/stage/$ABI"
  mkdir -p "$STAGE" "$HERE/obj/$ABI" "$HERE/jniLibs"
  python3 - "$UPSTREAM_JNI" "$STAGE" "$DEPS" "$ABI" <<'PY'
from pathlib import Path
import shutil,sys
src,dst,deps,abi=map(str,sys.argv[1:]);src,dst,deps=map(Path,[src,dst,deps])
def ignore(p,names):return [n for n in names if n=='.git' or n.endswith('.a')]
if dst.exists(): shutil.rmtree(dst)
shutil.copytree(src,dst,ignore=ignore,copy_function=shutil.copyfile)
for name,archive in [('openssl','libcrypto.a'),('opus','libopus.a')]:
 target=dst/'moonlight-core'/name/abi;target.mkdir(parents=True,exist_ok=True)
 shutil.copyfile(deps/abi/archive,target/archive)
 # The include prefix is openssl/ or opus/ at the caller boundary.
 hdr=deps/abi/'include'/('openssl' if name=='openssl' else 'opus')
 out=dst/'moonlight-core'/name/'include'
 if name=='openssl':out=out/'openssl'
 shutil.copytree(hdr,out,dirs_exist_ok=True,copy_function=shutil.copyfile)
PY
  "$NDK/ndk-build" -j"$JOBS" NDK_PROJECT_PATH=null \
    APP_BUILD_SCRIPT="$STAGE/Android.mk" NDK_APPLICATION_MK="$STAGE/Application.mk" \
    APP_ABI="$ABI" APP_PLATFORM=android-26 NDK_DEBUG=0 APP_OPTIM=release PRODUCT_FLAVOR=nonRoot \
    APP_LDFLAGS="-Wl,--version-script,$HERE/exports.map -Wl,--exclude-libs,ALL" \
    NDK_OUT="$HERE/obj/$ABI" NDK_LIBS_OUT="$HERE/jniLibs"
done
find "$HERE/jniLibs" -name libmoonlight-core.so -print
