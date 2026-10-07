package com.mooncast.host;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class OriginalMediaServerTest {
    record Response(String headers,byte[] body){}
    public static void main(String[] args) throws Exception {
        byte[] original=new byte[120001];new Random(71).nextBytes(original);
        var source=new OriginalMediaServer.Source(){public String name(){return "<script>alert('x')</script>.mp4";}public String mime(){return "video/mp4";}public long length(){return original.length;}public InputStream open(){return new ByteArrayInputStream(original);}};
        int port;String path;
        try(var server=new OriginalMediaServer(source,InetAddress.getLoopbackAddress(),false)){
            port=server.port();path=server.path();
            Response full=request(port,"GET",path+"media","");require(full.headers.startsWith("HTTP/1.1 200"),"full status");require(Arrays.equals(original,full.body),"original bytes");
            Response part=request(port,"GET",path+"media","Range: bytes=65-8098\r\n");require(part.headers.contains("206 Partial") && part.headers.contains("bytes 65-8098/120001"),"range header");require(Arrays.equals(Arrays.copyOfRange(original,65,8099),part.body),"seek bytes");
            Response suffix=request(port,"GET",path+"media","Range: bytes=-17\r\n");require(Arrays.equals(Arrays.copyOfRange(original,original.length-17,original.length),suffix.body),"suffix");
            Response head=request(port,"HEAD",path+"media","");require(head.body.length==0 && head.headers.contains("Content-Length: 120001"),"head");
            for(String range:new String[]{"bytes=120001-","bytes=8-7","bytes=-0","bytes=1-2,3-4","bytes=922337203685477580799-","items=1-2"})require(request(port,"GET",path+"media","Range: "+range+"\r\n").headers.contains("416 Range"),"invalid range "+range);
            require(request(port,"GET","/wrong/media","").headers.contains("404 Not Found"),"token required");
            require(request(port,"POST",path+"media","").headers.contains("405 Method"),"method");
            require(request(port,"GET",path+"../media","").headers.contains("404 Not Found"),"no path traversal");
            String page=new String(request(port,"GET",path,"").body,StandardCharsets.UTF_8);require(page.contains("&lt;script&gt;") && !page.contains("<script>"),"escape document name");
        }
        try(Socket ignored=new Socket(InetAddress.getLoopbackAddress(),port)){throw new AssertionError("closed server reachable");}catch(ConnectException expected){}
        try(var unknown=new OriginalMediaServer(new OriginalMediaServer.Source(){public String name(){return "unknown";}public String mime(){return "audio/wav";}public long length(){return -1;}public InputStream open(){return new ByteArrayInputStream(original);}},InetAddress.getLoopbackAddress(),true)){
            require(Arrays.equals(request(unknown.port(),"GET",unknown.path()+"media","").body,original),"unknown-length provider");
            require(request(unknown.port(),"GET",unknown.path()+"media","Range: bytes=0-1\r\n").headers.contains("416"),"unknown range refused");
        }
        System.out.println("PASS: original bytes, seek/suffix/HEAD, token, malformed ranges, document escaping, unknown length and shutdown");
    }
    private static Response request(int port,String method,String path,String extra) throws Exception {
        try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),port)){
            socket.setSoTimeout(3000);socket.getOutputStream().write((method+" "+path+" HTTP/1.1\r\nHost: localhost\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));
            byte[] raw=socket.getInputStream().readAllBytes();int split=-1;for(int i=0;i+3<raw.length;i++)if(raw[i]==13 && raw[i+1]==10 && raw[i+2]==13 && raw[i+3]==10){split=i+4;break;}
            if(split<0)throw new AssertionError("no headers");return new Response(new String(raw,0,split,StandardCharsets.ISO_8859_1),Arrays.copyOfRange(raw,split,raw.length));
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
