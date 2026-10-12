package com.mooncast.transfer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** File-backed client; sockets and absolute timeout allow cancellation of blocked writes. */
public final class ApkTransferClient implements Closeable {
    public interface Progress { void onProgress(long sent,long total); }
    private final InetAddress address;
    private final int port;
    private volatile Socket active;
    private volatile boolean canceled;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r,"apk-client-timeout");t.setDaemon(true);return t;
    });
    public ApkTransferClient(InetAddress address,int port) {
        if(address == null || port<1 || port>65535)throw new IllegalArgumentException("Invalid TV endpoint");
        this.address=address;this.port=port;
    }
    public String info()throws IOException {
        Socket socket=connect();ScheduledFuture<?> timeout=deadline(socket,20000);
        try {headers(socket.getOutputStream(),"GET","/info",0,"");return response(socket);}
        finally {timeout.cancel(false);finish(socket);}
    }
    public void send(File apk,String originalName,String sha256,Progress progress)throws IOException {
        ApkTransfer.checkName(originalName);
        if(!apk.isFile() || apk.length()<=0 || apk.length()>ApkTransfer.MAX_BYTES)throw new IOException("Invalid APK size (maximum 1 GiB)");
        if(sha256==null || !sha256.matches("[a-fA-F0-9]{64}"))throw new IOException("Invalid checksum");
        ApkTransfer.checkContainer(apk);long length=apk.length();
        Socket socket=connect();ScheduledFuture<?> timeout=deadline(socket,ApkTransfer.SESSION_MILLIS);
        try {
            OutputStream out=new BufferedOutputStream(socket.getOutputStream());
            headers(out,"PUT","/v1/apk",length,"X-APK-Name: "+URLEncoder.encode(originalName,"UTF-8")+"\r\nX-APK-SHA256: "+sha256+"\r\nContent-Type: application/vnd.android.package-archive\r\n");
            try(InputStream in=new FileInputStream(apk)) {
                byte[] buffer=new byte[65536];int n;long sent=0,last=0;
                while((n=in.read(buffer))!=-1) {
                    if(canceled || Thread.currentThread().isInterrupted())throw new InterruptedIOException("Transfer canceled");
                    out.write(buffer,0,n);sent+=n;
                    if(progress!=null && (sent==length || sent-last>=256*1024)){progress.onProgress(sent,length);last=sent;}
                }
            }
            out.flush();
            if(!"received".equals(response(socket)))throw new IOException("Invalid receiver response");
        } finally {timeout.cancel(false);finish(socket);}
    }
    private Socket connect()throws IOException {
        Socket socket=new Socket();
        synchronized(this){
            if(canceled){socket.close();throw new InterruptedIOException("Transfer canceled");}
            if(active!=null){socket.close();throw new IOException("Another operation is active");}
            active=socket;
        }
        try {socket.connect(new InetSocketAddress(address,port),10000);socket.setSoTimeout(20000);return socket;}
        catch(IOException e){finish(socket);throw e;}
    }
    private ScheduledFuture<?> deadline(Socket socket,long millis)throws IOException {
        try {return watchdog.schedule(()->{try{socket.close();}catch(IOException ignored){}},millis,TimeUnit.MILLISECONDS);}
        catch(RejectedExecutionException e){finish(socket);throw new InterruptedIOException("Transfer canceled");}
    }
    private void headers(OutputStream out,String method,String path,long length,String extra)throws IOException {
        String host=address.getHostAddress();if(host.indexOf(':')>=0)host="["+host+"]";
        out.write((method+" "+path+" HTTP/1.1\r\nHost: "+host+":"+port+"\r\nContent-Length: "+length+"\r\nConnection: close\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));
    }
    private String response(Socket socket)throws IOException {
        InputStream in=new BufferedInputStream(socket.getInputStream());int[] budget={8192};
        String statusLine=line(in,budget);String[] parts=statusLine.split(" ",3);
        if(parts.length<2 || !"HTTP/1.1".equals(parts[0]) || !parts[1].matches("[0-9]{3}"))throw new IOException("Invalid receiver response");
        int status=Integer.parseInt(parts[1]);Map<String,String> headers=new HashMap<>();
        for(int count=0;;count++){
            String value=line(in,budget);if(value.isEmpty())break;int colon=value.indexOf(':');
            if(count>=32 || colon<=0 || headers.put(value.substring(0,colon).toLowerCase(Locale.ROOT),value.substring(colon+1).trim())!=null)throw new IOException("Invalid response headers");
        }
        String lengthText=headers.get("content-length");
        if(headers.containsKey("transfer-encoding") || lengthText==null || !lengthText.matches("[0-9]{1,4}"))throw new IOException("Invalid response framing");
        int length=Integer.parseInt(lengthText);if(length>1024)throw new IOException("Receiver response too large");
        byte[] bytes=new byte[length];int offset=0;
        while(offset<length){int n=in.read(bytes,offset,length-offset);if(n<0)throw new EOFException("Incomplete receiver response");offset+=n;}
        String text=new String(bytes,StandardCharsets.UTF_8);
        if(status!=200)throw new IOException("TV rejected transfer ("+status+"): "+text);
        return text;
    }
    private static String line(InputStream in,int[] budget)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        while(--budget[0]>=0 && out.size()<=4096){
            int b=in.read();if(b<0)throw new EOFException("Incomplete receiver response");
            if(b=='\r'){if(in.read()!='\n')throw new IOException("Invalid receiver response");budget[0]--;return out.toString("US-ASCII");}
            if(b<32 || b>126)throw new IOException("Invalid receiver response");out.write(b);
        }
        throw new IOException("Receiver response headers too large");
    }
    private void finish(Socket socket){try{socket.close();}catch(IOException ignored){}synchronized(this){if(active==socket)active=null;}}
    @Override public void close(){Socket socket;synchronized(this){canceled=true;socket=active;}if(socket!=null)try{socket.close();}catch(IOException ignored){}watchdog.shutdownNow();}
}
