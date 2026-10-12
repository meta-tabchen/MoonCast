# GameStream renderer host fakes

These minimal Android/ABI fakes exercise lifecycle, ownership, queueing and error paths
without loading a native library or requiring a device. They are not decoders, audio
playback, Android compatibility tests, or copied Android implementation code. They
must never enter application source sets. Real SDK/API compilation remains separate.
