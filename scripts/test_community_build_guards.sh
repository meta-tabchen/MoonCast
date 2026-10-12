#!/usr/bin/env bash
# Isolated CI checkout only. Never generates, exports or transmits signing material.
set -euo pipefail
key=signing/community/mooncast-public-test.p12
pin=signing/community/certificate-sha256.txt
tmp=$(mktemp -d)
cleanup() {
  if [ -f "$tmp/fixture" ]; then mv "$tmp/fixture" "$key"; fi
  if [ -f "$tmp/pin" ]; then cp "$tmp/pin" "$pin"; fi
  rm -rf "$tmp"
}
trap cleanup EXIT
cp "$pin" "$tmp/pin"
mv "$key" "$tmp/fixture"
if gradle --no-daemon :unified:tasks >"$tmp/missing.txt" 2>&1; then echo 'Missing community fixture was accepted' >&2; exit 1; fi
grep -q 'Fixed public community fixture missing' "$tmp/missing.txt"
gradle --no-daemon :unified:tasks -PmooncastUnsignedVerification=true >"$tmp/verification.txt" 2>&1
mv "$tmp/fixture" "$key"
printf '%064d\n' 0 > "$pin"
if gradle --no-daemon :unified:tasks >"$tmp/mismatch.txt" 2>&1; then echo 'Changed community pin was accepted' >&2; exit 1; fi
grep -q 'Unexpected community identity' "$tmp/mismatch.txt"
cp "$tmp/pin" "$pin"
if gradle --no-daemon :unified:preProductionBuild >"$tmp/production.txt" 2>&1; then echo 'Production accepted community setup' >&2; exit 1; fi
grep -q 'Production publication is not configured' "$tmp/production.txt"
echo 'PASS: missing fixture and changed pin fail; explicit unsigned verification configures; production is rejected; no key generated.'
