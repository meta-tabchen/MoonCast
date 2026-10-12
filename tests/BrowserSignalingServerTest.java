package com.mooncast.host;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real TCP listener and HTTP parser, without Android or a WebRTC runtime. */
public final class BrowserSignalingServerTest {
    private static int checks;
    private static final BrowserSignalingServer.AssetSource ASSETS = name ->
            new ByteArrayInputStream(("packaged:" + name).getBytes(StandardCharsets.UTF_8));

    private static final class Response {
        final String headers;
        final byte[] body;
        Response(String headers, byte[] body) { this.headers = headers; this.body = body; }
        String text() { return new String(body, StandardCharsets.UTF_8); }
        void status(int expected) { require(headers.startsWith("HTTP/1.1 " + expected + " "), "HTTP " + expected + ": " + headers); }
    }

    private static class Handler implements BrowserSignalingServer.Handler {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> offer = new AtomicReference<>();
        final AtomicReference<String> status = new AtomicReference<>();
        final AtomicReference<String> disconnected = new AtomicReference<>();
        @Override public String offer(String json) throws Exception {
            calls.incrementAndGet(); offer.set(json); return "{\"type\":\"answer\",\"sdp\":\"synthetic\"}";
        }
        @Override public String status(String id) throws Exception {
            calls.incrementAndGet(); status.set(id); return "{\"state\":\"connected\"}";
        }
        @Override public void disconnect(String id) throws Exception { calls.incrementAndGet(); disconnected.set(id); }
    }

    public static void main(String[] args) throws Exception {
        routesAndPolicy();
        originGuards();
        parserAndLimits();
        handlerErrors();
        requestDeadlines();
        shutdownAndCapacity();
        System.out.println("PASS: browser signaling " + checks + " checks (TCP routes, opaque SDP JSON, origin/Host guards, parser limits, errors, bounded clients and revocation)");
    }

    private static void routesAndPolicy() throws Exception {
        Handler handler = new Handler();
        try (BrowserSignalingServer server = server(handler)) {
            require(server.path().matches("/[A-Za-z0-9_-]{32}/"), "192-bit URL token");
            Response page = request(server, "GET", "", "", null);
            page.status(200);
            require(page.text().equals("packaged:index.html"), "index bytes");
            require(page.headers.contains("Cache-Control: no-store") && page.headers.contains("Referrer-Policy: no-referrer"), "token privacy headers");
            require(page.headers.contains("script-src 'self'") && page.headers.contains("style-src 'self'") && !page.headers.contains("unsafe-inline"), "self-only script/style policy");
            require(page.headers.contains("media-src 'self' blob:") && page.headers.contains("connect-src 'self'"), "receiver media/connect policy");
            require(!page.headers.toLowerCase().contains("access-control-allow-origin"), "no CORS grants");
            require(page.headers.contains("camera=(), microphone=()"), "receiver needs no local capture permission");
            require(request(server, "GET", "index.html", "", null).text().equals(page.text()), "explicit index route");
            require(request(server, "GET", "?lang=zh", "", null).text().equals(page.text()), "page query allowed");
            Response script = request(server, "GET", "receiver.js", "", null);
            script.status(200);
            require(script.headers.contains("text/javascript") && script.text().equals("packaged:receiver.js"), "script bytes and MIME");
            Response css = request(server, "GET", "receiver.css", "", null);
            css.status(200);
            require(css.headers.contains("text/css") && css.text().equals("packaged:receiver.css"), "style bytes and MIME");
            Response head = request(server, "HEAD", "receiver.js", "", null);
            head.status(200);
            require(head.body.length == 0 && head.headers.contains("Content-Length: " + script.body.length), "HEAD without body");
            for (String route : new String[]{"../index.html", "%2e%2e/index.html", "private.txt", "status/", "status/a/b", "session/%61", "status/" + "a".repeat(129)})
                request(server, "GET", route, "", null).status(404);
            raw(server.port(), "GET /wrong/ HTTP/1.1\r\n" + host(server) + "\r\n", null).status(404);
            Response missingHead = request(server, "HEAD", "missing", "", null);
            missingHead.status(404);
            require(missingHead.body.length == 0, "HEAD errors also omit response body");
            request(server, "GET", "session", "", null).status(405);
            require(request(server, "POST", "index.html", "", null).headers.contains("Allow: GET, HEAD"), "allow header");
            require(handler.calls.get() == 0, "static/error routes do not invoke media handler");
            String offer = "{\"type\":\"offer\",\"sdp\":\"v=0\\r\\n测试\",\"previousSessionId\":null}";
            Response answer = request(server, "POST", "session", "Content-Type: application/json; charset=UTF-8\r\n", offer.getBytes(StandardCharsets.UTF_8));
            answer.status(200);
            require(answer.text().contains("\"answer\"") && offer.equals(handler.offer.get()), "opaque UTF-8 SDP JSON reaches handler intact");
            request(server, "GET", "status/test-session_2", "", null).status(200);
            require(handler.status.get().equals("test-session_2"), "status session id");
            Response deleted = request(server, "DELETE", "session/test-session_2", "Content-Length: 0\r\n", null);
            deleted.status(204);
            require(deleted.body.length == 0 && !deleted.headers.contains("Content-Length:"), "204 has no body or content length");
            require(handler.disconnected.get().equals("test-session_2"), "disconnect session id");
            require(handler.calls.get() == 3, "only authorized routes invoke handler");
        }
        try {
            new BrowserSignalingServer(InetAddress.getLoopbackAddress(), name -> { throw new IOException("missing"); }, new Handler());
            throw new AssertionError("missing assets accepted");
        } catch (IOException expected) { checks++; }
    }

