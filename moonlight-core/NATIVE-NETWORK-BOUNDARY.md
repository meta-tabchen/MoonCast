# Selected-peer network boundary

Audited source: moonlight-common-c 874ac9548f1bd6f095ef2b435c42cdde460e7821.
Paths below are beneath its src/ directory. The app must validate a selected
literal IPv4 on its current LAN; the facade additionally refuses public/DNS targets
and forces STREAM_CFG_LOCAL. This note concerns native outbound destinations,
not inbound UDP sender authentication or host trustworthiness.

- NvConnection passes context.serverAddress.address to MoonBridge.startConnection
  separately from the launch response's rtspSessionUrl. The JNI bridge places them
  in distinct SERVER_INFORMATION fields; the URL does not replace address.
- Connection.c:264 copies only serverInfo->address to RemoteAddrString.
  Lines351–372 resolve only serverInfo->address into RemoteAddr. Alternate RTSP
  ports and fallback ports still probe this selected address.
- Connection.c:182–208 parses only a 1–65535 port from the URL (otherwise48010).
  The native parser accepts a numeric prefix; this is not a strict URL validator.
  It cannot make a different hostname become a socket destination.
- RtspConnection.c:407 connects TCP to RemoteAddr/RtspPortNumber; lines1015–1016
  do the same for ENet. The URL's authority supplies RTSP header/target text only
  (lines973–986). rtspenc:// selects RTSP encryption (line955), not an external
  scheme handler. No browser or general URL client is invoked for these URLs.
- AudioStream.c:44 and VideoStream.c:61 copy RemoteAddr and change only the
  negotiated UDP port. Their sendto calls use those copies. Video first-frame
  TCP also uses RemoteAddr (VideoStream.c:363).
- ControlStream.c:1767 and1843 use RemoteAddr for ENet/TCP. Negotiated control,
  audio and video ports never supply another peer address.
- STREAM_CFG_LOCAL avoids the native AUTO address classification/STUN path.
  HTTPS redirects are disabled, and paired HTTP never downgrades on a pin failure.

Therefore a returned RTSP URL containing a public or different hostname cannot
redirect RTSP/RTP/control sockets away from the selected peer. Ports are still
host-selected protocol metadata on that peer. We preserve upstream URL semantics
rather than adding an incompatible scheme/authority rule (some upstream audio
negotiation intentionally uses a 0.0.0.0 target string).

This is source review against a locked revision, not a packet-capture or hostile
host runtime test. The CI source inventory prevents silently replacing this code;
re-audit this boundary when changing common-C or its JNI/Java caller.
