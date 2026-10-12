# AirPlay decoder host fakes

`scripts/run_airplay_decoder_tests.py` compiles the **actual** production
`MirrorDecoder.java` and `VideoTiming.java` with these test-only Android fakes.
It does not copy or reimplement the decoder under test. The fakes inject failures
at configure, start, stop and release, record codec ownership, and fail immediately
if another codec is created while a previous codec's release is unconfirmed or a
surface is released while a codec may still own it.

The tests cover sticky release failures, failed candidate cleanup blocking decoder
fallback, rejected surface reassignment while release is unconfirmed, retries,
and recovery after a successful release. Reflection checks private retained codec
and surface references without adding production-only test hooks.

Run with a full JDK 17 or newer:

```sh
python3 scripts/run_airplay_decoder_tests.py
```

For a stripped local JDK without `ct.sym`, explicitly opt in to Java 17 source and
bytecode targeting instead of the default `--release 17`:

```sh
python3 scripts/run_airplay_decoder_tests.py --source-target
# Equivalent to the option used by the other receiver host suites:
MOONCAST_TEST_JAVAC_SOURCE_ONLY=1 python3 scripts/run_airplay_decoder_tests.py
```

These are synthetic host lifecycle/ownership checks. They require no SDK or native
library, do not produce decoded frames, and are **not** Android compatibility,
vendor MediaCodec, casting, latency, or real-device release evidence. SDK-backed
compilation, parser/watchdog tests, and device validation remain separate. These
sources must never be included in an application source set.
