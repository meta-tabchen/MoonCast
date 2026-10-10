package com.mooncast.tv.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** The local server simulates native HLS and ordinary origin behavior with generated data only. */
public final class TvMediaHttpTest {
    private static final byte[] DATA=new byte[16037];
    static {for(int i=0;i<DATA.length;i++)DATA[i]=(byte)(i*17);}
    public static void main(String[] args) throws Exception {
        for(String host:new String[]{"127.0.0.1","169.254.1.2","169.254.169.254","100.100.100.200","::1","::","fe80::1","ff02::1","fd00:ec2::254","0.0.0.0","224.0.0.1"})
            denied(()->MediaHttpConnection.validateResolvedAddress(InetAddress.getByName(host)),"resolved forbidden target "+host);
        for(String host:new String[]{"192.168.1.20","10.3.4.5","172.16.1.20","8.8.8.8","2001:db8::1"}) MediaHttpConnection.validateResolvedAddress(InetAddress.getByName(host));
        denied(()->MediaHttpConnection.validateDestination(URI.create("http://127.0.0.1:9911/a"),-1),"loopback denied without native capability");
        denied(()->MediaHttpConnection.validateDestination(URI.create("file:///etc/passwd"),9911),"nested file source denied");
        denied(()->MediaHttpConnection.validateDestination(URI.create("content://media/video/1"),9911),"nested content source denied");
        denied(()->MediaHttpConnection.validateDestination(URI.create("http://localhost:9912/a"),9911),"loopback different port denied");
        denied(()->MediaHttpConnection.validateDestination(URI.create("http://127.0.0.2:9911/a"),9911),"loopback different literal denied");
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
        server.createContext("/data",exchange->{
            String range=exchange.getRequestHeaders().getFirst("Range");
            if(range==null){send(exchange,200,DATA);return;}
            String[] parts=range.substring(6).split("-",-1);int start=Integer.parseInt(parts[0]);int end=parts[1].isEmpty()?DATA.length-1:Math.min(DATA.length-1,Integer.parseInt(parts[1]));
            if(start>=DATA.length){exchange.getResponseHeaders().add("Content-Range","bytes */"+DATA.length);send(exchange,416,new byte[0]);return;}
            exchange.getResponseHeaders().add("Content-Range","bytes "+start+"-"+end+"/"+DATA.length);send(exchange,206,Arrays.copyOfRange(DATA,start,end+1));
        });
        server.createContext("/ignore-range",exchange->send(exchange,200,DATA));
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(compressed)){gzip.write(DATA);}
        server.createContext("/gzip",exchange->{exchange.getResponseHeaders().add("Content-Encoding","gzip");send(exchange,200,compressed.toByteArray());});
        server.createContext("/chunked",exchange->{exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(DATA);exchange.close();});
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/data");send(exchange,302,new byte[0]);});
        server.createContext("/redirect-loop",exchange->{exchange.getResponseHeaders().add("Location","/redirect-loop");send(exchange,307,new byte[0]);});
        server.createContext("/redirect-private",exchange->{exchange.getResponseHeaders().add("Location","http://127.0.0.1:1/private");send(exchange,302,new byte[0]);});
        server.createContext("/redirect-file",exchange->{exchange.getResponseHeaders().add("Location","file:///etc/passwd");send(exchange,302,new byte[0]);});
        server.createContext("/redirect-metadata",exchange->{exchange.getResponseHeaders().add("Location","http://169.254.169.254/latest/meta-data/");send(exchange,302,new byte[0]);});
        server.createContext("/bad-range",exchange->{exchange.getResponseHeaders().add("Content-Range","bytes 20-22/50");send(exchange,206,new byte[]{1,2,3});});
        server.createContext("/head",exchange->{exchange.getResponseHeaders().add("Content-Length",Integer.toString(DATA.length));exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.createContext("/not-found",exchange->send(exchange,404,new byte[0]));
        server.start();
        int port=server.getAddress().getPort();AtomicInteger allowed=new AtomicInteger(port);String base="http://127.0.0.1:"+port;
        try(MediaHttpConnection media=new MediaHttpConnection(allowed::get)) {
            require(media.open(base+"/data",0,-1,Collections.emptyMap(),false)==DATA.length,"known length");require(Arrays.equals(DATA,readAll(media)),"original bytes");
            require(media.open(base+"/data",999,121,Collections.emptyMap(),false)==121,"206 returned range length");require(Arrays.equals(Arrays.copyOfRange(DATA,999,1120),readAll(media)),"206 exact range bytes");
            media.open(base+"/ignore-range",999,121,Collections.emptyMap(),false);require(Arrays.equals(Arrays.copyOfRange(DATA,999,1120),readAll(media)),"200 fallback skip");
            require(media.open(base+"/gzip",0,-1,Collections.emptyMap(),false)==-1,"decoded gzip has unknown length");require(Arrays.equals(DATA,readAll(media)),"gzip full response bytes");
            denied(()->media.open(base+"/gzip",20,100,Collections.emptyMap(),false),"compressed byte ranges refused");
            require(media.open(base+"/chunked",0,-1,Collections.emptyMap(),false)==-1,"chunked unknown length");require(Arrays.equals(DATA,readAll(media)),"chunked original bytes");
            media.open(base+"/redirect",20,100,Collections.emptyMap(),false);require(media.uri().getPath().equals("/data"),"redirect final URI");require(Arrays.equals(Arrays.copyOfRange(DATA,20,120),readAll(media)),"redirect preserves requested range");
            require(media.open(base+"/data",DATA.length,-1,Collections.emptyMap(),false)==0,"416 exact EOF is valid");require(readAll(media).length==0,"EOF reads empty");
            require(media.open(base+"/head",0,-1,Collections.emptyMap(),true)==0,"HEAD has no body");
            denied(()->media.open(base+"/data",DATA.length+1,-1,Collections.emptyMap(),false),"out of range");
            for(String path:new String[]{"/redirect-loop","/redirect-private","/redirect-file","/redirect-metadata","/bad-range","/not-found"}) denied(()->media.open(base+path,0,-1,Collections.emptyMap(),false),"redirect/status gate "+path);
            denied(()->media.open(base+"/data",0,-1,Map.of("Host","other"),false),"host override refused");
            denied(()->media.open(base+"/data",0,-1,Map.of("Authorization","secret"),false),"credentials not forwarded");
            denied(()->media.open(base+"/data",0,-1,Map.of("X-Test","a\r\nb"),false),"header injection refused");
            media.open(base+"/data",0,-1,Collections.emptyMap(),false);allowed.set(-1);
            denied(()->media.read(new byte[20],0,20),"native capability revocation checked while reading");
            denied(()->media.open(base+"/data",0,-1,Collections.emptyMap(),false),"ended native source cannot reopen proxy");
            media.close();require(media.uri()==null && media.headers().isEmpty(),"close clears source metadata");
        }finally{server.stop(0);}
        System.out.println("PASS: TV HTTP media ranges, 200 fallback, chunked/gzip streams, redirect limits/destination gates, EOF, reserved headers and native-port revocation");
    }
    private static byte[] readAll(MediaHttpConnection connection) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[3000];for(int n;(n=connection.read(bytes,0,bytes.length))!=-1;)out.write(bytes,0,n);return out.toByteArray();}
    private static void send(HttpExchange exchange,int code,byte[] bytes) throws IOException {exchange.sendResponseHeaders(code,bytes.length==0?-1:bytes.length);if(bytes.length>0)exchange.getResponseBody().write(bytes);exchange.close();}
    @FunctionalInterface private interface Checked {void run() throws Exception;}
    private static void denied(Checked action,String label) throws Exception {try{action.run();throw new AssertionError("Unexpectedly allowed: "+label);}catch(IOException expected){}}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
