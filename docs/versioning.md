# Version naming

App version names use numeric `0.x.x` values only. The current application version
is `0.6.0` with versionCode `56`, greater than the highest previously published
code `55`. App About reads the installed package version; phone/TV module metadata
uses the same numeric version. The separate internal diagnostic application keeps
its own numeric `0.1.0` version. Build variant names and package identities remain
unchanged, but variants do not append version-name suffixes.

New release APK filenames are `MoonCast-0.6.0-arm64-v8a.apk` and
`MoonCast-0.6.0-armeabi-v7a.apk`. Record source/build identity and SHA256 separately.
The planned release tag is `v0.6.0`; previous tags, asset names and download URLs
are historical records and must not be overwritten or deleted.

This update changes version metadata and its verification expectations only. It
retains the selected enlarged icon, existing browser/Android media behavior, fixed
`com.mooncast.community` package and existing public test signing certificate.
Numeric naming does not expand the tested compatibility or quality scope. See
`browser-validation.md`, `browser-qr-quality.md` and `community-validation.md` for
validation limits and signing policy. No additional device test is claimed.
