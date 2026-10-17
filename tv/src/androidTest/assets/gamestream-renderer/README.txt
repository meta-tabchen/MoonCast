Generated renderer test media. No third-party clip or personal media is included.

red-blue-160x96.h264: two H.264 baseline 8-bit YUV420 pictures at 160x96.
Left half is synthesized red; right half is synthesized blue. First picture is
IDR with separate SPS/PPS NALs; second is a P picture. Encoder SEI is ignored.
SHA256: 72f46a6785b2ac3cce827517df3ea915f70166bff1e0262ccd6e9e68b459fe61

Generated with the already-installed FFmpeg 7.1.5 / libx264:
ffmpeg -hide_banner -loglevel error -f lavfi \
  -i 'color=c=red:s=160x96:r=30,drawbox=x=80:y=0:w=80:h=96:color=blue:t=fill' \
  -frames:v 2 -c:v libx264 -profile:v baseline -level:v 3.0 -pix_fmt yuv420p \
  -tune zerolatency -x264-params 'keyint=60:min-keyint=60:scenecut=0:bframes=0:threads=1' \
  -f h264 red-blue-160x96.h264

This fixture is packaged only in the androidTest APK. It does not demonstrate
GameStream transport, pairing, sender compatibility, or native Opus decoding.

Reconnect fixture blue-red-160x96.h264.base64 was generated in the cloud with
FFmpeg 7.1.5-0+deb13u1 / libx264, using the command above with blue/red swapped.
The ASCII asset decodes to 741 bytes; decoded H.264 SHA256:
cd08af91899e4a141e1f52ad1a3c522cf4109fa90a726403c657d8221bf6c398
The AirPlay session test changes pattern on reconnect so old queued pictures
cannot satisfy second-session pixel assertions. This is generated fixture media.
