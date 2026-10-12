package android.net.nsd;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
public class NsdServiceInfo {
    private String type, name; private InetAddress host; private int port;
    private final Map<String, byte[]> attributes = new LinkedHashMap<>();
    public String getServiceType() { return type; } public void setServiceType(String t) { type = t; }
    public String getServiceName() { return name; } public void setServiceName(String n) { name = n; }
    public InetAddress getHost() { return host; } public void setHost(InetAddress h) { host = h; }
    public int getPort() { return port; } public void setPort(int p) { port = p; }
    public Map<String, byte[]> getAttributes() { return attributes; }
    public void setAttribute(String k, String v) { attributes.put(k, v.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
