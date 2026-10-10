package com.mooncast.tv.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual LAN UPnP MediaRenderer: SSDP, bounded HTTP/SOAP and GENA events.
 * No account, pairing or encryption is provided by UPnP. Only enable on a trusted LAN.
 * Own one instance from the foreground service. Stop/start on interface/address changes.
 * The Android owner must hold a Wi-Fi multicast lock while this receiver is running. */
public final class DlnaReceiver implements AutoCloseable {
    public interface Listener {
        void onStatus(String message);
        void onError(String message);
    }
    private static final String DEVICE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static final String SERVER = "Android/1.0 UPnP/1.0 MoonCastTV/0.1";
    private static final String XML = "text/xml; charset=\"utf-8\"";
    private final PlaybackTarget player;
    private final String name, uuid;
    private final Listener listener;
    private volatile Session session;

    public DlnaReceiver(PlaybackTarget target, String friendlyName, String identity, Listener listener) {
        if (target == null || friendlyName == null || friendlyName.trim().isEmpty() || friendlyName.length() > 160) {
            throw new IllegalArgumentException("Player and a bounded receiver name are required");
        }
        player = target; name = friendlyName.trim();
        uuid = "uuid:" + UUID.fromString(identity.startsWith("uuid:") ? identity.substring(5) : identity);
        this.listener = listener;
    }
    public synchronized void start(Inet4Address address, int prefixLength) throws IOException {
        stop();
        Session next = new Session(address, prefixLength, true);
        session = next;
        try { next.begin(); }
        catch (IOException | RuntimeException e) { session = null; next.close(); throw e; }
        status("DLNA listening on " + getDescriptionUrl());
    }
    // Loopback fixture runs the actual HTTP protocol without taking host SSDP port 1900.
    synchronized void startForTest(Inet4Address address, int prefixLength) throws IOException {
        stop(); Session next = new Session(address, prefixLength, false); session = next; next.begin();
    }
    public synchronized void stop() {
        Session old = session; session = null;
        if (old != null) old.close();
    }
    @Override public void close() { stop(); }
    public boolean isRunning() { Session s = session; return s != null && !s.closed; }
    public int getPort() { Session s = session; return s == null ? -1 : s.http.getLocalPort(); }
    public String getDescriptionUrl() { Session s = session; return s == null ? "" : s.location; }
    private void status(String message) { if (listener != null) try { listener.onStatus(message); } catch (RuntimeException ignored) {} }
    private void error(String message) { if (listener != null) try { listener.onError(message); } catch (RuntimeException ignored) {} }
    private static ThreadFactory threads(String prefix) { return r -> { Thread t = new Thread(r, prefix); t.setDaemon(true); return t; }; }

