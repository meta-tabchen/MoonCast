# GameStream controller host fakes

`run_gamestream_controller_tests.py` compiles the **actual production**
GameStreamController, GameStreamHosts, PreparedDesktop, ReceiverSession,
RendererRelease, StreamSpec, and NvConnectionListener. Their ownership, epoch,
main-thread, and cancellation code is not copied into the harness.

The collaborators here are deliberately synthetic Android API shapes, renderers,
identity/client/host metadata, storage, discovery, and dialog fakes. They perform
no networking, native calls, real audio/video, key or certificate access, or real
pairing/trust writes. Fake host records carry a paired boolean instead of real
trust. The client fake can return a stale result after cancellation on purpose,
so production coordinators must reject it. A manual main Looper queue never
executes worker callbacks inline. Latches force scheduling windows; a watchdog
only releases a latch after a main-thread blocking regression so failure cannot
hang the suite. The blocked start fixture injects scheduling, and makes no claim
that the real NvConnection.start has that latency.

Each scenario runs in a fresh JVM, so an intentionally failed process-wide release
barrier is not reset with reflection or a test-only production backdoor. Tests
cover pending double Connect, focus until audio release, Stop during identity
lookup/prepare/start, stale callbacks, Activity/controller recreation, failed
release blocking replacement output, focus denial/loss, release-pending error
reporting, main-only mutations, and
non-blocking host cancellation during pairing and an already-started trust write.
A failed store reload also proves cached trust is cleared and manual/discovered
host selection, Pair confirmation, and Forget stay disabled without creating an
identity, client, pairing attempt, or trust mutation.

These files must never enter app source sets. They are host regression evidence,
not Android API/device compatibility, protocol interoperability, audio/codec
playback, security, cancellation latency, or device release proof. Android SDK
build/lint and physical-device tests remain separate requirements.
