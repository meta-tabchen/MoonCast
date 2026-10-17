package com.mooncast.host;

/** Compile-only targets for paths deliberately excluded from this isolated module.
 * The actual CastControls, ScreenDimmer and ManualControlState are compiled unchanged.
 * Physical panel support is compile-time false; any accidental use must fail loudly. */
final class PanelRecoveryClient {
 PanelRecoveryClient(android.app.Service service,boolean root){throw new AssertionError("Physical panel is excluded from this fixture");}
 void disconnected(){throw new AssertionError();}void restore(){throw new AssertionError();}
 void requestOff(){throw new AssertionError();}void close(){throw new AssertionError();}
}
class MainActivity extends android.app.Activity { /* Notification content target is not exercised. */ }
