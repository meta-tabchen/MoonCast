#!/bin/sh
set -eu
cd "$(dirname "$0")"
for source in src/com/mooncast/transfer/*.java; do cmp "$source" "../../tv/src/main/java/com/mooncast/transfer/$(basename "$source")"; done
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
JAVAC=javac
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"; fi
"$JAVAC" -source 8 -target 8 -d "$OUT" $(find src test -name '*.java') ../../tv/src/main/java/com/mooncast/tv/apk/ApkSingleManifest.java
"$JAVA" -Xmx64m -cp "$OUT" com.mooncast.transfer.TransferTest

"$JAVA" -Xmx64m -cp "$OUT" com.mooncast.tv.apk.ApkManifestHostTest "$@"
