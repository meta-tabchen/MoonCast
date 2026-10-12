#!/usr/bin/env python3
"""Run generated-media Android renderer tests on an explicitly selected test device.

No network sender, pairing identity, key, or release artifact is created by this script.
Use a dedicated emulator/test device; --install replaces its debug test application.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True, help="Explicit adb device selector")
    parser.add_argument("--output", type=Path, required=True, help="Private local evidence directory outside release assets")
    parser.add_argument("--install", action="store_true", help="Install the already-built debug app and androidTest APK")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    args.output.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, "-s", args.serial]

    def command(*parts, timeout=120, check=True):
        return subprocess.run(adb + list(parts), text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, timeout=timeout, check=check)

    try:
        state = command("get-state").stdout.strip()
        if state != "device":
            raise RuntimeError("Selected device is not ready: " + state)
        if args.install:
            for apk in (root / "tv/build/outputs/apk/debug/tv-debug.apk",
                        root / "tv/build/outputs/apk/androidTest/debug/tv-debug-androidTest.apk"):
                if not apk.is_file():
                    raise RuntimeError("Build the debug APK first: " + str(apk))
                print(command("install", "-r", "-t", str(apk), timeout=240).stdout, end="")
        result = command("shell", "am", "instrument", "-w", "-e", "suite", "gamestream-renderer",
                         "com.mooncast.tv.test/com.mooncast.tv.TvUiInstrumentation", timeout=600, check=False)
        (args.output / "instrumentation.txt").write_text(result.stdout)
        print(result.stdout, end="")
        # Pull only this generated-media suite's artifacts; never collect private app data.
        pulled = command("pull", "/sdcard/Android/data/com.mooncast.tv/files/gamestream-renderer-validation/.",
                         str(args.output), timeout=120, check=False)
        (args.output / "pull.txt").write_text(pulled.stdout)
        if result.returncode != 0 or "GAMESTREAM_RENDERER_VALIDATION_PASSED" not in result.stdout:
            raise RuntimeError("Instrumentation did not report a pass; see instrumentation.txt")
        if pulled.returncode != 0:
            raise RuntimeError("Evidence pull failed: " + pulled.stdout)
        report = json.loads((args.output / "report.json").read_text())
        if report.get("passed") is not True or not report.get("checks") or any(
                item.get("passed") is not True for item in report["checks"]):
            raise RuntimeError("Device report does not contain passing checks")
        for name in ("first-surface", "replacement-surface", "reattached-surface", "new-session"):
            if not (args.output / (name + ".png")).is_file():
                raise RuntimeError("Missing decoded-pixel evidence: " + name)
        print(f"PASS: {len(report['checks'])} actual Android renderer-boundary checks")
        print("No GameStream network, native Opus decode, speaker-audibility, physical-TV or performance claim.")
        return 0
    except (OSError, subprocess.SubprocessError, ValueError, RuntimeError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