    private static void originGuards() throws Exception {
        Handler handler = new Handler();
        try (BrowserSignalingServer server = server(handler)) {
            String local = "localhost:" + server.port();
            request(server, "GET", "", "Origin: http://" + local + "\r\nSec-Fetch-Site: same-origin\r\n", null).status(200);
            for (String allowed : new String[]{"127.0.0.1", "LOCALHOST", "[::1]"}) {
                raw(server.port(), "GET " + server.path() + " HTTP/1.1\r\nHost: " + allowed + ":" + server.port()
                        + "\r\nOrigin: http://" + allowed + ":" + server.port() + "\r\n\r\n", null).status(200);
            }
            for (String origin : new String[]{"null", "http://evil.example", "http://127.0.0.1:" + server.port(),
                    "https://" + local, "http://" + local + "/", "http://" + local + "?query", "http://" + local + "#fragment",
                    "http://user@" + local, "http://localhost:1", "http://" + local + ".evil", "garbage"})
                request(server, "POST", "session", "Origin: " + origin + "\r\nContent-Type: application/json\r\n", new byte[]{'{', '}'}).status(403);
            request(server, "GET", "", "Sec-Fetch-Site: cross-site\r\n", null).status(403);
            for (String host : new String[]{"evil.example", "localhost.evil.example", "localhost.", "127.1", "127.0.0.999",
                    "0127.0.0.1", "127.0.0.01", "0x7f000001", "2130706433", "[fe80::1%25en0]", "[:::]", "user@localhost", "localhost/"})
                raw(server.port(), "GET " + server.path() + " HTTP/1.1\r\nHost: " + host + ":" + server.port() + "\r\n\r\n", null).status(403);
            raw(server.port(), "GET " + server.path() + " HTTP/1.1\r\nHost: localhost:1\r\n\r\n", null).status(403);
            raw(server.port(), "GET " + server.path() + " HTTP/1.1\r\n\r\n", null).status(403);
            require(handler.calls.get() == 0, "cross-origin requests never reach handler");
        }
    }

