package com.mooncast.transfer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Foreground, single-use LAN receiver. Has no Android installer or remote URL functionality. */
public final class ApkTransferServer implements Closeable {
    public interface Listener {
        void onProgress(long received, long total);
        void onReceived(File file, String sha256);
        void onError(String message);
    }
    public static final class Session {
        public final int port;
        public final String receiverId;
        public final long expiresAtMillis;
        Session(int port, String id, long expires) {
            this.port = port; this.receiverId = id; this.expiresAtMillis = expires;
        }
    }
    private final File directory;
    private final InetAddress bindAddress;
    private final Predicate<InetAddress> peerAllowed;
    private final Listener listener;
    private final String receiverId;
    private final Set<Socket> sockets = Collections.synchronizedSet(new HashSet<Socket>());
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(2), r -> { Thread t = new Thread(r, "apk-request"); t.setDaemon(true); return t; });
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "apk-expiry"); t.setDaemon(true); return t;
    });
    private volatile boolean closed, completed;
    private boolean uploading, consumed;
    private long requestWindow;
    private int requestCount;
    private Session session;
    private ServerSocket server;
    private Thread acceptThread;
    private volatile File partial;
    private long deadlineNanos;
    public ApkTransferServer(File privateDir, InetAddress bindAddress,
                             Predicate<InetAddress> peerAllowed, String receiverId, Listener listener) {
        if (privateDir == null || bindAddress == null || peerAllowed == null || listener == null)
            throw new IllegalArgumentException("Required receiver configuration missing");
        if (receiverId == null || !receiverId.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Receiver UUID required");
        this.receiverId = receiverId;
        this.directory = privateDir; this.bindAddress = bindAddress;
        this.peerAllowed = peerAllowed; this.listener = listener;
    }
    public synchronized Session start() throws IOException {
        if (session != null || closed) throw new IOException("Receiver cannot be restarted");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create private staging directory");
        server = new ServerSocket(); server.setReuseAddress(true);
        try { server.bind(new InetSocketAddress(bindAddress, ApkTransfer.PORT), 4); }
        catch (IOException failure) {
            close(); throw new IOException("APK port 48152 is unavailable. Close the other APK receiver and reopen this screen.", failure);
        }
        session = new Session(ApkTransfer.PORT, receiverId, System.currentTimeMillis() + ApkTransfer.SESSION_MILLIS);
        deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ApkTransfer.SESSION_MILLIS);
        timer.schedule(() -> { if (!closed) { boolean notify = !completed; close(); if (notify) safeError("Receive session expired. Open a new session on TV."); } },
            ApkTransfer.SESSION_MILLIS, TimeUnit.MILLISECONDS);
        acceptThread = new Thread(this::acceptLoop, "apk-accept"); acceptThread.setDaemon(true); acceptThread.start();
        return session;
    }
    private void acceptLoop() {
        while (!closed) {
            Socket socket = null;
            try {
                socket = server.accept();
                if (!peerAllowed.test(socket.getInetAddress())) { socket.close(); continue; }
                socket.setSoTimeout(15000);
                synchronized (this) {
                    // close() may race the return from accept or the peer check. Never
                    // register a socket after close has finished clearing the owned set.
                    if (closed) { socket.close(); break; }
                    sockets.add(socket);
                    final Socket accepted = socket;
                    try { workers.execute(() -> handle(accepted)); }
                    catch (RejectedExecutionException busy) { sockets.remove(socket); socket.close(); }
                }
            } catch (IOException e) { if (!closed) { close(); safeError("Receiver connection failed"); } }
        }
    }
    private void handle(Socket socket) {
        try (Socket ignored = socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            try {
                limitRequests();
                Request request = readRequest(in);
                if ("GET".equals(request.method) && "/info".equals(request.path)) {
                    if (request.length != 0) throw new HttpError(400,"Info request must have no body");
                    respond(out,200,info(),"application/json; charset=utf-8");
                } else if ("PUT".equals(request.method) && "/v1/apk".equals(request.path)) {
                    receive(request, in, out);
                } else throw new HttpError(404,"Unknown endpoint");
            } catch (HttpError e) { respond(out,e.status,e.getMessage()); }
              catch (IOException e) { respond(out,400,"Transfer rejected or interrupted"); }
        } catch (IOException ignored) { /* Disconnection always reaches receive's cleanup. */ }
        finally { sockets.remove(socket); }
    }
    private synchronized void limitRequests() throws HttpError {
        checkAlive();
        long now=System.nanoTime();
        if(now-requestWindow >= TimeUnit.MINUTES.toNanos(1)){requestWindow=now;requestCount=0;}
        if(++requestCount>60)throw new HttpError(429,"Too many requests; wait before trying again");
    }
    private synchronized String info() throws HttpError {
        checkAlive();
        String state=uploading?"receiving":consumed?"unavailable":"ready";
        return "{\"protocol\":2,\"apk\":true,\"pairing\":false,\"receiverId\":\""+receiverId
            +"\",\"port\":48152,\"maxBytes\":1073741824,\"state\":\""+state+"\"}";
    }
    private synchronized void admit() throws HttpError {
        checkAlive();
        if (uploading || consumed) throw new HttpError(409,"Only one APK per receive session");
        uploading = true; consumed = true;
    }
    private synchronized void checkAlive() throws HttpError {
        if (closed || System.nanoTime() >= deadlineNanos) throw new HttpError(410,"Receive session expired or closed");
    }
    private void receive(Request r, InputStream in, OutputStream out) throws IOException {
        if (r.length <= 0 || r.length > ApkTransfer.MAX_BYTES) throw new HttpError(413,"APK exceeds 1 GiB limit");
        String name;
        try { name = URLDecoder.decode(r.headers.getOrDefault("x-apk-name", ""), "UTF-8"); }
        catch (IllegalArgumentException e) { throw new HttpError(400,"Invalid filename"); }
        ApkTransfer.checkName(name);
        String expected = r.headers.get("x-apk-sha256");
        if (expected == null || !expected.matches("[a-fA-F0-9]{64}")) throw new HttpError(400,"SHA-256 required");
        admit();
        File file = null; boolean successful = false;
        try {
            if (directory.getUsableSpace() < r.length + ApkTransfer.FREE_RESERVE_BYTES)
                throw new HttpError(507,"Insufficient free space on TV");
            file = File.createTempFile("mooncast-apk-", ".part", directory); partial = file;
            MessageDigest digest = ApkTransfer.sha256(); long received = 0; long lastProgress = 0;
            try (OutputStream dest = new BufferedOutputStream(new FileOutputStream(file))) {
                byte[] buffer = new byte[65536];
                while (received < r.length) {
                    checkAlive();
                    int n = in.read(buffer,0,(int)Math.min(buffer.length,r.length-received));
                    if (n < 0) throw new EOFException("Incomplete APK");
                    dest.write(buffer,0,n); digest.update(buffer,0,n); received += n;
                    if (received == r.length || received-lastProgress >= 256*1024) {
                        listener.onProgress(received,r.length); lastProgress = received;
                    }
                }
            }
            String hash = ApkTransfer.hex(digest.digest());
            if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII), expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
                throw new HttpError(422,"APK checksum mismatch");
            ApkTransfer.checkContainer(file);
            synchronized (this) {
                checkAlive();
                File ready = new File(directory, file.getName().replace(".part", ".apk"));
                if (!file.renameTo(ready)) throw new IOException("Cannot finalize APK");
                file = ready; partial = null; completed = true; successful = true;
            }
            // Flush acknowledgement before UI callback can close this receiver. Local delivery
            // remains authoritative even when the phone disconnects before reading the response.
            try { respond(out,200,"received"); }
            finally {
                try { listener.onReceived(file,hash); }
                catch (RuntimeException callbackFailure) {
                    file.delete(); safeError("APK could not be delivered to the TV review screen");
                }
            }
        } finally {
            if (!successful) {
                if (file != null) file.delete(); partial = null;
                safeError("APK transfer failed or was canceled. Open a new receive session to retry.");
            }
            // Each explicit receive window accepts one attempt, including failures.
            synchronized (this) { uploading = false; }
        }
    }
    private static Request readRequest(InputStream in) throws IOException {
        int[] budget = {16384}; String line = readLine(in,budget);
        String[] first = line.split(" ",-1);
        if (first.length != 3 || !"HTTP/1.1".equals(first[2])) throw new HttpError(400,"HTTP/1.1 required");
        Map<String,String> headers = new HashMap<>();
        for (int count = 0; ; count++) {
            line = readLine(in,budget); if (line.isEmpty()) break;
            if (count >= 32) throw new HttpError(431,"Too many headers");
            int colon = line.indexOf(':');
            if (colon <= 0) throw new HttpError(400,"Invalid header");
            String key = line.substring(0,colon).toLowerCase(Locale.ROOT);
            if (!key.matches("[a-z0-9-]+") || headers.put(key,line.substring(colon+1).trim()) != null)
                throw new HttpError(400,"Invalid or duplicate header");
        }
        if (headers.containsKey("transfer-encoding") || headers.containsKey("expect"))
            throw new HttpError(400,"Chunked or expect requests unsupported");
        String length = headers.get("content-length");
        if (length == null && "GET".equals(first[0])) length = "0";
        if (length == null || !length.matches("[0-9]{1,12}")) throw new HttpError(411,"Fixed Content-Length required");
        return new Request(first[0],first[1],headers,Long.parseLong(length));
    }
    private static String readLine(InputStream in,int[] budget) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (--budget[0] >= 0 && bytes.size() <= 4096) {
            int b = in.read(); if (b == -1) throw new EOFException();
            if (b == '\r') { if (in.read() != '\n') throw new HttpError(400,"CRLF required"); budget[0]--; return bytes.toString("US-ASCII"); }
            if (b < 32 || b > 126) throw new HttpError(400,"Invalid HTTP characters"); bytes.write(b);
        }
        throw new HttpError(431,"Headers too large");
    }
    private static void readFully(InputStream in,byte[] bytes) throws IOException {
        int offset = 0; while (offset < bytes.length) { int n=in.read(bytes,offset,bytes.length-offset); if(n<0)throw new EOFException();offset+=n; }
    }
    private static void respond(OutputStream out,int status,String body) throws IOException {
        respond(out,status,body,"text/plain; charset=utf-8");
    }
    private static void respond(OutputStream out,int status,String body,String contentType) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: "+contentType+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);out.flush();
    }
    private void safeError(String message) { try { listener.onError(message); } catch (RuntimeException ignored) {} }
    @Override public void close() {
        final Thread accepting;
        synchronized (this) {
            if (!closed) {
                closed=true;
                if (server != null) try { server.close(); } catch(IOException ignored) {}
                synchronized(sockets) { for(Socket s:sockets)try{s.close();}catch(IOException ignored){} }
                workers.shutdownNow();timer.shutdownNow(); File f=partial;if(f!=null)f.delete();
            }
            accepting=acceptThread;
        }
        // Linux/JDK may retain the listening descriptor until a blocked accept unwinds,
        // even after ServerSocket.close returns. Wait for that owner before allowing an
        // immediate new receive window on the same fixed port. This is not a bind retry.
        // Do not hold the lifecycle/socket locks, join ourselves, or let an interrupted
        // caller skip the release acknowledgement. A blocked peer callback is bounded.
        if (accepting!=null && accepting!=Thread.currentThread()) {
            boolean interrupted=false;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
            while (accepting.isAlive()) {
                long remaining=deadline-System.nanoTime();
                if (remaining<=0) break;
                try { TimeUnit.NANOSECONDS.timedJoin(accepting,remaining); }
                catch (InterruptedException e) { interrupted=true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    private static final class Request {
        final String method,path;final Map<String,String> headers;final long length;
        Request(String method,String path,Map<String,String>headers,long length){this.method=method;this.path=path;this.headers=headers;this.length=length;}
    }
    private static final class HttpError extends IOException {
        final int status;HttpError(int status,String message){super(message);this.status=status;}
    }
}
