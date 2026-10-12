package com.mooncast.tv.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Portable HTTP media transport. Validates every open/redirect, including nested playlist resources.
 * HttpURLConnection resolves again at connect time: destination checking reduces accidental/straightforward
 * SSRF, but is not DNS-rebinding-proof. Native loopback access is a narrow, revocable port exception.
 * No cookie jar, authentication challenge, file/content scheme or DRM transport is intentionally provided. */
final class MediaHttpConnection implements AutoCloseable {
    private static final int TIMEOUT_MS=8000, MAX_REDIRECTS=5;
    private static final long MAX_FALLBACK_SKIP=8L*1024*1024;
    private static final Pattern RANGE=Pattern.compile("bytes ([0-9]+)-([0-9]+)/(?:([0-9]+)|\\*)");
    private static final ThreadPoolExecutor DNS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"TvMediaDns");t.setDaemon(true);return t;});
    private final IntSupplier loopbackAllowance;
    private HttpURLConnection connection;
    private InputStream stream;
    private URI uri;
    private Map<String,List<String>> headers=Collections.emptyMap();
    private long remaining=-1;
    private int authorizedPort=-1;
    static final class PositionException extends IOException {
        private static final long serialVersionUID=1L;
        PositionException(String message){super(message);}
    }
    MediaHttpConnection(IntSupplier loopbackAllowance) { this.loopbackAllowance=loopbackAllowance; }
    URI uri(){return uri;}
    Map<String,List<String>> headers(){return headers;}

    long open(String value,long position,long length,Map<String,String> requestHeaders,boolean head) throws IOException {
        close();
        if(position<0 || length< -1 || (length>0 && position>Long.MAX_VALUE-length)) throw new IOException("Invalid media range");
        Map<String,String> safeHeaders=validateHeaders(requestHeaders);
        URI target;
        try{target=URI.create(value);}catch(IllegalArgumentException e){throw new IOException("Invalid media URL",e);}
        try {
            for(int redirect=0;redirect<=MAX_REDIRECTS;redirect++) {
                authorizedPort=validateDestination(target,loopbackAllowance.getAsInt());
                HttpURLConnection next=(HttpURLConnection)target.toURL().openConnection(Proxy.NO_PROXY);
                connection=next;
                next.setInstanceFollowRedirects(false);
                next.setConnectTimeout(TIMEOUT_MS);next.setReadTimeout(TIMEOUT_MS);next.setUseCaches(false);
                next.setRequestMethod(head?"HEAD":"GET");
                next.setRequestProperty("User-Agent","MoonCastTV/0.1");
                next.setRequestProperty("Accept-Encoding","identity");
                next.setRequestProperty("Connection","close");
                for(Map.Entry<String,String> header:safeHeaders.entrySet()) next.setRequestProperty(header.getKey(),header.getValue());
                if(position>0 || length>0) next.setRequestProperty("Range","bytes="+position+"-"+(length>0?Long.toString(position+length-1):""));
                checkLoopbackPermission();
                int status=next.getResponseCode();
                headers=boundedHeaders(next.getHeaderFields());
                if(status==301 || status==302 || status==303 || status==307 || status==308) {
                    String location=singleHeader("location");
                    if(location==null || location.length()>8192 || redirect==MAX_REDIRECTS) throw new IOException("Media redirect limit or missing target");
                    URI redirected;
                    try{redirected=target.resolve(location);}catch(IllegalArgumentException e){throw new IOException("Invalid media redirect",e);}
                    if("https".equalsIgnoreCase(target.getScheme()) && "http".equalsIgnoreCase(redirected.getScheme())) throw new IOException("Insecure media redirect refused");
                    next.disconnect();connection=null;target=redirected;continue;
                }
                uri=target;
                if(status==416) {
                    String range=singleHeader("content-range");
                    if(range!=null && range.equals("bytes */"+position)) { remaining=0;return 0; }
                    throw new PositionException("Media range is outside the resource");
                }
                if(status!=200 && status!=206 && status!=204) throw new IOException("Media HTTP response "+status);
                String encoding=singleHeader("content-encoding");
                boolean gzip=encoding!=null && encoding.equalsIgnoreCase("gzip") && position==0 && length<0 && status==200;
                if(encoding!=null && !encoding.equalsIgnoreCase("identity") && !gzip) throw new IOException("Unexpected encoded or ranged media response");
                long contentLength=parseLength(singleHeader("content-length"));
                String transfer=singleHeader("transfer-encoding");
                if(transfer!=null && !transfer.equalsIgnoreCase("chunked")) throw new IOException("Unsupported media transfer encoding");
                if(transfer!=null && contentLength>=0) throw new IOException("Ambiguous media response framing");
                long skip=status==200?position:0;
                if(status==206) {
                    String range=singleHeader("content-range");
                    Matcher m=RANGE.matcher(range==null?"":range);
                    if(!m.matches()) throw new IOException("Missing media Content-Range");
                    try {
                        long start=Long.parseLong(m.group(1)),end=Long.parseLong(m.group(2));
                        if(start!=position || end<start || end==Long.MAX_VALUE || (m.group(3)!=null && Long.parseLong(m.group(3))<=end)) throw new IOException("Inconsistent media Content-Range");
                        long rangeLength=end-start+1;
                        if(contentLength>=0 && contentLength!=rangeLength) throw new IOException("Inconsistent media response length");
                        contentLength=rangeLength;
                    }catch(NumberFormatException e){throw new IOException("Oversized media range",e);}
                }
                if(head || status==204 || length==0) {remaining=0;return 0;}
                if(contentLength>=0 && skip>contentLength) throw new PositionException("Media position beyond end");
                if(skip>MAX_FALLBACK_SKIP) throw new IOException("Media server does not support byte-range seeking");
                stream=next.getInputStream();
                if(gzip){stream=new GZIPInputStream(stream);contentLength=-1;}
                skipFully(skip);
                long available=contentLength<0?-1:contentLength-skip;
                remaining=length<0?available:available<0?length:Math.min(length,available);
                return remaining;
            }
            throw new IOException("Too many media redirects");
        }catch(IOException | RuntimeException e){close();if(e instanceof IOException)throw (IOException)e;throw new IOException("Media request rejected",e);}
    }
    int read(byte[] buffer,int offset,int count) throws IOException {
        if(count==0)return 0;
        checkLoopbackPermission();
        if(remaining==0)return -1;
        if(stream==null)throw new IOException("Media source is not open");
        int n=stream.read(buffer,offset,remaining<0?count:(int)Math.min(remaining,count));
        if(n<0) {if(remaining>0)throw new EOFException("Truncated media response");return -1;}
        if(remaining>0)remaining-=n;
        return n;
    }
    private void skipFully(long bytes) throws IOException {
        byte[] buffer=new byte[8192];long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        while(bytes>0){checkLoopbackPermission();if(System.nanoTime()>deadline)throw new IOException("Media skip deadline exceeded");int n=stream.read(buffer,0,(int)Math.min(bytes,buffer.length));if(n<0)throw new PositionException("Media position beyond end");bytes-=n;}
    }
    private void checkLoopbackPermission() throws IOException {
        if(authorizedPort>=0 && loopbackAllowance.getAsInt()!=authorizedPort)throw new IOException("Native media session ended");
    }
    static int validateDestination(URI uri,int nativePort) throws IOException {
        String host=uri.getHost();
        boolean nativeLoopback="http".equalsIgnoreCase(uri.getScheme()) && nativePort>0 && nativePort<=65535
                && uri.getPort()==nativePort && ("localhost".equalsIgnoreCase(host)||"127.0.0.1".equals(host))
                && uri.getRawUserInfo()==null && uri.getFragment()==null;
        if(nativeLoopback){if(uri.toString().length()>8192)throw new IOException("Oversized media URL");return nativePort;}
        try{LanAccess.validateMediaUri(uri.toString());}catch(IllegalArgumentException e){throw new IOException("Media URL is not permitted",e);}
        InetAddress[] addresses=resolve(host);
        if(addresses.length==0)throw new UnknownHostException("No media addresses");
        for(InetAddress address:addresses)validateResolvedAddress(address);
        return -1;
    }
    static void validateResolvedAddress(InetAddress address) throws IOException {
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isMulticastAddress())throw new IOException("Media DNS resolved to a forbidden endpoint");
        byte[] b=address.getAddress();
        if(b.length==4 && ((b[0]&255)==0 || (b[0]&255)>=224 || ArraysEqual4(b,169,254,169,254) || ArraysEqual4(b,100,100,100,200)))throw new IOException("Invalid media destination");
        if(b.length==16 && (b[0]&255)==0xfd && (b[1]&255)==0 && (b[2]&255)==0x0e && (b[3]&255)==0xc2
                && (b[14]&255)==2 && (b[15]&255)==0x54) {
            boolean zero=true;for(int i=4;i<14;i++)if(b[i]!=0)zero=false;
            if(zero)throw new IOException("Infrastructure media destination refused");
        }
    }
    private static boolean ArraysEqual4(byte[] b,int a,int c,int d,int e){return(b[0]&255)==a&&(b[1]&255)==c&&(b[2]&255)==d&&(b[3]&255)==e;}
    private static InetAddress[] resolve(String host) throws IOException {
        Future<InetAddress[]> future;
        try{future=DNS.submit(()->InetAddress.getAllByName(host));}catch(RejectedExecutionException e){throw new IOException("Media DNS is busy",e);}
        try{return future.get(TIMEOUT_MS,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();future.cancel(true);throw new IOException("Media resolution interrupted",e);}
        catch(TimeoutException e){future.cancel(true);throw new IOException("Media resolution timed out",e);}
        catch(ExecutionException e){throw new IOException("Media resolution failed",e.getCause());}
    }
    private static Map<String,String> validateHeaders(Map<String,String> input) throws IOException {
        if(input.size()>32)throw new IOException("Too many media request headers");
        Map<String,String> output=new LinkedHashMap<>();int length=0;
        for(Map.Entry<String,String> h:input.entrySet()){
            String name=h.getKey(),value=h.getValue();
            if(name==null||value==null||!name.matches("[A-Za-z0-9-]{1,64}")||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.length()>4096)throw new IOException("Invalid media header");
            String lower=name.toLowerCase(Locale.ROOT);
            if(lower.equals("host")||lower.equals("range")||lower.equals("connection")||lower.equals("accept-encoding")||lower.equals("content-length")||lower.equals("transfer-encoding")||lower.equals("authorization")||lower.equals("cookie")||lower.startsWith("proxy-"))throw new IOException("Reserved media header");
            length+=name.length()+value.length();if(length>16384)throw new IOException("Media headers too large");output.put(name,value);
        }
        return output;
    }
    private static Map<String,List<String>> boundedHeaders(Map<String,List<String>> input) throws IOException {
        if(input.size()>64)throw new IOException("Too many media response headers");
        Map<String,List<String>> output=new LinkedHashMap<>();int total=0;
        for(Map.Entry<String,List<String>> h:input.entrySet()){
            if(h.getKey()==null)continue;
            String key=h.getKey().toLowerCase(Locale.ROOT);List<String> values=new ArrayList<>();
            for(String v:h.getValue()){total+=key.length()+v.length();if(total>32768||values.size()>=16)throw new IOException("Media response headers too large");values.add(v);}
            if(output.put(key,Collections.unmodifiableList(values))!=null)throw new IOException("Duplicate media header names");
        }
        return Collections.unmodifiableMap(output);
    }
    private String singleHeader(String name) throws IOException {
        List<String> values=headers.get(name);if(values==null||values.isEmpty())return null;
        if(values.size()!=1)throw new IOException("Ambiguous media header "+name);return values.get(0).trim();
    }
    private static long parseLength(String value) throws IOException {
        if(value==null)return -1;if(!value.matches("[0-9]{1,19}"))throw new IOException("Invalid media Content-Length");
        try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("Oversized media length",e);}
    }
    @Override public void close() {
        HttpURLConnection old=connection;connection=null;
        if(old!=null)old.disconnect();
        InputStream input=stream;stream=null;if(input!=null)try{input.close();}catch(IOException ignored){}
        uri=null;headers=Collections.emptyMap();remaining=-1;authorizedPort=-1;
    }
}
