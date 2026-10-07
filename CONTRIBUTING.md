# Contributing

Thanks for helping test and improve MoonCast. English and Chinese reports are welcome.

## Before coding

Read the [build guide](docs/building.md), [architecture](docs/architecture.md), and [current limits](docs/compatibility.md). Discuss large features in an issue first. Keep changes focused and retain upstream licenses/provenance. Do not update vendored native code and binary manifests independently.

Use JDK 17 and Android SDK 35. Run:

```sh
./gradlew :app:assembleDebug :app:lintDebug
python scripts/run_unit_tests.py
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Use `gradlew.bat` on Windows. Add behavior tests when changing crop stabilization, input mapping/session cleanup, or volume recovery. Device-only changes need an actual device test report; static checks alone are not runtime evidence.

## Pull requests

Describe the user-visible problem and resulting behavior, verification performed, and remaining limits. For visual changes, include an original screenshot or test-pattern image with private information removed. For native changes, include source provenance and corresponding-source build details. CI does not run Root or Moonlight end-to-end tests.

## Translation

English default resources are in `app/src/main/res/values/strings.xml`; Chinese resources are in `values-zh/strings.xml`. Preserve resource names and escapes; test long labels and small screens. Documentation starts at `README.md` / `README.zh-CN.md`. Lower-level native/Root diagnostic strings are not all localized yet.

## Reports

Use the bug/compatibility/feature forms. Include exact versions and settings, steps, expected and actual behavior. Never upload keys, certificates, pairing identities, phone serial numbers, unredacted screens, or private logs. Report vulnerabilities privately through [SECURITY.md](SECURITY.md).

By submitting a contribution, you agree to distribute it under the project's GPLv3 license with applicable retained third-party notices. No CLA is required. Follow the [code of conduct](CODE_OF_CONDUCT.md).
