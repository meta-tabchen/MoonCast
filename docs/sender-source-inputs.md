# Offline Sunshine sender build inputs

The packaged phone sender uses OpenSSL 3.5.5 and Sunshine's pinned Boost 1.86.0
CMake archive. The TV receiver's OpenSSL 3.4.4 and Moonlight's separate crypto
sources do not replace these sender dependencies.

`native/sender-build-inputs/` contains the unmodified official archives, downloaded
checksum sidecars, and provenance manifest. Their SHA256 values are pinned in
`scripts/prepare_sender_build_inputs.py`:

- OpenSSL 3.5.5: `b28c91532a8b65a1f983b4c28b7488174e4a01008e29ce8e69bd789f28bc2a89`
- Boost 1.86.0 CMake: `2c5ec5edcdff47ff55e27ed9560b0a0b94b07bd07ed9928b476150e16b0efc57`

Both match their official release checksum sidecars. Boost also matches the
Sunshine recipe's MD5 `d02759931cedc02aded80402906c5eb6`. SHA256 verification is
performed on every preparation. PGP signatures have not been verified.

## Prepare without downloads or compilation

Use Python 3.12 or newer and a new directory outside this repository:

```sh
python3 scripts/prepare_sender_build_inputs.py --verify-only
python3 scripts/prepare_sender_build_inputs.py --workdir /tmp/mooncast-sender-arm64
```

The script checks pinned hashes, rejects archive traversal, links, special files,
duplicate members and excessive extraction sizes, and uses the explicit Python
`data` extraction filter. It refuses to reuse an existing directory. Failed
preparation can leave a partial directory; choose a new directory after correcting
the error. No tools are installed and no native code is built.

The resulting `sunshine/` is a copy of the pinned native source. Boost already
supports CMake's `FETCHCONTENT_SOURCE_DIR_BOOST` override. A hash-checked patch to
the copied Boost recipe disables only its initial system-package lookup so that an
unrelated installed version is not silently used. Later package lookups remain
enabled and use FetchContent's existing `OVERRIDE_FIND_PACKAGE` redirect; globally
disabling Boost lookup would break required lookups in dependent projects.

The pinned ViaDuck OpenSSL recipe hardcodes its download URL and has no local
source-directory override. The script checks that recipe's exact SHA256, then
replaces only the URL/hash stanza in the work copy with an ExternalProject
`SOURCE_DIR` and an empty `DOWNLOAD_COMMAND`. Existing configuration, patch,
build and install steps remain unchanged. Original pinned Sunshine files remain
untouched. This deterministic transformation is replayed on each preparation;
both recipes' input/output hashes are saved in `prepared-inputs.json`.

## Use with the original Android build

Supply the already installed Android NDK/toolchain and the same original build
options, including the phone build's CMake 3.31.1. Sunshine's project requires at
least CMake 3.24 and relies on `OVERRIDE_FIND_PACKAGE`; the TV toolchain's CMake
3.22.1 is insufficient for configuring the phone sender. For example, the CMake
input portion for ARM64 becomes:

```sh
cmake -S /tmp/mooncast-sender-arm64/sunshine \
  -B /tmp/mooncast-sender-arm64/build \
  -C /tmp/mooncast-sender-arm64/sender-inputs.cmake \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26
```

This is an input-preparation example, not a claim that these flags alone reproduce
the shipped binaries. Preserve the original sender build settings and toolchain
versions. Prepare a fresh work directory per ABI because OpenSSL's original build
steps modify its source tree. The two dependency inputs require no network during
configuration; other original build requirements remain the caller's responsibility.

## Provenance and validation boundary

This work supplies missing corresponding-source build inputs. It does not change
or rebuild the delivered prebuilt native libraries, establish bit-for-bit native
reproducibility, or validate a new native runtime. Existing prebuilt provenance
records remain authoritative. Preparation and hash verification are separate from
a full native build and runtime test.

Validation on 2026-10-11: archive/sidecar verification, complete isolated extraction,
both deterministic recipe transformations, and preservation of original recipe
hashes passed. Traversal, absolute-path, symlink and special-device test archives
were rejected. The generated initial cache parsed with `cmake -P` using 3.22.1;
this syntax-only check did not configure Sunshine or establish toolchain compatibility.

Official sources and checksums:

- https://github.com/openssl/openssl/releases/download/openssl-3.5.5/openssl-3.5.5.tar.gz
- https://github.com/openssl/openssl/releases/download/openssl-3.5.5/openssl-3.5.5.tar.gz.sha256
- https://github.com/boostorg/boost/releases/download/boost-1.86.0/boost-1.86.0-cmake.tar.xz
- https://github.com/boostorg/boost/releases/download/boost-1.86.0/boost-1.86.0-cmake.tar.xz.txt