    private static void parserAndLimits() throws Exception {
        Handler handler = new Handler();
        try (BrowserSignalingServer server = server(handler)) {
            String post = "POST " + server.path() + "session HTTP/1.1\r\n" + host(server);
            String get = "GET " + server.path() + " HTTP/1.1\r\n" + host(server);
            raw(server.port(), post + "Content-Type: application/json\r\n\r\n", null).status(411);
            request(server, "POST", "session", "Content-Type: application/json\r\n", new byte[0]).status(400);
            request(server, "POST", "session", "Content-Type: text/plain\r\n", new byte[]{'{', '}'}).status(415);
            request(server, "POST", "session", "", new byte[]{'{', '}'}).status(415);
            request(server, "POST", "session", "Content-Type: application/json\r\n", new byte[]{(byte) 0xc3, 0x28}).status(400);
            for (String value : new String[]{"-1", "+2", "1, 1", "a"})
                raw(server.port(), post + "Content-Length: " + value + "\r\n\r\n", null).status(400);
            for (String value : new String[]{"65537", "9999999999999999999999999999999999999999"})
                raw(server.port(), post + "Content-Length: " + value + "\r\n\r\n", null).status(413);
            raw(server.port(), post + "Content-Length: 2\r\nContent-Length: 2\r\n\r\n", null).status(400);
            raw(server.port(), get + host(server) + "\r\n", null).status(400);
            raw(server.port(), post + "Transfer-Encoding: chunked\r\nContent-Length: 2\r\n\r\n", null).status(400);
            raw(server.port(), get + "X-Test: " + "a".repeat(8193) + "\r\n\r\n", null).status(431);
            StringBuilder many = new StringBuilder(get);
            for (int i = 0; i < 200; i++) many.append("X-").append(i).append(": ").append("a".repeat(40)).append("\r\n");
            raw(server.port(), many + "\r\n", null).status(431);
            raw(server.port(), get + " Bad: value\r\n\r\n", null).status(400);
            raw(server.port(), get + "Bad : value\r\n\r\n", null).status(400);
            raw(server.port(), "GET / HTTP/2.0\r\n\r\n", null).status(400);
            raw(server.port(), "GET http://localhost:" + server.port() + server.path() + " HTTP/1.1\r\n" + host(server) + "\r\n", null).status(400);
            raw(server.port(), "GET " + server.path() + "#fragment HTTP/1.1\r\n" + host(server) + "\r\n", null).status(400);
            raw(server.port(), "GET " + server.path() + " HTTP/1.1\n", null).status(400);
            request(server, "GET", "", "", new byte[]{'{', '}'}).status(400);
            require(handler.calls.get() == 0, "bad requests rejected before handler");
            byte[] max = new byte[65536];
            Arrays.fill(max, (byte) ' '); max[0] = '{'; max[max.length - 1] = '}';
            request(server, "POST", "session", "Content-Type: application/json\r\n", max).status(200);
            require(handler.offer.get().length() == 65536, "exact maximum body accepted without truncation");
            try (Socket incomplete = connect(server.port())) {
                incomplete.getOutputStream().write((post + "Content-Length: 2\r\nContent-Type: application/json\r\n\r\n{").getBytes(StandardCharsets.US_ASCII));
                incomplete.shutdownOutput();
                response(incomplete).status(400);
            }
        }
    }

    private static void handlerErrors() throws Exception {
        try (BrowserSignalingServer server = server(new Handler() {
            @Override public String offer(String json) throws Exception { throw new BrowserSignalingServer.RequestException(409, "Viewer \"busy\"\\retry\nsoon"); }
            @Override public String status(String id) throws Exception {
                if (id.equals("missing")) return null;
                if (id.equals("large")) return "x".repeat(65537);
                throw new IllegalStateException("private-sdp-details");
            }
        })) {
            Response conflict = request(server, "POST", "session", "Content-Type: application/json\r\n", new byte[]{'{', '}'});
            conflict.status(409);
            require(conflict.text().equals("{\"error\":\"Viewer \\\"busy\\\"\\\\retry soon\"}"), "safe JSON error escaping");
            request(server, "GET", "status/missing", "", null).status(503);
            Response unexpected = request(server, "GET", "status/private", "", null);
            unexpected.status(500);
            require(!unexpected.text().contains("private-sdp-details"), "no private exception details exposed");
            request(server, "GET", "status/large", "", null).status(500);
        }
    }

