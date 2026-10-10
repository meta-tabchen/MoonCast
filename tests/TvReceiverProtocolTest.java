package com.mooncast.tv.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Generated-input protocol tests, not evidence of physical TV/controller interoperability. */
public final class TvReceiverProtocolTest {
    private static final Inet4Address LOOP;
    private static final String ID="4c1c1fce-cbfd-409f-8093-10433647cb70";
    static { try { LOOP=(Inet4Address)InetAddress.getByName("127.0.0.1"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    private record Response(String headers,String body) {}
    private static final class Player implements PlaybackTarget {
        volatile Snapshot value=Snapshot.empty();
        private void update(String state,String uri,String metadata,long pos,int volume,boolean mute) { value=new Snapshot(state,uri,metadata,120000,pos,volume,mute,false); }
        @Override public void setMedia(String uri,String metadata) { update(uri.isEmpty()?"NO_MEDIA_PRESENT":"STOPPED",uri,metadata,0,value.volume,value.muted); }
        @Override public void play() { update("PLAYING",value.uri,value.metadata,value.positionMillis,value.volume,value.muted); }
        @Override public void pause() { update("PAUSED_PLAYBACK",value.uri,value.metadata,value.positionMillis,value.volume,value.muted); }
        @Override public void stop() { update(value.uri.isEmpty()?"NO_MEDIA_PRESENT":"STOPPED",value.uri,value.metadata,0,value.volume,value.muted); }
        @Override public void seekTo(long pos) { update(value.state,value.uri,value.metadata,pos,value.volume,value.muted); }
        @Override public void setVolume(int volume) { update(value.state,value.uri,value.metadata,value.positionMillis,volume,value.muted); }
        @Override public void setMuted(boolean mute) { update(value.state,value.uri,value.metadata,value.positionMillis,value.volume,mute); }
        @Override public Snapshot snapshot() { return value; }
    }
    public static void main(String[] args) throws Exception {
        pureValidation();
        Player player=new Player();
        DlnaReceiver server=new DlnaReceiver(player,"TV <&> test",ID,null);
        try(server) {
            server.startForTest(LOOP,8);
            int port=server.getPort();
            require(server.isRunning(),"running");
            Response description=request(port,"GET","/description.xml","","");
            require(description.headers.contains("200 OK") && description.body.contains("TV &lt;&amp;&gt; test"),"escaped device description");
            document(description.body);
            for(String service:new String[]{UpnpService.AV,UpnpService.RC,UpnpService.CM}) validateScpd(request(port,"GET","/scpd/"+service+".xml","","").body);
            require(request(port,"HEAD","/description.xml","","").body.isEmpty(),"HEAD no body");
            require(request(port,"GET","/../description.xml","","").headers.contains("404"),"path traversal refused");
            fault(soap(port,UpnpService.AV,"Play","<InstanceID>0</InstanceID><Speed>1</Speed>"),701);
            String metadata="&lt;DIDL-Lite&gt;A &amp;amp; B&lt;/DIDL-Lite&gt;";
            ok(soap(port,UpnpService.AV,"SetAVTransportURI","<InstanceID>0</InstanceID><CurrentURI>http://192.168.1.20/media.mp4?x=1&amp;y=2</CurrentURI><CurrentURIMetaData>"+metadata+"</CurrentURIMetaData>"));
            require(player.value.uri.endsWith("?x=1&y=2") && player.value.metadata.equals("<DIDL-Lite>A &amp; B</DIDL-Lite>"),"URI and nested metadata decoded exactly once");
            ok(soap(port,UpnpService.AV,"Play","<InstanceID>0</InstanceID><Speed>1</Speed>"));
            require(soap(port,UpnpService.AV,"GetTransportInfo","<InstanceID>0</InstanceID>").body.contains("<CurrentTransportState>PLAYING</CurrentTransportState>"),"transport state");
            ok(soap(port,UpnpService.AV,"Pause","<InstanceID>0</InstanceID>"));
            require(player.value.state.equals("PAUSED_PLAYBACK"),"pause callback");
            ok(soap(port,UpnpService.AV,"Stop","<InstanceID>0</InstanceID>"));
            require(player.value.state.equals("STOPPED") && !player.value.uri.isEmpty(),"stop retains the current media");
            ok(soap(port,UpnpService.AV,"Play","<InstanceID>0</InstanceID><Speed>1</Speed>"));
            require(player.value.state.equals("PLAYING"),"Stop then Play resumes the same item");
            ok(soap(port,UpnpService.AV,"Pause","<InstanceID>0</InstanceID>"));
            ok(soap(port,UpnpService.AV,"Seek","<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>00:01:03.250</Target>"));
            require(player.value.positionMillis==63250,"seek callback");
            require(soap(port,UpnpService.AV,"GetPositionInfo","<InstanceID>0</InstanceID>").body.contains("<RelTime>00:01:03</RelTime>"),"position response");
            for(String action:new String[]{"GetMediaInfo","GetDeviceCapabilities","GetTransportSettings","GetCurrentTransportActions"}) ok(soap(port,UpnpService.AV,action,"<InstanceID>0</InstanceID>"));
            ok(soap(port,UpnpService.RC,"SetVolume","<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>37</DesiredVolume>"));
            ok(soap(port,UpnpService.RC,"SetMute","<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredMute>1</DesiredMute>"));
            require(player.value.volume==37 && player.value.muted,"volume and mute callbacks");
            require(soap(port,UpnpService.RC,"GetVolume","<InstanceID>0</InstanceID><Channel>Master</Channel>").body.contains("<CurrentVolume>37</CurrentVolume>"),"volume read");
            ok(soap(port,UpnpService.RC,"ListPresets","<InstanceID>0</InstanceID>"));
            ok(soap(port,UpnpService.RC,"SelectPreset","<InstanceID>0</InstanceID><PresetName>FactoryDefaults</PresetName>"));
            require(player.value.volume==100 && !player.value.muted,"preset");
            ok(soap(port,UpnpService.CM,"GetProtocolInfo",""));
            ok(soap(port,UpnpService.CM,"GetCurrentConnectionIDs",""));
            ok(soap(port,UpnpService.CM,"GetCurrentConnectionInfo","<ConnectionID>0</ConnectionID>"));
            fault(soap(port,UpnpService.CM,"GetCurrentConnectionInfo","<ConnectionID>42</ConnectionID>"),706);
            fault(soap(port,UpnpService.AV,"Play","<InstanceID>0</InstanceID><Speed>2</Speed>"),717);
            fault(soap(port,UpnpService.AV,"GetTransportInfo","<InstanceID>99</InstanceID>"),718);
            fault(soap(port,UpnpService.AV,"Seek","<InstanceID>0</InstanceID><Unit>TRACK_NR</Unit><Target>1</Target>"),710);
            fault(soap(port,UpnpService.AV,"Seek","<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>00:59:00</Target>"),711);
            fault(soap(port,UpnpService.AV,"SetPlayMode","<InstanceID>0</InstanceID><NewPlayMode>REPEAT_ALL</NewPlayMode>"),712);
            fault(soap(port,UpnpService.AV,"Next","<InstanceID>0</InstanceID>"),711);
            fault(soap(port,UpnpService.AV,"Record","<InstanceID>0</InstanceID>"),401);
            fault(soap(port,UpnpService.RC,"SetVolume","<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>101</DesiredVolume>"),402);
            fault(soap(port,UpnpService.RC,"GetMute","<InstanceID>0</InstanceID><Channel>Left</Channel>"),402);
            fault(soap(port,UpnpService.AV,"SetAVTransportURI","<InstanceID>0</InstanceID><CurrentURI>file:///etc/passwd</CurrentURI><CurrentURIMetaData/>"),714);
            ok(soap(port,UpnpService.AV,"SetAVTransportURI","<InstanceID>0</InstanceID><!-- valid SOAP comment --><CurrentURI>http://192.168.1.20/media.mp4</CurrentURI><CurrentURIMetaData><![CDATA[<DIDL-Lite>A & B</DIDL-Lite>]]></CurrentURIMetaData>"));
            require(player.value.metadata.equals("<DIDL-Lite>A & B</DIDL-Lite>"),"CDATA metadata supported without enabling DTDs");
            malformed(port);
            eventing(server,player);
            int oldPort=server.getPort();
            try(Socket slow=new Socket(LOOP,oldPort)) {
                slow.setSoTimeout(3000); slow.getOutputStream().write("POST /control/AVTransport HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
                server.stop();
                try { require(slow.getInputStream().read()==-1,"stop closes incomplete requests"); } catch(java.net.SocketException expected) {}
            }
            require(!server.isRunning() && server.getDescriptionUrl().isEmpty(),"stopped state");
            assertClosed(oldPort);
            server.startForTest(LOOP,8);
            require(request(server.getPort(),"GET","/description.xml","","").body.contains(ID),"restart same identity");
            ok(soap(server.getPort(),UpnpService.AV,"SetAVTransportURI","<InstanceID>0</InstanceID><CurrentURI/><CurrentURIMetaData/>"));
            require(player.value.state.equals("NO_MEDIA_PRESENT"),"empty media clears state");
        }
        liveSsdp();
        rejectedIngress();
        System.out.println("PASS: TV DLNA live HTTP/SOAP controls, descriptions, XML/framing limits, subnet/URL gates, GENA events, SSDP replies, stop and restart");
    }
    private static void pureValidation() throws Exception {
        LanAccess lan=new LanAccess((Inet4Address)InetAddress.getByName("192.168.10.4"),24);
        require(lan.allows(InetAddress.getByName("192.168.10.30")),"same LAN");
        for(String peer:new String[]{"192.168.11.30","8.8.8.8","127.0.0.1","192.168.10.255","192.168.10.0","239.255.255.250","::1"}) require(!lan.allows(InetAddress.getByName(peer)),"denied peer "+peer);
        for(String uri:new String[]{"http://192.168.1.2:8000/video.mp4","https://cdn.example.org/video.m3u8","http://[2001:db8::10]/video"}) require(LanAccess.validateMediaUri(uri).equals(uri),"allowed media");
        for(String uri:new String[]{"file:///sdcard/private","content://media/video/1","ftp://example.org/file","http://u:p@example.org/","http://localhost/x","http://LOCALHOST./x","http://127.1/x","http://2130706433/x","http://0x7f000001/x","http://0x7f.0.0.1/x","http://[fd00:ec2::254]/x","http://0177.0.0.1/x","http://127.0.0.1/x","http://169.254.169.254/latest/meta-data","http://100.100.100.200/latest/meta-data","http://0.0.0.0/x","http://[::1]/x","http://[::ffff:127.0.0.1]/x","http://[fe80::1]/x","http://metadata.google.internal/x","http://example.org:0/x","http://example.org:70000/x"}) {
            try { LanAccess.validateMediaUri(uri); throw new AssertionError("Allowed unsafe URI "+uri); } catch(IllegalArgumentException expected) {}
        }
        require(UpnpService.parseTime("100:00:00.001")==360000001L,"long fractional time");
        for(String time:new String[]{"-1:00:00","00:61:00","NaN","999999999999999:00:00"}) try { UpnpService.parseTime(time); throw new AssertionError("bad time"); } catch(UpnpService.Fault expected) {}
        String search="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n";
        require(DlnaReceiver.parseSearch(search)!=null,"valid search");
        require(DlnaReceiver.parseSearch(search.replace("MX: 1","MX: 0"))==null,"bad MX");
        require(DlnaReceiver.parseSearch(search.replace("ST: ssdp:all","ST: ssdp:all\r\nST: ssdp:all"))==null,"duplicate ST");
    }
    private static void malformed(int port) throws Exception {
        String type="urn:schemas-upnp-org:service:AVTransport:1";
        String headers="Content-Type: text/xml\r\nSOAPACTION: \""+type+"#GetTransportInfo\"\r\n";
        String good=body(type,"GetTransportInfo","<InstanceID>0</InstanceID>");
        for(String xml:new String[]{"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]>"+good,good.replace("<InstanceID>0</InstanceID>","<InstanceID>0</InstanceID><InstanceID>1</InstanceID>"),good.replace("<InstanceID>0</InstanceID>","<InstanceID><nested/></InstanceID>"),good.replace("<InstanceID>0</InstanceID>","<InstanceID>0</InstanceID><Extra/>"),good.replace(type,"urn:bad"),"<x/>",good.replace("</s:Body>","<u:GetTransportInfo xmlns:u='"+type+"'><InstanceID>0</InstanceID></u:GetTransportInfo></s:Body>")}) fault(request(port,"POST","/control/AVTransport",headers,xml),402);
        fault(request(port,"POST","/control/AVTransport",headers.replace("#GetTransportInfo","#Stop"),good),401);
        require(raw(port,"GET /description.xml HTTP/1.1\r\nHost: evil.example:"+port+"\r\n\r\n").headers.contains("403"),"host binding");
        require(request(port,"POST","/control/AVTransport",headers+"Origin: http://evil.example\r\n",good).headers.contains("400"),"browser origin refused");
        require(raw(port,"POST /control/AVTransport HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nContent-Length: 0\r\nContent-Length: 1\r\n\r\n").headers.contains("400"),"duplicate length refused");
        require(raw(port,"POST /control/AVTransport HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nTransfer-Encoding: chunked\r\n\r\n").headers.contains("400"),"chunked framing refused");
        require(raw(port,"POST /control/AVTransport HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nContent-Length: 65537\r\n\r\n").headers.contains("400"),"body limit before allocation/read");
    }
    private static void eventing(DlnaReceiver server,Player player) throws Exception {
        int port=server.getPort();
        try(ServerSocket callback=new ServerSocket(0,4,LOOP)) {
            callback.setSoTimeout(4000);
            CompletableFuture<String> first=notification(callback);
            Response subscribe=request(port,"SUBSCRIBE","/event/AVTransport","NT: upnp:event\r\nCALLBACK: <http://127.0.0.1:"+callback.getLocalPort()+"/events>\r\nTIMEOUT: Second-90\r\n","");
            ok(subscribe);
            String sid=header(subscribe.headers,"sid");
            String initial=first.get(5,TimeUnit.SECONDS);
            require(initial.contains("SEQ: 0") && initial.contains("LastChange") && initial.contains("&lt;Event"),"initial escaped GENA event");
            CompletableFuture<String> second=notification(callback);
            player.play();
            String changed=second.get(5,TimeUnit.SECONDS);
            require(changed.contains("SEQ: 1") && changed.contains("PLAYING"),"changed state GENA event");
            ok(request(port,"SUBSCRIBE","/event/AVTransport","SID: "+sid+"\r\nTIMEOUT: Second-120\r\n",""));
            require(request(port,"SUBSCRIBE","/event/RenderingControl","SID: "+sid+"\r\n","").headers.contains("412"),"SID pinned to service");
            require(request(port,"SUBSCRIBE","/event/AVTransport","NT: upnp:event\r\nCALLBACK: <http://192.168.1.1:80/>\r\n","").headers.contains("412"),"event reflection refused");
            require(request(port,"SUBSCRIBE","/event/AVTransport","NT: upnp:event\r\nCALLBACK: <http://localhost:80/>\r\n","").headers.contains("412"),"DNS callback refused");
            ok(request(port,"UNSUBSCRIBE","/event/AVTransport","SID: "+sid+"\r\n",""));
            require(request(port,"SUBSCRIBE","/event/AVTransport","SID: "+sid+"\r\n","").headers.contains("412"),"revoked SID");
        }
    }
    private static CompletableFuture<String> notification(ServerSocket callback) {
        return CompletableFuture.supplyAsync(()->{
            try(Socket socket=callback.accept()) {
                socket.setSoTimeout(4000); ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                int length=-1, headerEnd=-1;
                for(;;) {
                    int b=socket.getInputStream().read(); if(b<0) throw new IOException("early event EOF"); bytes.write(b);
                    byte[] raw=bytes.toByteArray();
                    if(headerEnd<0 && raw.length>=4 && raw[raw.length-4]==13 && raw[raw.length-3]==10 && raw[raw.length-2]==13 && raw[raw.length-1]==10) {
                        headerEnd=raw.length; length=Integer.parseInt(header(new String(raw,StandardCharsets.UTF_8),"content-length"));
                    }
                    if(headerEnd>=0 && bytes.size()==headerEnd+length) break;
                    if(bytes.size()>256000) throw new IOException("oversized event");
                }
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                return bytes.toString(StandardCharsets.UTF_8);
            } catch(Exception e) { throw new RuntimeException(e); }
        });
    }
    private static void rejectedIngress() throws Exception {
        try(DlnaReceiver server=new DlnaReceiver(new Player(),"Subnet test",ID,null)) {
            server.startForTest(LOOP,32);
            try(Socket socket=new Socket()) {
                socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.2"),0));
                socket.connect(new InetSocketAddress(LOOP,server.getPort())); socket.setSoTimeout(3000);
                try { require(socket.getInputStream().read()==-1,"off-prefix live client closed"); }
                catch(java.net.SocketException expected) {}
            }
            ok(request(server.getPort(),"GET","/description.xml","",""));
        }
    }
    private static void liveSsdp() throws Exception {
        try(DlnaReceiver server=new DlnaReceiver(new Player(),"SSDP test",ID,null)) {
            server.start(LOOP,8);
            try(DatagramSocket socket=new DatagramSocket(new InetSocketAddress(LOOP,0))) {
                socket.setSoTimeout(3000);
                byte[] search=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                socket.send(new DatagramPacket(search,search.length,LOOP,1900));
                Set<String> types=new HashSet<>();
                for(int i=0;i<6;i++) {
                    byte[] bytes=new byte[4096]; DatagramPacket reply=new DatagramPacket(bytes,bytes.length); socket.receive(reply);
                    String text=new String(bytes,0,reply.getLength(),StandardCharsets.US_ASCII);
                    require(text.startsWith("HTTP/1.1 200 OK") && text.contains(server.getDescriptionUrl()),"live SSDP response");
                    types.add(header(text,"st"));
                }
                require(types.size()==6 && types.contains("urn:schemas-upnp-org:device:MediaRenderer:1"),"SSDP root, uuid, renderer and 3 services");
            }
            int port=server.getPort(); server.stop(); assertClosed(port);
            server.start(LOOP,8); require(server.isRunning(),"full discovery restart");
        }
    }
    private static Response soap(int port,String service,String action,String args) throws Exception {
        String type="urn:schemas-upnp-org:service:"+service+":1";
        return request(port,"POST","/control/"+service,"Content-Type: text/xml; charset=utf-8\r\nSOAPACTION: \""+type+"#"+action+"\"\r\n",body(type,action,args));
    }
    private static String body(String type,String action,String args) { return "<s:Envelope xmlns:s='"+UpnpService.SOAP+"'><s:Body><u:"+action+" xmlns:u='"+type+"'>"+args+"</u:"+action+"></s:Body></s:Envelope>"; }
    private static Response request(int port,String method,String path,String headers,String body) throws Exception {
        return raw(port,method+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n"+headers+"Content-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\n\r\n"+body);
    }
    private static Response raw(int port,String message) throws Exception {
        try(Socket socket=new Socket(LOOP,port)) {
            socket.setSoTimeout(4000); socket.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
            byte[] bytes=socket.getInputStream().readAllBytes(); String result=new String(bytes,StandardCharsets.UTF_8);
            int end=result.indexOf("\r\n\r\n"); require(end>=0,"HTTP response headers");
            return new Response(result.substring(0,end+4),result.substring(end+4));
        }
    }
    private static String header(String headers,String name) {
        for(String line:headers.split("\r\n")) { int colon=line.indexOf(':'); if(colon>0 && line.substring(0,colon).equalsIgnoreCase(name)) return line.substring(colon+1).trim(); }
        throw new AssertionError("Missing header "+name);
    }
    private static Element document(String xml) throws Exception { return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement(); }
    private static void validateScpd(String xml) throws Exception {
        Element root=document(xml); Set<String> variables=new HashSet<>();
        NodeList states=root.getElementsByTagName("stateVariable");
        for(int i=0;i<states.getLength();i++) variables.add(((Element)states.item(i)).getElementsByTagName("name").item(0).getTextContent());
        NodeList refs=root.getElementsByTagName("relatedStateVariable");
        for(int i=0;i<refs.getLength();i++) require(variables.contains(refs.item(i).getTextContent()),"SCPD argument reference exists");
        require(root.getElementsByTagName("action").getLength()>0,"SCPD actions");
    }
    private static void assertClosed(int port) throws Exception { try(Socket ignored=new Socket(LOOP,port)) { throw new AssertionError("Stopped port accepts on "+ignored.getRemoteSocketAddress()); } catch(ConnectException expected) {} }
    private static void fault(Response response,int code) throws Exception { require(response.headers.contains("500") && response.body.contains("<errorCode>"+code+"</errorCode>"),"UPnP fault "+code+": "+response); document(response.body); }
    private static void ok(Response response) { require(response.headers.contains("200 OK"),"success: "+response); }
    private static void require(boolean success,String detail) { if(!success) throw new AssertionError(detail); }
}
