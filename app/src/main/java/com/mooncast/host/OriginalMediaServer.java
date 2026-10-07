package com.mooncast.host;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Serves exactly one selected source. Token paths expire when this instance closes. */
final class OriginalMediaServer implements AutoCloseable {
    interface Source {
        String name(); String mime(); long length();
        InputStream open() throws IOException;
    }
    private final Source source;
    private final ServerSocket server;
    private final String token;
    private final boolean chinese;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"OriginalMediaClient");t.setDaemon(true);return t;});
    private volatile boolean closed;
    OriginalMediaServer(Source source,InetAddress bind,boolean chinese) throws IOException {
        this.source=Objects.requireNonNull(source);this.chinese=chinese;
        byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);
        token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        server=new ServerSocket();server.bind(new InetSocketAddress(bind,0),8);
        Thread accept=new Thread(this::accept,"OriginalMediaAccept");accept.setDaemon(true);accept.start();
    }
    int port(){return server.getLocalPort();}
    String path(){return "/"+token+"/";}
    private void accept(){while(!closed)try{
        Socket socket=server.accept();socket.setSoTimeout(10000);sockets.add(socket);
        try{workers.execute(()->serve(socket));}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}
    }catch(IOException e){if(!closed)close();}}
    private void serve(Socket socket){try(socket){
        InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
        String request=line(in);if(request==null)return;
        String[] first=request.split(" ");if(first.length!=3 || !first[2].matches("HTTP/1\\.[01]")){reply(out,400,"Bad Request",0,null);return;}
        boolean head=first[0].equals("HEAD");if(!head && !first[0].equals("GET")){reply(out,405,"Method Not Allowed",0,"Allow: GET, HEAD\r\n");return;}
        Map<String,String> headers=new HashMap<>();int total=request.length();
        for(;;){String row=line(in);if(row==null)throw new IOException("incomplete headers");total+=row.length()+2;if(total>8192)throw new IOException("headers too long");if(row.isEmpty())break;int colon=row.indexOf(':');if(colon<1){reply(out,400,"Bad Request",0,null);return;}String key=row.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.put(key,row.substring(colon+1).trim())!=null){reply(out,400,"Bad Request",0,null);return;}}
        if(first[1].equals(path())){
            byte[] page=page().getBytes(StandardCharsets.UTF_8);
            reply(out,200,"OK",page.length,"Content-Type: text/html; charset=utf-8\r\nContent-Security-Policy: default-src 'none'; media-src 'self'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'\r\n");if(!head)out.write(page);return;
        }
        if(!first[1].equals(path()+"media")){reply(out,404,"Not Found",0,null);return;}
        long size=source.length(),start=0,end=size-1;boolean partial=false;
        String range=headers.get("range");
        if(range!=null){
            if(size<0){reply(out,416,"Range Not Satisfiable",0,null);return;}
            try{long[] bounds=range(range,size);start=bounds[0];end=bounds[1];partial=true;}
            catch(IllegalArgumentException e){reply(out,416,"Range Not Satisfiable",0,"Content-Range: bytes */"+size+"\r\n");return;}
        }
        long count=size<0?-1:Math.max(0,end-start+1);
        String mime=source.mime();if(mime==null || !mime.matches("(?:video|audio|image)/[A-Za-z0-9.+-]+"))mime="application/octet-stream";
        String extra="Content-Type: "+mime+"\r\n"+(size>=0?"Accept-Ranges: bytes\r\n":"")+(partial?"Content-Range: bytes "+start+"-"+end+"/"+size+"\r\n":"");
        if(head){reply(out,partial?206:200,partial?"Partial Content":"OK",count,extra);return;}
        // Open before committing status, so a revoked provider returns an HTTP error.
        InputStream opened;
        try{opened=source.open();}catch(IOException | SecurityException e){reply(out,503,"Service Unavailable",0,null);return;}
        try(InputStream media=opened){
            try{skip(media,start);}catch(IOException e){reply(out,503,"Service Unavailable",0,null);return;}
            reply(out,partial?206:200,partial?"Partial Content":"OK",count,extra);
            byte[] buffer=new byte[65536];long remaining=count;
            while(!closed && remaining!=0){int n=media.read(buffer,0,remaining<0?buffer.length:(int)Math.min(buffer.length,remaining));if(n<0)break;out.write(buffer,0,n);if(remaining>0)remaining-=n;}
        }
    }catch(IOException ignored){}finally{sockets.remove(socket);}}
    static long[] range(String value,long size){
        if(size<=0 || !value.matches("bytes=(?:[0-9]+-[0-9]*|-[0-9]+)"))throw new IllegalArgumentException("range");
        try{
            String[] pieces=value.substring(6).split("-",-1);long start,end;
            if(pieces[0].isEmpty()){long suffix=Long.parseLong(pieces[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,size-suffix);end=size-1;}
            else{start=Long.parseLong(pieces[0]);end=pieces[1].isEmpty()?size-1:Math.min(size-1,Long.parseLong(pieces[1]));}
            if(start>=size || end<start)throw new IllegalArgumentException();return new long[]{start,end};
        }catch(NumberFormatException e){throw new IllegalArgumentException("range",e);}
    }
    private static void skip(InputStream in,long bytes) throws IOException {while(bytes>0){long n=in.skip(bytes);if(n==0){if(in.read()<0)throw new EOFException();n=1;}bytes-=n;}}
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int previous=-1;
        while(bytes.size()<=8192){int b=in.read();if(b<0)return bytes.size()==0?null:throwEof();if(previous==13 && b==10){byte[] raw=bytes.toByteArray();return new String(raw,0,raw.length-1,StandardCharsets.ISO_8859_1);}bytes.write(b);previous=b;}
        throw new IOException("line too long");
    }
    private static String throwEof() throws IOException {throw new EOFException();}
    private static void reply(OutputStream out,int code,String reason,long size,String extra) throws IOException {
        out.write(("HTTP/1.1 "+code+" "+reason+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"+(size>=0?"Content-Length: "+size+"\r\n":"")+(extra==null?"":extra)+"\r\n").getBytes(StandardCharsets.ISO_8859_1));
    }
    private String page(){
        String mime=source.mime()==null?"":source.mime();String media;
        if(mime.startsWith("image/"))media="<img src='media' alt='"+escape(source.name())+"'>";
        else if(mime.startsWith("audio/"))media="<audio src='media' controls></audio>";
        else media="<video src='media' controls playsinline preload='metadata'></video>";
        return "<!doctype html><html lang='"+(chinese?"zh":"en")+"'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>MoonCast · "+escape(source.name())+"</title><style>body{margin:0;background:#111827;color:#e5e7eb;font:16px system-ui}header{padding:24px;max-width:1000px;margin:auto}h1{font-size:24px}p{color:#9ca3af;line-height:1.6}video,img{display:block;width:100%;max-height:80vh;object-fit:contain;background:#000}audio{width:100%}</style><header><h1>MoonCast · "+escape(source.name())+"</h1><p>"+(chinese?"原文件直接传输 · 无转码。请在播放器中开启全屏；格式支持取决于浏览器。停止分享后链接失效。":"Original bytes · No transcoding. Use the player’s fullscreen control. Format support depends on your browser. The link expires when sharing stops.")+"</p></header>"+media+"</html>";
    }
    private static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    @Override public void close(){if(closed)return;closed=true;try{server.close();}catch(IOException ignored){}for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}workers.shutdownNow();}
}
