#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
exec python3 scripts/run_apk_transfer_tests.py
