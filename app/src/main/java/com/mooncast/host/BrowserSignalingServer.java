package com.mooncast.host;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** LAN-only receiver page and opaque JSON signaling. Closing revokes this instance's URL. */
final class BrowserSignalingServer implements AutoCloseable {
    interface AssetSource {
        InputStream open(String name) throws IOException;
    }

    interface Handler {
        String offer(String json) throws Exception;
        String status(String sessionId) throws Exception;
        void disconnect(String sessionId) throws Exception;
    }

    /** An intentional API failure; the message must be safe to display to the receiver. */
    static final class RequestException extends Exception {
        final int status;

        RequestException(int status, String message) {
            super(message);
            if (status < 400 || status > 599) throw new IllegalArgumentException("HTTP error status");
            this.status = status;
        }
    }

    private static final int MAX_HEADERS = 8192;
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_ASSET = 1024 * 1024;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; media-src 'self' blob:; img-src 'self' data:; "
            + "base-uri 'none'; frame-ancestors 'none'; form-action 'none'";

    private final Handler handler;
    private final Map<String, byte[]> assets = new HashMap<>();
    private final ServerSocket server;
    private final Thread acceptThread;
    private final String basePath;
    private final int port;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> daemon(runnable, "BrowserSignalingClient"));
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
            runnable -> daemon(runnable, "BrowserSignalingDeadline"));
    private volatile boolean closed;

    BrowserSignalingServer(InetAddress bind, AssetSource source, Handler handler) throws IOException {
        this.handler = Objects.requireNonNull(handler);
        Objects.requireNonNull(source);
        // Only packaged, allowlisted assets are exposed. Read them before opening the listener.
        for (String name : new String[]{"index.html", "receiver.js", "receiver.css"}) {
            try (InputStream in = source.open(name)) {
                if (in == null) throw new IOException("Missing browser asset: " + name);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_ASSET) throw new IOException("Browser asset too large");
                    bytes.write(buffer, 0, count);
                }
                assets.put(name, bytes.toByteArray());
            }
        }
        byte[] token = new byte[24];
        new SecureRandom().nextBytes(token);
        basePath = "/" + Base64.getUrlEncoder().withoutPadding().encodeToString(token) + "/";
        server = new ServerSocket();
        try {
            server.bind(new InetSocketAddress(Objects.requireNonNull(bind), 0), 8);
        } catch (IOException | RuntimeException failure) {
            server.close();
            throw failure;
        }
        port = server.getLocalPort();
        deadlines.setRemoveOnCancelPolicy(true);
        acceptThread = daemon(this::accept, "BrowserSignalingAccept");
        acceptThread.start();
    }

    int port() { return port; }
    String path() { return basePath; }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private void accept() {
        while (!closed) {
            try {
                Socket socket = server.accept();
                synchronized (this) {
                    if (closed) { closeSocket(socket); return; }
                    sockets.add(socket);
                    // Also bounds a stalled response write or a handler that exceeds its budget.
                    ScheduledFuture<?> expiry = deadlines.schedule(() -> closeSocket(socket), 30, TimeUnit.SECONDS);
                    long headerDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MS);
                    try {
                        workers.execute(() -> serve(socket, headerDeadline, expiry));
                    } catch (RejectedExecutionException full) {
                        expiry.cancel(false);
                        sockets.remove(socket);
                        closeSocket(socket);
                    }
                }
            } catch (IOException failure) {
                if (!closed) close();
            }
        }
    }

    private void serve(Socket socket, long headerDeadline, ScheduledFuture<?> expiry) {
        try (Socket owned = socket) {
            InputStream in = owned.getInputStream();
            OutputStream out = owned.getOutputStream();
            boolean head = false;
            try {
                String request = line(in, socket, headerDeadline);
                if (request == null) return;
                String[] first = request.split(" ", -1);
                if (first.length != 3 || !first[0].matches("[A-Z]+") || !first[2].matches("HTTP/1\\.[01]"))
                    throw new RequestException(400, "Malformed request");
                head = first[0].equals("HEAD");
                Map<String, String> headers = new HashMap<>();
                int total = request.length() + 2;
                for (;;) {
                    String row = line(in, socket, headerDeadline);
                    if (row == null) throw new EOFException();
                    total += row.length() + 2;
                    if (total > MAX_HEADERS) throw new RequestException(431, "Headers too large");
                    if (row.isEmpty()) break;
                    int colon = row.indexOf(':');
                    if (colon < 1 || !row.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))
                        throw new RequestException(400, "Malformed header");
                    String value = row.substring(colon + 1).trim();
                    for (int i = 0; i < value.length(); i++) {
                        if (value.charAt(i) < 32 || value.charAt(i) == 127)
                            throw new RequestException(400, "Malformed header");
                    }
                    if (headers.put(row.substring(0, colon).toLowerCase(Locale.ROOT), value) != null)
                        throw new RequestException(400, "Duplicate header");
                }
                checkOrigin(headers);
                if (headers.containsKey("transfer-encoding")) throw new RequestException(400, "Transfer encoding is unsupported");
                int length = contentLength(headers.get("content-length"));
                String target = requestPath(first[1]);
                if (!target.startsWith(basePath)) throw new RequestException(404, "Link not found or expired");
                String route = target.substring(basePath.length());
                if (route.isEmpty() || assets.containsKey(route)) {
                    if (!head && !first[0].equals("GET")) {
                        reply(out, 405, "application/json; charset=utf-8", jsonError("Method not allowed"), head, "GET, HEAD");
                        return;
                    }
                    if (length > 0) throw new RequestException(400, "Unexpected request body");
                    String name = route.isEmpty() ? "index.html" : route;
                    String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                            : name.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8";
                    reply(out, 200, type, assets.get(name), head, null);
                    return;
                }
                if (route.equals("session")) {
                    if (!first[0].equals("POST")) {
                        reply(out, 405, "application/json; charset=utf-8", jsonError("Method not allowed"), head, "POST");
                        return;
                    }
                    if (length < 0) throw new RequestException(411, "Content-Length required");
                    String type = headers.get("content-type");
                    if (type == null || !type.toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;\\s*charset=utf-8)?"))
                        throw new RequestException(415, "Use application/json with UTF-8");
                    if (length == 0) throw new RequestException(400, "Empty request body");
                    byte[] bytes = new byte[length];
                    long bodyDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MS);
                    int offset = 0;
                    while (offset < bytes.length) {
                        setReadTimeout(socket, bodyDeadline);
                        int count = in.read(bytes, offset, bytes.length - offset);
                        if (count < 0) throw new EOFException();
                        offset += count;
                    }
                    String body;
                    try {
                        body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                    } catch (CharacterCodingException invalid) {
                        throw new RequestException(400, "Invalid UTF-8");
                    }
                    if (closed) return;
                    sendJson(out, handler.offer(body));
                    return;
                }
                if (length > 0) throw new RequestException(400, "Unexpected request body");
                if (route.startsWith("status/") && validId(route.substring(7))) {
                    if (!first[0].equals("GET")) {
                        reply(out, 405, "application/json; charset=utf-8", jsonError("Method not allowed"), head, "GET");
                        return;
                    }
                    if (!closed) sendJson(out, handler.status(route.substring(7)));
                    return;
                }
                if (route.startsWith("session/") && validId(route.substring(8))) {
                    if (!first[0].equals("DELETE")) {
                        reply(out, 405, "application/json; charset=utf-8", jsonError("Method not allowed"), head, "DELETE");
                        return;
                    }
                    if (closed) return;
                    handler.disconnect(route.substring(8));
                    reply(out, 204, null, new byte[0], false, null);
                    return;
                }
                throw new RequestException(404, "Not found");
            } catch (RequestException failure) {
                reply(out, failure.status, "application/json; charset=utf-8", jsonError(failure.getMessage()), head, null);
            } catch (SocketTimeoutException timeout) {
                reply(out, 408, "application/json; charset=utf-8", jsonError("Request timed out"), head, null);
            } catch (EOFException incomplete) {
                reply(out, 400, "application/json; charset=utf-8", jsonError("Incomplete request"), head, null);
            } catch (Exception failure) {
                // Do not expose SDP, local addresses or native exception details.
                reply(out, 500, "application/json; charset=utf-8", jsonError("Receiver request failed"), head, null);
            }
        } catch (IOException ignored) {
            // A client may leave, or the owner may stop sharing, at any point.
        } finally {
            expiry.cancel(false);
            sockets.remove(socket);
        }
    }

    private void checkOrigin(Map<String, String> headers) throws RequestException {
        String host = authority(headers.get("host"));
        if (host == null) throw new RequestException(403, "Use the phone's local IP address");
        if ("cross-site".equalsIgnoreCase(headers.get("sec-fetch-site")))
            throw new RequestException(403, "Cross-site request refused");
        String origin = headers.get("origin");
        if (origin == null) return; // Non-browser clients and ordinary page navigations omit Origin.
        try {
            URI uri = new URI(origin);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !"".equals(uri.getRawPath()) || !host.equals(authority(uri.getRawAuthority())))
                throw new RequestException(403, "Cross-origin request refused");
        } catch (URISyntaxException invalid) {
            throw new RequestException(403, "Invalid Origin");
        }
    }

    /** Accept IP literals/localhost only; never resolve a supplied hostname through DNS. */
    private String authority(String value) {
        if (value == null) return null;
        String host;
        String portText;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 2 || end + 1 >= value.length() || value.charAt(end + 1) != ':') return null;
            host = value.substring(1, end);
            if (!host.contains(":") || !host.matches("[0-9A-Fa-f:.]+")) return null;
            try { InetAddress.getByName(host); } catch (IOException invalid) { return null; }
            host = "[" + host.toLowerCase(Locale.ROOT) + "]";
            portText = value.substring(end + 2);
        } else {
            int colon = value.indexOf(':');
            if (colon < 1) return null;
            host = value.substring(0, colon).toLowerCase(Locale.ROOT);
            portText = value.substring(colon + 1);
            if (!host.equals("localhost")) {
                if (!host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return null;
                for (String part : host.split("\\.")) {
                    if (part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) return null;
                    if (Integer.parseInt(part) > 255) return null;
                }
            }
        }
        if (!portText.matches("[0-9]{1,5}") || Integer.parseInt(portText) != port) return null;
        return host + ":" + port;
    }

    private static String requestPath(String target) throws RequestException {
        try {
            URI uri = new URI(target);
            if (!target.startsWith("/") || target.startsWith("//") || uri.getRawFragment() != null)
                throw new RequestException(400, "Invalid request target");
            return uri.getRawPath(); // Deliberately do not URL-decode path traversal or route delimiters.
        } catch (URISyntaxException invalid) {
            throw new RequestException(400, "Invalid request target");
        }
    }

    private static int contentLength(String value) throws RequestException {
        if (value == null) return -1;
        if (!value.matches("[0-9]+")) throw new RequestException(400, "Invalid Content-Length");
        try {
            long parsed = Long.parseLong(value);
            if (parsed > MAX_BODY) throw new RequestException(413, "Request body too large");
            return (int) parsed;
        } catch (NumberFormatException overflow) {
            throw new RequestException(413, "Request body too large");
        }
    }

    private static boolean validId(String value) { return value.matches("[A-Za-z0-9_-]{1,128}"); }

    private static void setReadTimeout(Socket socket, long deadline) throws IOException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new SocketTimeoutException();
        socket.setSoTimeout((int) Math.min(READ_TIMEOUT_MS, remaining));
    }

    private static String line(InputStream in, Socket socket, long deadline) throws IOException, RequestException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() <= MAX_HEADERS) {
            setReadTimeout(socket, deadline);
            int next = in.read();
            if (next < 0) { if (bytes.size() == 0) return null; throw new EOFException(); }
            if (next == '\r') {
                setReadTimeout(socket, deadline);
                if (in.read() != '\n') throw new RequestException(400, "Malformed line ending");
                return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
            }
            if (next == '\n' || next == 0) throw new RequestException(400, "Malformed request line");
            bytes.write(next);
        }
        throw new RequestException(431, "Headers too large");
    }

    private static void sendJson(OutputStream out, String json) throws IOException, RequestException {
        if (json == null) throw new RequestException(503, "Session unavailable");
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_BODY) throw new RequestException(500, "Signaling response too large");
        reply(out, 200, "application/json; charset=utf-8", body, false, null);
    }

    private static byte[] jsonError(String message) {
        String value = message == null ? "Request failed" : message;
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') escaped.append('\\').append(c);
            else if (c < 32) escaped.append(' ');
            else escaped.append(c);
        }
        return ("{\"error\":\"" + escaped + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static void reply(OutputStream out, int status, String type, byte[] body, boolean head, String allow) throws IOException {
        String reason;
        switch (status) {
            case 200: reason = "OK"; break;
            case 204: reason = "No Content"; break;
            case 400: reason = "Bad Request"; break;
            case 403: reason = "Forbidden"; break;
            case 404: reason = "Not Found"; break;
            case 405: reason = "Method Not Allowed"; break;
            case 408: reason = "Request Timeout"; break;
            case 409: reason = "Conflict"; break;
            case 410: reason = "Gone"; break;
            case 411: reason = "Length Required"; break;
            case 413: reason = "Content Too Large"; break;
            case 415: reason = "Unsupported Media Type"; break;
            case 431: reason = "Request Header Fields Too Large"; break;
            case 503: reason = "Service Unavailable"; break;
            default: reason = "Request Failed";
        }
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\nConnection: close\r\n"
                + "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: " + POLICY + "\r\n"
                + "Permissions-Policy: camera=(), microphone=(), geolocation=()\r\n"
                + (status == 204 ? "" : "Content-Length: " + body.length + "\r\n")
                + (type == null ? "" : "Content-Type: " + type + "\r\n")
                + (allow == null ? "" : "Allow: " + allow + "\r\n") + "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        if (!head && status != 204) out.write(body);
    }

    private static void closeSocket(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        synchronized (this) {
            if (!closed) {
                closed = true;
                try { server.close(); } catch (IOException ignored) { }
                for (Socket socket : sockets) closeSocket(socket);
                workers.shutdownNow();
                deadlines.shutdownNow();
            }
        }
        if (Thread.currentThread() != acceptThread) {
            try { acceptThread.join(1000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}