    private static void shutdownAndCapacity() throws Exception {
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        BrowserSignalingServer server = server(new Handler() {
            @Override public String offer(String json) throws Exception {
                concurrent.incrementAndGet(); entered.countDown();
                try { release.await(); return "{}"; }
                finally { concurrent.decrementAndGet(); }
            }
        });
        int port = server.port();
        String oldPath = server.path();
        List<Socket> pending = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                Socket socket = connect(port); pending.add(socket);
                socket.getOutputStream().write(("POST " + server.path() + "session HTTP/1.1\r\n" + host(server)
                        + "Content-Type: application/json\r\nContent-Length: 2\r\n\r\n{}").getBytes(StandardCharsets.US_ASCII));
            }
            require(entered.await(3, TimeUnit.SECONDS), "four worker requests started");
            for (int i = 0; i < 8; i++) pending.add(connect(port));
            try (Socket rejected = connect(port)) {
                require(rejected.getInputStream().read() == -1, "client queue bounded at eight");
            }
            require(concurrent.get() == 4, "at most four active handlers");
            server.close();
            server.close();
            for (Socket socket : pending) {
                try { require(socket.getInputStream().read() == -1, "close revokes in-flight and queued sockets"); }
                catch (SocketException reset) { checks++; }
            }
        } finally {
            server.close(); release.countDown();
            for (Socket socket : pending) socket.close();
        }
        try (Socket ignored = connect(port)) { throw new AssertionError("closed listener reachable"); }
        catch (ConnectException expected) { checks++; }
        try (BrowserSignalingServer next = server(new Handler())) {
            require(!next.path().equals(oldPath), "restarting rotates token");
            raw(next.port(), "GET " + oldPath + " HTTP/1.1\r\n" + host(next) + "\r\n", null).status(404);
        }
    }

    private static void requestDeadlines() throws Exception {
        Handler handler = new Handler();
        ScheduledExecutorService trickle = Executors.newSingleThreadScheduledExecutor();
        try (BrowserSignalingServer server = server(handler);
             Socket headers = connect(server.port()); Socket body = connect(server.port())) {
            headers.setSoTimeout(14000);
            body.setSoTimeout(14000);
            headers.getOutputStream().write(("GET " + server.path() + " HTTP/1.1\r\n" + host(server)
                    + "X-Trickle:").getBytes(StandardCharsets.US_ASCII));
            body.getOutputStream().write(("POST " + server.path() + "session HTTP/1.1\r\n" + host(server)
                    + "Content-Type: application/json\r\nContent-Length: 20\r\n\r\n{").getBytes(StandardCharsets.US_ASCII));
            long started = System.nanoTime();
            // New bytes at four and eight seconds must not reset the ten-second stage budget.
            Runnable byteAtATime = () -> {
                try { headers.getOutputStream().write(' '); body.getOutputStream().write(' '); }
                catch (IOException ignored) { }
            };
            trickle.schedule(byteAtATime, 4, TimeUnit.SECONDS);
            trickle.schedule(byteAtATime, 8, TimeUnit.SECONDS);
            response(headers).status(408);
            response(body).status(408);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            require(elapsed >= 9000 && elapsed < 14000, "absolute request deadlines survive trickle traffic: " + elapsed);
            require(handler.calls.get() == 0, "timed-out request never invokes handler");
        } finally { trickle.shutdownNow(); }
    }

    private static BrowserSignalingServer server(BrowserSignalingServer.Handler handler) throws IOException {
        return new BrowserSignalingServer(InetAddress.getLoopbackAddress(), ASSETS, handler);
    }

    private static String host(BrowserSignalingServer server) { return "Host: localhost:" + server.port() + "\r\n"; }

    private static Response request(BrowserSignalingServer server, String method, String route, String extra, byte[] body) throws Exception {
        return raw(server.port(), method + " " + server.path() + route + " HTTP/1.1\r\n" + host(server) + extra
                + (body == null ? "" : "Content-Length: " + body.length + "\r\n") + "\r\n", body);
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(3000);
        return socket;
    }

    private static Response raw(int port, String headers, byte[] body) throws Exception {
        try (Socket socket = connect(port)) {
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.ISO_8859_1));
            if (body != null) socket.getOutputStream().write(body);
            return response(socket);
        }
    }

    private static Response response(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int tail = 0;
        while (bytes.size() < 16384) {
            int next = in.read();
            if (next < 0) break;
            bytes.write(next);
            tail = (tail << 8) | next;
            if (tail == 0x0d0a0d0a) {
                String headers = bytes.toString(StandardCharsets.ISO_8859_1);
                int length = 0;
                for (String header : headers.split("\r\n")) {
                    if (header.startsWith("Content-Length: ")) length = Integer.parseInt(header.substring(16));
                }
                // HTTP responses have an explicit size; do not await EOF after a rejection with an unread request body.
                return new Response(headers, in.readNBytes(length));
            }
        }
        throw new AssertionError("No HTTP response");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
