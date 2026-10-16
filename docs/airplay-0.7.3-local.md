# Local HTTP mirroring follow-up / 本地镜像修正

0.7.3/code60, com.mooncast.community, existing fixed community signer.
No GitHub/Library upload, release or messaging is authorized for this follow-up.

User evidence: newer attempts reach SETUP/session status501 over RTSP. The user
reports Mirroring360 on the same Android phone and iPhone mirroring work on the
same TV. A separate static analysis of SenderBasic suggests legacy7100 streaming;
the installed user app version and live route are not packet-captured here.
No proprietary implementation code is copied into MoonCast.

The new branch only probes the chosen numeric LAN host at7100 and selects it
when stream.xml is a valid bounded plist with plausible dimensions/version.
No redirects or host scan. Unsupported/invalid probes preserve the existing
modern path; authentication denials remain errors/challenges. The independently
written HTTP branch negotiates optional FairPlay using existing reviewed
primitives, retains Digest authentication, writes a binary /stream preamble,
then complete Annex-B access units as AVCC config and length-prefixed video.
AES-CTR remains continuous across video payloads when negotiated. A required
crypto mode is never bypassed. Responses never forward keys or receiver bodies.

Baseline H264, <=1280x720, <=30fps and <=4Mbps are selected for this initial
video-only path. Audio is not advertised. Clock responses bind7010 on the
selected route's local address and only answer the chosen receiver. Cancellation
closes concrete sockets and unblocks writes. Errors say HTTP/1.1 and the actual
legacy stage; the modern RTSP diagnostic path remains separate.

Public protocol references (format facts, not copied vendor implementation):
- https://nto.github.io/AirPlay.html#screenmirroring-httprequests
- https://github.com/espes/Slave-in-the-Magic-Mirror/blob/master/airplay.py
- https://www.mirroring360.com/android

Local mock evidence includes a generated20-frame128x72 H264 fixture, a receiver
that validates the actual HTTP/plist/header/AVCC wire bytes, FFmpeg decoding of
all20 reconstructed frames, clock origin timestamps, heartbeats, Digest auth,
required-encryption refusal, continuous CTR, blocked-write cancellation and
focused race checks. Existing modern SETUP and lifecycle regressions remain.
These checks are NOT a physical Android capture, TV pairing or TV display pass.

本版仅本地交付。验证是mock接收与真实H264解码，不宣称用户电视已成功投屏。
不读取/上传真实配对密钥，不改电视或网络设置，也不忽略现代SETUP错误假装成功。
