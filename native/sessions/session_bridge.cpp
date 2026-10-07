// SPDX-License-Identifier: GPL-3.0-only
// The opaque handle is valid only while guarded by NativeSessions' callback registry.
#include <jni.h>
#include <dlfcn.h>
#include <cstdint>

namespace stream { struct session_t; }
using Stop = void (*)(stream::session_t &);
static Stop resolve() {
    void *library = dlopen("libsunshine.so", RTLD_NOW | RTLD_NOLOAD);
    if (!library) return nullptr;
    auto stop = reinterpret_cast<Stop>(dlsym(library, "_ZN6stream7session4stopERNS_9session_tE"));
    dlclose(library);
    return stop;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_jqssun_displaymirror_sunshine_NativeSessions_availableNative(JNIEnv *, jclass) {
    return resolve() ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_jqssun_displaymirror_sunshine_NativeSessions_stopNative(JNIEnv *, jclass, jlong handle) {
    auto stop = resolve();
    if (!stop || !handle) return JNI_FALSE;
    stop(*reinterpret_cast<stream::session_t *>(static_cast<uintptr_t>(handle)));
    return JNI_TRUE;
}
