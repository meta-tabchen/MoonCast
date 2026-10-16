#!/usr/bin/env bash
# Rebuild the pinned v0.0.33 AirPlay AAR entirely offline from corresponding source.
# Supply already-installed tools; this script never installs SDK packages or accepts licenses.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
NATIVE="$ROOT/native/airplay-sender"
OUTPUT=$(python3 -c 'import pathlib,sys; print(pathlib.Path(sys.argv[1]).resolve())' "${1:-$ROOT/app/libs/airplaylib.aar}")
GO=${GO_BINARY:-go}
: "${ANDROID_HOME:?Set ANDROID_HOME to an existing Android SDK with platform 35}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the existing NDK 29.0.14206865}"
: "${JAVA_HOME:?Set JAVA_HOME to an existing JDK 17 or 21 installation}"
case "$("$GO" version)" in
  "go version go1.25.10 linux/amd64"|"go version go1.25.6 darwin/amd64") ;;
  *) echo "Use recorded Go 1.25.10 linux/amd64 or Go 1.25.6 darwin/amd64" >&2; exit 1 ;;
esac
grep -q '^Pkg.Revision = 29.0.14206865$' "$ANDROID_NDK_HOME/source.properties" || { echo 'NDK 29.0.14206865 is required' >&2; exit 1; }
[[ -f "$ANDROID_HOME/platforms/android-35/android.jar" ]] || { echo 'Android SDK platform 35 is required' >&2; exit 1; }
case "$("$JAVA_HOME/bin/javac" -version 2>&1 | tail -n 1)" in
  "javac 17."*|"javac 21."*) ;;
  *) echo "JDK 17 or 21 is required" >&2; exit 1 ;;
esac
python3 "$ROOT/scripts/prepare_airplay.py" --verify-sources
# Go records the local replacement module directory in its build information even
# with -trimpath. Use a stable, non-personal path and exclusive mkdir ownership.
WORK=/tmp/mooncast-airplay-073
mkdir "$WORK" || { echo "Build directory already exists: $WORK (another build or interrupted run)" >&2; exit 1; }
trap 'chmod -R u+w "$WORK"; rm -rf "$WORK"' EXIT
python3 "$ROOT/scripts/prepare_airplay.py" --prepare "$WORK/source"
# Pin javac to platform 35 even when the supplied SDK also contains newer platforms.
mkdir -p "$WORK/android-sdk/platforms"
ln -s "$ANDROID_HOME/platforms/android-35" "$WORK/android-sdk/platforms/android-35"
export ANDROID_HOME="$WORK/android-sdk" ANDROID_SDK_ROOT="$WORK/android-sdk"
export GOPATH="$WORK/gopath" GOBIN="$WORK/bin" GOMODCACHE="$WORK/gopath/pkg/mod"
export GOCACHE="${AIRPLAY_GO_CACHE:-$WORK/gocache}"
export GOPROXY="file://$NATIVE/module-proxy" GOSUMDB=off GOTOOLCHAIN=local
export GOFLAGS='-trimpath -buildvcs=false -p=3'
export PATH="$(dirname "$(command -v "$GO")"):$GOBIN:$JAVA_HOME/bin:$PATH"
# Full module archives were checked against Go's checksum database at acquisition,
# and are checksum-locked locally. No proxy or checksum network request is used now.
cd "$WORK/source"
"$GO" install golang.org/x/mobile/cmd/gomobile
"$GO" install golang.org/x/mobile/cmd/gobind
"$GO" test ./airplaylib
# One client per process; same protocol code, privacy patch, and 3 ABIs as the lock.
gomobile bind -v -trimpath \
  -ldflags='-buildid= -extldflags=-Wl,-z,max-page-size=16384' \
  -target=android/arm64,android/arm,android/amd64 -androidapi=26 \
  -o "$WORK/airplaylib-raw.aar" ./airplaylib
python3 "$ROOT/scripts/prepare_airplay.py" --normalize "$WORK/airplaylib-raw.aar" "$WORK/airplaylib.aar"
python3 "$ROOT/scripts/prepare_airplay.py" --verify-aar "$WORK/airplaylib.aar"
mkdir -p "$(dirname "$OUTPUT")"
cp "$WORK/airplaylib.aar" "$OUTPUT"
echo "Pinned AirPlay AAR rebuilt and verified: $OUTPUT"
