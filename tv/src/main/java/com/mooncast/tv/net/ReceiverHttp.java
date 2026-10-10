package com.mooncast.tv.net;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One bounded HTTP/1.x request per connection. No transfer encoding, pipelining or browser CORS API. */
final class ReceiverHttp {
    static final int MAX_BODY = 65536;
    static final class Request {
        final String method, path;
        final Map<String, String> headers;
        final byte[] body;
        Request(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method; this.path = path; this.headers = headers; this.body = body;
        }
    }
    static Request read(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        long deadline = System.nanoTime() + 8_000_000_000L;
        String first = line(in, socket, deadline);
        String[] parts = first.split(" ", -1);
        if (parts.length != 3 || !parts[0].matches("[A-Z-]{1,16}") || !parts[1].startsWith("/")
                || parts[1].length() > 2048 || !parts[2].matches("HTTP/1\\.[01]")) throw new IOException("Invalid request line");
        Map<String, String> headers = new LinkedHashMap<>();
        int total = first.length();
        for (;;) {
            String row = line(in, socket, deadline);
            total += row.length() + 2;
            if (total > 16384 || headers.size() > 48) throw new IOException("Headers too large");
            if (row.isEmpty()) break;
            int colon = row.indexOf(':');
            if (colon <= 0 || !row.substring(0, colon).matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+")) throw new IOException("Invalid header");
            String key = row.substring(0, colon).toLowerCase(Locale.ROOT);
            if (headers.put(key, row.substring(colon + 1).trim()) != null) throw new IOException("Duplicate header");
        }
        if (headers.containsKey("transfer-encoding") || headers.containsKey("origin")
                || headers.containsKey("expect")) throw new IOException("Unsupported request framing or browser origin");
        int length = 0;
        if (headers.containsKey("content-length")) {
            String raw = headers.get("content-length");
            if (!raw.matches("[0-9]{1,6}")) throw new IOException("Invalid content length");
            length = Integer.parseInt(raw);
        }
        if (length > MAX_BODY) throw new IOException("Body too large");
        byte[] body = new byte[length];
        for (int off = 0; off < length;) {
            timeout(socket, deadline);
            int n = in.read(body, off, length - off);
            if (n < 0) throw new EOFException();
            off += n;
        }
        return new Request(parts[0], parts[1], headers, body);
    }
    static String line(InputStream in, Socket socket, long deadline) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (;;) {
            timeout(socket, deadline);
            int b = in.read();
            if (b < 0) throw new EOFException();
            if (b == '\r') {
                if (in.read() != '\n') throw new IOException("Invalid line ending");
                return raw.toString(StandardCharsets.ISO_8859_1.name());
            }
            if (b == '\n' || b == 0 || b < 32 && b != '\t' || b == 127 || raw.size() >= 8192) throw new IOException("Invalid line");
            raw.write(b);
        }
    }
    static void timeout(Socket socket, long deadline) throws IOException {
        long left = (deadline - System.nanoTime()) / 1_000_000L;
        if (left <= 0) throw new java.net.SocketTimeoutException("Request deadline exceeded");
        socket.setSoTimeout((int) Math.min(3000, left));
    }
    static void reply(OutputStream out, int code, String type, String extra, String body, boolean head) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason;
        switch (code) {
            case 200: reason = "OK"; break;
            case 400: reason = "Bad Request"; break;
            case 403: reason = "Forbidden"; break;
            case 404: reason = "Not Found"; break;
            case 405: reason = "Method Not Allowed"; break;
            case 412: reason = "Precondition Failed"; break;
            case 503: reason = "Service Unavailable"; break;
            default: reason = "Internal Server Error";
        }
        out.write(("HTTP/1.1 " + code + " " + reason + "\r\nConnection: close\r\n"
                + "Server: Android/1.0 UPnP/1.0 MoonCastTV/0.1\r\nContent-Type: " + type
                + "\r\nContent-Length: " + bytes.length + "\r\nCache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n" + extra + "\r\n").getBytes(StandardCharsets.US_ASCII));
        if (!head) out.write(bytes);
        out.flush();
    }
}