    private final class Session implements AutoCloseable {
        final Inet4Address address;
        final LanAccess access;
        final ServerSocket http;
        final String location, expectedHost;
        final Map<String, UpnpService> services = new LinkedHashMap<>();
        final Map<Socket, Long> sockets = new ConcurrentHashMap<>();
        final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
        final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(12), threads("DlnaHttp"));
        final ThreadPoolExecutor events = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), threads("DlnaEvent"));
        final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, threads("DlnaTimer"));
        final boolean discovery;
        volatile boolean closed;
        MulticastSocket ssdp;
        NetworkInterface network;
        InetAddress group;
        Thread acceptThread, discoveryThread;
        long lastSearchNanos;

        Session(Inet4Address address, int prefix, boolean discovery) throws IOException {
            this.address = address; access = new LanAccess(address, prefix); this.discovery = discovery;
            timer.setRemoveOnCancelPolicy(true);
            timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            for (String service : new String[]{UpnpService.AV, UpnpService.RC, UpnpService.CM}) services.put(service, new UpnpService(service, player));
            http = new ServerSocket();
            try { http.bind(new InetSocketAddress(address, 0), 16); }
            catch (IOException e) { http.close(); workers.shutdownNow(); events.shutdownNow(); timer.shutdownNow(); throw e; }
            expectedHost = address.getHostAddress() + ":" + http.getLocalPort();
            location = "http://" + expectedHost + "/description.xml";
        }
        void begin() throws IOException {
            if (discovery) {
                network = NetworkInterface.getByInetAddress(address);
                if (network == null) throw new IOException("Selected network interface is unavailable");
                group = InetAddress.getByName("239.255.255.250");
                ssdp = new MulticastSocket(null);
                ssdp.setReuseAddress(true);
                ssdp.bind(new InetSocketAddress(1900));
                ssdp.setNetworkInterface(network);
                ssdp.setTimeToLive(2);
                ssdp.joinGroup(new InetSocketAddress(group, 1900), network);
                discoveryThread = threads("DlnaSsdp").newThread(this::discover);
                discoveryThread.start();
            }
            acceptThread = threads("DlnaAccept").newThread(this::accept);
            acceptThread.start();
            timer.scheduleWithFixedDelay(this::tick, 200, 500, TimeUnit.MILLISECONDS);
            if (discovery) timer.scheduleWithFixedDelay(() -> announce("ssdp:alive"), 0, 120, TimeUnit.SECONDS);
        }
        void accept() {
            while (!closed) try {
                Socket socket = http.accept();
                if (closed || !access.allows(socket.getInetAddress())) { quietClose(socket); continue; }
                socket.setSoTimeout(3000);
                sockets.put(socket, System.nanoTime() + 10_000_000_000L);
                try { workers.execute(() -> serve(socket)); }
                catch (RejectedExecutionException e) { sockets.remove(socket); quietClose(socket); }
            } catch (IOException e) {
                if (!closed) { error("DLNA listener stopped: " + e.getClass().getSimpleName()); close(); }
            }
        }
        void serve(Socket socket) {
            try (Socket client = socket) {
                ReceiverHttp.Request request;
                try { request = ReceiverHttp.read(client); }
                catch (IOException | IllegalArgumentException e) { reply(client, 400, "", ""); return; }
                // Literal Host blocks ordinary browser DNS-rebinding requests. Origin is rejected in the parser.
                if (!expectedHost.equals(request.headers.get("host"))) { reply(client, 403, "", ""); return; }
                if (closed) return;
                boolean head = request.method.equals("HEAD");
                if ((request.method.equals("GET") || head) && request.body.length == 0) {
                    String document = null;
                    if (request.path.equals("/description.xml")) document = deviceDescription();
                    for (UpnpService service : services.values()) if (request.path.equals("/scpd/" + service.name + ".xml")) document = service.description();
                    if (document == null) reply(client, 404, "", "");
                    else ReceiverHttp.reply(client.getOutputStream(), 200, XML, "", document, head);
                    return;
                }
                UpnpService control = serviceAt(request.path, "/control/");
                if (control != null && request.method.equals("POST")) {
                    String content = request.headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT);
                    if (!content.startsWith("text/xml") && !content.startsWith("application/xml")) { reply(client, 400, "", ""); return; }
                    try { reply(client, 200, "EXT:\r\n", control.control(request.body, request.headers.get("soapaction"))); }
                    catch (UpnpService.Fault e) { reply(client, 500, "", UpnpService.fault(e)); }
                    catch (RuntimeException e) { reply(client, 500, "", UpnpService.fault(new UpnpService.Fault(501, "Action Failed"))); }
                    return;
                }
                UpnpService event = serviceAt(request.path, "/event/");
                if (event != null && (request.method.equals("SUBSCRIBE") || request.method.equals("UNSUBSCRIBE"))) {
                    subscription(client, request, event); return;
                }
                reply(client, 404, "", "");
            } catch (IOException ignored) {} finally { sockets.remove(socket); }
        }
        void reply(Socket socket, int code, String extra, String body) throws IOException {
            ReceiverHttp.reply(socket.getOutputStream(), code, XML, extra, body, false);
        }
        UpnpService serviceAt(String path, String prefix) {
            if (!path.startsWith(prefix)) return null;
            return services.get(path.substring(prefix.length()));
        }
        String deviceDescription() {
            StringBuilder list = new StringBuilder();
            for (UpnpService service : services.values()) {
                list.append("<service>").append(UpnpService.tag("serviceType", service.type))
                        .append(UpnpService.tag("serviceId", "urn:upnp-org:serviceId:" + service.name))
                        .append(UpnpService.tag("SCPDURL", "/scpd/" + service.name + ".xml"))
                        .append(UpnpService.tag("controlURL", "/control/" + service.name))
                        .append(UpnpService.tag("eventSubURL", "/event/" + service.name)).append("</service>");
            }
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\">"
                    + "<specVersion><major>1</major><minor>0</minor></specVersion><device>"
                    + UpnpService.tag("deviceType", DEVICE) + UpnpService.tag("friendlyName", name)
                    + "<manufacturer>MoonCast</manufacturer><modelName>MoonCast TV</modelName><modelNumber>0.1</modelNumber>"
                    + UpnpService.tag("UDN", uuid) + "<serviceList>" + list + "</serviceList></device></root>";
        }
        synchronized void subscription(Socket socket, ReceiverHttp.Request request, UpnpService service) throws IOException {
            if (request.body.length != 0) { reply(socket, 400, "", ""); return; }
            String sid = request.headers.get("sid");
            boolean unsubscribe = request.method.equals("UNSUBSCRIBE");
            if (sid != null) {
                Subscription sub = subscriptions.get(sid);
                if (request.headers.containsKey("callback") || request.headers.containsKey("nt") || sub == null
                        || sub.service != service || !sub.peer.equals(socket.getInetAddress()) || sub.expires <= System.nanoTime()) {
                    reply(socket, 412, "", ""); return;
                }
                if (unsubscribe) { subscriptions.remove(sid); sub.close(); reply(socket, 200, "", ""); return; }
                int timeout = timeoutSeconds(request.headers.get("timeout"));
                sub.expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
                reply(socket, 200, "SID: " + sid + "\r\nTIMEOUT: Second-" + timeout + "\r\n", ""); return;
            }
            if (unsubscribe || !"upnp:event".equalsIgnoreCase(request.headers.get("nt"))) { reply(socket, 412, "", ""); return; }
            URI callback;
            try { callback = LanAccess.callback(request.headers.get("callback"), socket.getInetAddress()); }
            catch (IllegalArgumentException e) { reply(socket, 412, "", ""); return; }
            if (subscriptions.size() >= 16) { reply(socket, 503, "", ""); return; }
            int timeout = timeoutSeconds(request.headers.get("timeout"));
            Subscription sub = new Subscription(callback, socket.getInetAddress(), service, timeout);
            subscriptions.put(sub.id, sub);
            try {
                reply(socket, 200, "SID: " + sub.id + "\r\nTIMEOUT: Second-" + timeout + "\r\n", "");
                sub.ready = true;
            } catch (IOException e) { subscriptions.remove(sub.id); sub.close(); throw e; }
        }
        int timeoutSeconds(String value) {
            if (value == null || value.equalsIgnoreCase("Second-infinite")) return 1800;
            if (!value.matches("(?i)Second-[0-9]{1,9}")) return 300;
            return Math.max(30, Math.min(1800, Integer.parseInt(value.substring(7))));
        }
        void tick() {
            if (closed) return;
            long now = System.nanoTime();
            for (Map.Entry<Socket, Long> entry : sockets.entrySet()) if (entry.getValue() <= now) { quietClose(entry.getKey()); sockets.remove(entry.getKey()); }
            Map<UpnpService, String> current = new LinkedHashMap<>();
            for (Subscription sub : subscriptions.values()) {
                if (sub.expires <= now) { subscriptions.remove(sub.id); sub.close(); continue; }
                if (!sub.ready || sub.pending.get()) continue;
                String body;
                try {
                    body = current.get(sub.service);
                    if (body == null) { body = sub.service.eventBody(); current.put(sub.service, body); }
                } catch (RuntimeException e) { continue; }
                if (body.equals(sub.delivered) || !sub.pending.compareAndSet(false, true)) continue;
                String payload = body;
                try { events.execute(() -> sub.send(payload)); }
                catch (RejectedExecutionException e) { sub.pending.set(false); }
            }
        }
        List<String> searchTypes() {
            List<String> types = new ArrayList<>(Arrays.asList("upnp:rootdevice", uuid, DEVICE));
            for (UpnpService service : services.values()) types.add(service.type);
            return types;
        }
        String usn(String type) { return type.equals(uuid) ? uuid : uuid + "::" + type; }
        void discover() {
            byte[] bytes = new byte[4096];
            while (!closed) try {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length); ssdp.receive(packet);
                if (!access.allows(packet.getAddress()) || packet.getLength() >= bytes.length || packet.getPort() < 1) continue;
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.US_ASCII);
                Map<String, String> headers = parseSearch(message);
                if (headers == null) continue;
                long now = System.nanoTime();
                // Global cap also bounds amplification and work under spoofed/rotating source addresses.
                if (now - lastSearchNanos < 100_000_000L) continue;
                lastSearchNanos = now;
                String target = headers.get("st");
                for (String type : searchTypes()) if (target.equals("ssdp:all") || target.equals(type)) {
                    String response = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=300\r\nEXT:\r\nLOCATION: " + location
                            + "\r\nSERVER: " + SERVER + "\r\nST: " + type + "\r\nUSN: " + usn(type) + "\r\n\r\n";
                    sendDatagram(response, packet.getAddress(), packet.getPort());
                }
            } catch (IOException | RuntimeException e) {
                if (!closed) { error("DLNA discovery stopped: " + e.getClass().getSimpleName()); close(); }
            }
        }
        void announce(String kind) {
            if (ssdp == null || ssdp.isClosed()) return;
            for (String type : searchTypes()) {
                String packet = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: " + type
                        + "\r\nNTS: " + kind + "\r\nUSN: " + usn(type) + "\r\n"
                        + (kind.equals("ssdp:alive") ? "CACHE-CONTROL: max-age=300\r\nLOCATION: " + location + "\r\nSERVER: " + SERVER + "\r\n" : "") + "\r\n";
                try { sendDatagram(packet, group, 1900); } catch (IOException ignored) {}
            }
        }
        void sendDatagram(String message, InetAddress target, int port) throws IOException {
            byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
            ssdp.send(new DatagramPacket(bytes, bytes.length, target, port));
        }
        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                announce("ssdp:byebye");
                if (ssdp != null) ssdp.close();
                try { http.close(); } catch (IOException ignored) {}
                for (Subscription sub : subscriptions.values()) sub.close();
                subscriptions.clear();
                for (Socket socket : sockets.keySet()) quietClose(socket);
                sockets.clear(); timer.shutdownNow(); workers.shutdownNow(); events.shutdownNow();
            }
            // On Linux the native listener descriptor may remain open until its blocked accept returns.
            // Never join while holding the session monitor or from the joined thread itself.
            joinStopped(acceptThread);
            joinStopped(discoveryThread);
        }
        void joinStopped(Thread thread) {
            if (thread != null && thread != Thread.currentThread()) try { thread.join(1000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        final class Subscription implements AutoCloseable {
            final String id = "uuid:" + UUID.randomUUID();
            final URI callback;
            final InetAddress peer;
            final UpnpService service;
            final AtomicBoolean pending = new AtomicBoolean();
            volatile long expires;
            volatile boolean ready;
            volatile Socket connection;
            volatile String delivered;
            long sequence;
            Subscription(URI callback, InetAddress peer, UpnpService service, int seconds) {
                this.callback=callback; this.peer=peer; this.service=service;
                expires=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
            }
            void send(String body) {
                Socket socket = new Socket();
                connection=socket;
                try (Socket client=socket) {
                    if (closed || subscriptions.get(id)!=this) return;
                    sockets.put(client,System.nanoTime()+5_000_000_000L);
                    int port=callback.getPort()==-1?80:callback.getPort();
                    client.bind(new InetSocketAddress(address,0));
                    client.connect(new InetSocketAddress(peer,port),1500); client.setSoTimeout(1500);
                    if (closed || subscriptions.get(id)!=this) return;
                    byte[] payload=body.getBytes(StandardCharsets.UTF_8);
                    String path=callback.getRawPath(); if(path==null || path.isEmpty()) path="/";
                    if(callback.getRawQuery()!=null) path+="?"+callback.getRawQuery();
                    String header="NOTIFY "+path+" HTTP/1.1\r\nHOST: "+peer.getHostAddress()+":"+port
                            +"\r\nCONTENT-TYPE: text/xml; charset=\"utf-8\"\r\nCONTENT-LENGTH: "+payload.length
                            +"\r\nNT: upnp:event\r\nNTS: upnp:propchange\r\nSID: "+id+"\r\nSEQ: "+sequence+"\r\nConnection: close\r\n\r\n";
                    client.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().write(payload); client.getOutputStream().flush();
                    String response=ReceiverHttp.line(client.getInputStream(),client,System.nanoTime()+1_500_000_000L);
                    if(!response.matches("HTTP/1\\.[01] 2[0-9][0-9](?: .*)?")) throw new IOException("Event rejected");
                    delivered=body;
                    sequence=sequence==0xffffffffL?1:sequence+1;
                } catch(IOException | RuntimeException e) { subscriptions.remove(id,this); }
                finally { sockets.remove(socket); connection=null; pending.set(false); }
            }
            @Override public void close() { ready=false; Socket s=connection; if(s!=null) quietClose(s); }
        }
    }
    static Map<String,String> parseSearch(String message) {
        if(message.length()>4096 || !message.startsWith("M-SEARCH * HTTP/1.1\r\n") || !message.endsWith("\r\n\r\n")) return null;
        Map<String,String> headers=new LinkedHashMap<>();
        String[] lines=message.split("\r\n");
        for(int i=1;i<lines.length;i++) {
            int colon=lines[i].indexOf(':'); if(colon<=0) return null;
            String name=lines[i].substring(0,colon).toLowerCase(Locale.ROOT);
            if(headers.put(name,lines[i].substring(colon+1).trim())!=null) return null;
        }
        if(!"\"ssdp:discover\"".equalsIgnoreCase(headers.get("man")) || !headers.containsKey("st")
                || !headers.getOrDefault("mx","").matches("[1-5]") || !"239.255.255.250:1900".equals(headers.get("host"))) return null;
        return headers;
    }
    private static void quietClose(Socket socket) { try { socket.close(); } catch(IOException ignored) {} }
}
