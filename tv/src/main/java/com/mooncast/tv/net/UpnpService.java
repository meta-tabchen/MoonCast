package com.mooncast.tv.net;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** UPnP AV 1.0 subset with complete descriptions for all advertised actions. Not a DLNA certification claim. */
final class UpnpService {
    static final String AV = "AVTransport", RC = "RenderingControl", CM = "ConnectionManager";
    static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String PROTOCOLS = "http-get:*:video/mp4:*,http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*,"
            + "http-get:*:audio/flac:*,http-get:*:audio/wav:*,http-get:*:application/vnd.apple.mpegurl:*,"
            + "http-get:*:application/x-mpegURL:*,http-get:*:application/dash+xml:*";
    final String name, type;
    private final Map<String, String[]> actions = new LinkedHashMap<>();
    private final Map<String, Variable> variables = new LinkedHashMap<>();
    private final PlaybackTarget player;
    private static final class Variable {
        final String type; final boolean event; final String[] allowed;
        Variable(String type, boolean event, String[] allowed) { this.type=type; this.event=event; this.allowed=allowed; }
    }
    static final class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        final int code;
        Fault(int code, String message) { super(message); this.code = code; }
    }
    UpnpService(String name, PlaybackTarget target) {
        this.name = name; type = "urn:schemas-upnp-org:service:" + name + ":1"; player = target;
        if (AV.equals(name)) avDefinition(); else if (RC.equals(name)) rcDefinition(); else cmDefinition();
    }
    private void variable(String name, String type, boolean event, String... allowed) {
        variables.put(name, new Variable(type, event, allowed));
    }
    private void action(String name, String... args) { actions.put(name, args); }
    private void avDefinition() {
        variable("LastChange", "string", true);
        variable("A_ARG_TYPE_InstanceID", "ui4", false);
        variable("A_ARG_TYPE_SeekMode", "string", false, "REL_TIME", "ABS_TIME");
        variable("A_ARG_TYPE_SeekTarget", "string", false);
        variable("TransportState", "string", false, "STOPPED", "PLAYING", "TRANSITIONING", "PAUSED_PLAYBACK", "NO_MEDIA_PRESENT");
        variable("TransportStatus", "string", false, "OK", "ERROR_OCCURRED");
        variable("TransportPlaySpeed", "string", false, "1");
        variable("CurrentPlayMode", "string", false, "NORMAL");
        variable("PlaybackStorageMedium", "string", false, "NETWORK", "NONE");
        variable("RecordStorageMedium", "string", false, "NOT_IMPLEMENTED");
        for (String v : new String[]{"PossiblePlaybackStorageMedia", "PossibleRecordStorageMedia", "CurrentRecordQualityMode", "PossibleRecordQualityModes", "RecordMediumWriteStatus", "CurrentMediaDuration", "CurrentTrackDuration", "CurrentTrackMetaData", "AVTransportURIMetaData", "NextAVTransportURIMetaData", "RelativeTimePosition", "AbsoluteTimePosition", "CurrentTransportActions"}) variable(v,"string",false);
        for (String v : new String[]{"AVTransportURI", "NextAVTransportURI", "CurrentTrackURI"}) variable(v,"uri",false);
        for (String v : new String[]{"NumberOfTracks", "CurrentTrack"}) variable(v,"ui4",false);
        for (String v : new String[]{"RelativeCounterPosition", "AbsoluteCounterPosition"}) variable(v,"i4",false);
        String instance = "in:InstanceID:A_ARG_TYPE_InstanceID";
        action("SetAVTransportURI", instance, "in:CurrentURI:AVTransportURI", "in:CurrentURIMetaData:AVTransportURIMetaData");
        action("GetMediaInfo", instance, "out:NrTracks:NumberOfTracks", "out:MediaDuration:CurrentMediaDuration", "out:CurrentURI:AVTransportURI", "out:CurrentURIMetaData:AVTransportURIMetaData", "out:NextURI:NextAVTransportURI", "out:NextURIMetaData:NextAVTransportURIMetaData", "out:PlayMedium:PlaybackStorageMedium", "out:RecordMedium:RecordStorageMedium", "out:WriteStatus:RecordMediumWriteStatus");
        action("GetTransportInfo", instance, "out:CurrentTransportState:TransportState", "out:CurrentTransportStatus:TransportStatus", "out:CurrentSpeed:TransportPlaySpeed");
        action("GetPositionInfo", instance, "out:Track:CurrentTrack", "out:TrackDuration:CurrentTrackDuration", "out:TrackMetaData:CurrentTrackMetaData", "out:TrackURI:CurrentTrackURI", "out:RelTime:RelativeTimePosition", "out:AbsTime:AbsoluteTimePosition", "out:RelCount:RelativeCounterPosition", "out:AbsCount:AbsoluteCounterPosition");
        action("GetDeviceCapabilities", instance, "out:PlayMedia:PossiblePlaybackStorageMedia", "out:RecMedia:PossibleRecordStorageMedia", "out:RecQualityModes:PossibleRecordQualityModes");
        action("GetTransportSettings", instance, "out:PlayMode:CurrentPlayMode", "out:RecQualityMode:CurrentRecordQualityMode");
        action("Play", instance, "in:Speed:TransportPlaySpeed");
        for (String a : new String[]{"Pause", "Stop", "Next", "Previous"}) action(a, instance);
        action("Seek", instance, "in:Unit:A_ARG_TYPE_SeekMode", "in:Target:A_ARG_TYPE_SeekTarget");
        action("SetPlayMode", instance, "in:NewPlayMode:CurrentPlayMode");
        action("GetCurrentTransportActions", instance, "out:Actions:CurrentTransportActions");
    }
    private void rcDefinition() {
        variable("LastChange", "string", true);
        variable("A_ARG_TYPE_InstanceID", "ui4", false);
        variable("A_ARG_TYPE_Channel", "string", false, "Master");
        variable("A_ARG_TYPE_PresetName", "string", false, "FactoryDefaults");
        variable("PresetNameList", "string", false);
        variable("Volume", "ui2", false);
        variable("Mute", "boolean", false);
        String id="in:InstanceID:A_ARG_TYPE_InstanceID", channel="in:Channel:A_ARG_TYPE_Channel";
        action("ListPresets", id, "out:CurrentPresetNameList:PresetNameList");
        action("SelectPreset", id, "in:PresetName:A_ARG_TYPE_PresetName");
        action("GetVolume", id, channel, "out:CurrentVolume:Volume");
        action("SetVolume", id, channel, "in:DesiredVolume:Volume");
        action("GetMute", id, channel, "out:CurrentMute:Mute");
        action("SetMute", id, channel, "in:DesiredMute:Mute");
    }
    private void cmDefinition() {
        for (String v:new String[]{"SourceProtocolInfo","SinkProtocolInfo","CurrentConnectionIDs"}) variable(v,"string",true);
        for (String v:new String[]{"A_ARG_TYPE_ConnectionStatus","A_ARG_TYPE_ConnectionManager","A_ARG_TYPE_Direction","A_ARG_TYPE_ProtocolInfo"}) variable(v,"string",false);
        for (String v:new String[]{"A_ARG_TYPE_ConnectionID","A_ARG_TYPE_AVTransportID","A_ARG_TYPE_RcsID"}) variable(v,"i4",false);
        action("GetProtocolInfo", "out:Source:SourceProtocolInfo", "out:Sink:SinkProtocolInfo");
        action("GetCurrentConnectionIDs", "out:ConnectionIDs:CurrentConnectionIDs");
        action("GetCurrentConnectionInfo", "in:ConnectionID:A_ARG_TYPE_ConnectionID", "out:RcsID:A_ARG_TYPE_RcsID", "out:AVTransportID:A_ARG_TYPE_AVTransportID", "out:ProtocolInfo:A_ARG_TYPE_ProtocolInfo", "out:PeerConnectionManager:A_ARG_TYPE_ConnectionManager", "out:PeerConnectionID:A_ARG_TYPE_ConnectionID", "out:Direction:A_ARG_TYPE_Direction", "out:Status:A_ARG_TYPE_ConnectionStatus");
    }
    String description() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><actionList>");
        for (Map.Entry<String,String[]> a:actions.entrySet()) {
            xml.append("<action>").append(tag("name",a.getKey())).append("<argumentList>");
            for(String arg:a.getValue()) { String[] p=arg.split(":"); xml.append("<argument>").append(tag("name",p[1])).append(tag("direction",p[0])).append(tag("relatedStateVariable",p[2])).append("</argument>"); }
            xml.append("</argumentList></action>");
        }
        xml.append("</actionList><serviceStateTable>");
        for(Map.Entry<String,Variable> v:variables.entrySet()) {
            xml.append("<stateVariable sendEvents=\"").append(v.getValue().event?"yes":"no").append("\">").append(tag("name",v.getKey())).append(tag("dataType",v.getValue().type));
            if(v.getValue().allowed.length>0) { xml.append("<allowedValueList>"); for(String value:v.getValue().allowed) xml.append(tag("allowedValue",value)); xml.append("</allowedValueList>"); }
            if(v.getKey().equals("Volume")) xml.append("<allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>");
            xml.append("</stateVariable>");
        }
        return xml.append("</serviceStateTable></scpd>").toString();
    }
    String control(byte[] body, String soapAction) throws Fault {
        Parsed parsed = parse(body, type);
        String header = soapAction == null ? "" : soapAction.trim();
        if(header.startsWith("\"") && header.endsWith("\"") && header.length()>1) header=header.substring(1,header.length()-1);
        if(!header.equals(type+"#"+parsed.action)) throw new Fault(401,"Invalid Action");
        String[] definition=actions.get(parsed.action);
        if(definition==null) throw new Fault(401,"Invalid Action");
        int inputs=0;
        for(String spec:definition) { String[] p=spec.split(":"); if(p[0].equals("in")) { inputs++; if(!parsed.args.containsKey(p[1])) throw new Fault(402,"Invalid Args"); } }
        if(inputs!=parsed.args.size()) throw new Fault(402,"Invalid Args");
        if(parsed.args.containsKey("InstanceID") && !parsed.args.get("InstanceID").equals("0")) throw new Fault(718,"Invalid InstanceID");
        Map<String,String> response;
        try {
            synchronized(player) {
                if(name.equals(AV)) response=av(parsed.action,parsed.args);
                else if(name.equals(RC)) response=rc(parsed.action,parsed.args);
                else response=cm(parsed.action,parsed.args);
            }
        } catch(IllegalArgumentException e) { throw new Fault(402,"Invalid Args"); }
        catch(IllegalStateException e) { throw new Fault(701,"Transition not available"); }
        StringBuilder values=new StringBuilder();
        for(Map.Entry<String,String> item:response.entrySet()) values.append(tag(item.getKey(),item.getValue()));
        return envelope("<u:"+parsed.action+"Response xmlns:u=\""+type+"\">"+values+"</u:"+parsed.action+"Response>");
    }
    private Map<String,String> av(String action,Map<String,String> a) throws Fault {
        PlaybackTarget.Snapshot s=player.snapshot();
        boolean loaded=!s.uri.isEmpty();
        switch(action) {
            case "SetAVTransportURI":
                String uri=a.get("CurrentURI");
                if(!uri.isEmpty()) try { LanAccess.validateMediaUri(uri); } catch(IllegalArgumentException e) { throw new Fault(714,"Illegal MIME-type or URI"); }
                if(a.get("CurrentURIMetaData").length()>32768) throw new Fault(402,"Metadata too large");
                player.setMedia(uri,a.get("CurrentURIMetaData")); return map();
            case "Play":
                if(!a.get("Speed").equals("1")) throw new Fault(717,"Play speed not supported");
                if(!loaded) throw new Fault(701,"Transition not available");
                player.play(); return map();
            case "Pause": if(!loaded) throw new Fault(701,"Transition not available"); player.pause(); return map();
            case "Stop": player.stop(); return map();
            case "Seek":
                if(!a.get("Unit").equals("REL_TIME") && !a.get("Unit").equals("ABS_TIME")) throw new Fault(710,"Seek mode not supported");
                if(!loaded) throw new Fault(701,"Transition not available");
                long position=parseTime(a.get("Target"));
                if(s.durationMillis>0 && position>s.durationMillis) throw new Fault(711,"Illegal seek target");
                player.seekTo(position); return map();
            case "Next": case "Previous": throw new Fault(711,"Illegal seek target");
            case "SetPlayMode": if(!a.get("NewPlayMode").equals("NORMAL")) throw new Fault(712,"Play mode not supported"); return map();
            case "GetTransportInfo": return map("CurrentTransportState",s.state,"CurrentTransportStatus",s.error?"ERROR_OCCURRED":"OK","CurrentSpeed","1");
            case "GetPositionInfo": return map("Track",loaded?"1":"0","TrackDuration",time(s.durationMillis),"TrackMetaData",s.metadata,"TrackURI",s.uri,"RelTime",time(s.positionMillis),"AbsTime",time(s.positionMillis),"RelCount","2147483647","AbsCount","2147483647");
            case "GetMediaInfo": return map("NrTracks",loaded?"1":"0","MediaDuration",time(s.durationMillis),"CurrentURI",s.uri,"CurrentURIMetaData",s.metadata,"NextURI","","NextURIMetaData","","PlayMedium",loaded?"NETWORK":"NONE","RecordMedium","NOT_IMPLEMENTED","WriteStatus","NOT_IMPLEMENTED");
            case "GetDeviceCapabilities": return map("PlayMedia","NETWORK","RecMedia","NOT_IMPLEMENTED","RecQualityModes","NOT_IMPLEMENTED");
            case "GetTransportSettings": return map("PlayMode","NORMAL","RecQualityMode","NOT_IMPLEMENTED");
            case "GetCurrentTransportActions": return map("Actions",transportActions(s));
            default: throw new Fault(401,"Invalid Action");
        }
    }
    private Map<String,String> rc(String action,Map<String,String> a) throws Fault {
        if(a.containsKey("Channel") && !a.get("Channel").equals("Master")) throw new Fault(402,"Invalid Args");
        PlaybackTarget.Snapshot s=player.snapshot();
        switch(action) {
            case "ListPresets": return map("CurrentPresetNameList","FactoryDefaults");
            case "SelectPreset": if(!a.get("PresetName").equals("FactoryDefaults")) throw new Fault(701,"Invalid Name"); player.setVolume(100); player.setMuted(false); return map();
            case "GetVolume": return map("CurrentVolume",Integer.toString(s.volume));
            case "GetMute": return map("CurrentMute",s.muted?"1":"0");
            case "SetVolume":
                String value=a.get("DesiredVolume");
                if(!value.matches("[0-9]{1,3}")) throw new Fault(402,"Invalid Args");
                int volume=Integer.parseInt(value); if(volume>100) throw new Fault(402,"Invalid Args"); player.setVolume(volume); return map();
            case "SetMute":
                String mute=a.get("DesiredMute");
                if(!mute.equals("0") && !mute.equals("1") && !mute.equals("true") && !mute.equals("false")) throw new Fault(402,"Invalid Args");
                player.setMuted(mute.equals("1") || mute.equals("true")); return map();
            default: throw new Fault(401,"Invalid Action");
        }
    }
    private Map<String,String> cm(String action,Map<String,String> a) throws Fault {
        switch(action) {
            case "GetProtocolInfo": return map("Source","","Sink",PROTOCOLS);
            case "GetCurrentConnectionIDs": return map("ConnectionIDs","0");
            case "GetCurrentConnectionInfo":
                if(!a.get("ConnectionID").equals("0")) throw new Fault(706,"Invalid connection reference");
                return map("RcsID","0","AVTransportID","0","ProtocolInfo","","PeerConnectionManager","","PeerConnectionID","-1","Direction","Input","Status","OK");
            default: throw new Fault(401,"Invalid Action");
        }
    }
    String eventBody() {
        PlaybackTarget.Snapshot s=player.snapshot();
        String properties;
        if(name.equals(CM)) properties=property("SourceProtocolInfo","")+property("SinkProtocolInfo",PROTOCOLS)+property("CurrentConnectionIDs","0");
        else {
            String content;
            if(name.equals(AV)) {
                content=val("TransportState",s.state)+val("TransportStatus",s.error?"ERROR_OCCURRED":"OK")+val("TransportPlaySpeed","1")
                        +val("CurrentTrack",s.uri.isEmpty()?"0":"1")+val("CurrentTrackDuration",time(s.durationMillis))
                        +val("CurrentMediaDuration",time(s.durationMillis))+val("NumberOfTracks",s.uri.isEmpty()?"0":"1")
                        +val("AVTransportURI",s.uri)+val("AVTransportURIMetaData",s.metadata)+val("CurrentTrackURI",s.uri)
                        +val("CurrentTrackMetaData",s.metadata)+val("CurrentPlayMode","NORMAL")+val("CurrentTransportActions",transportActions(s));
            } else content="<Volume channel=\"Master\" val=\""+s.volume+"\"/><Mute channel=\"Master\" val=\""+(s.muted?1:0)+"\"/>";
            String change="<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/"+(name.equals(AV)?"AVT":"RCS")+"/\"><InstanceID val=\"0\">"+content+"</InstanceID></Event>";
            properties=property("LastChange",change);
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">"+properties+"</e:propertyset>";
    }
    private static String transportActions(PlaybackTarget.Snapshot s) {
        if(s.uri.isEmpty()) return "";
        return s.state.equals("PLAYING")?"Stop,Pause,Seek":"Play,Stop,Seek";
    }
    private static String property(String name,String value) { return "<e:property>"+tag(name,value)+"</e:property>"; }
    private static String val(String name,String value) { return "<"+name+" val=\""+escape(value)+"\"/>"; }
    static String tag(String name,String value) { return "<"+name+">"+escape(value)+"</"+name+">"; }
    static String escape(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
    private static Map<String,String> map(String... items) { Map<String,String> m=new LinkedHashMap<>(); for(int i=0;i<items.length;i+=2)m.put(items[i],items[i+1]); return m; }
    static String time(long millis) { long sec=Math.max(0,millis)/1000; return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,sec/60%60,sec%60); }
    static long parseTime(String value) throws Fault {
        if(!value.matches("[0-9]{1,6}:[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,3})?")) throw new Fault(711,"Illegal seek target");
        String[] p=value.split(":");
        return Long.parseLong(p[0])*3600000L+Long.parseLong(p[1])*60000L+Math.round(Double.parseDouble(p[2])*1000);
    }
    static String fault(Fault f) {
        return envelope("<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">"+tag("errorCode",Integer.toString(f.code))+tag("errorDescription",f.getMessage())+"</UPnPError></detail></s:Fault>");
    }
    private static String envelope(String content) { return "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\""+SOAP+"\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"+content+"</s:Body></s:Envelope>"; }
    private static final class Parsed extends DefaultHandler {
        final String service;
        final Map<String,String> args=new LinkedHashMap<>();
        String action, argument;
        StringBuilder text;
        int depth, count;
        boolean bodySeen;
        Parsed(String service) { this.service=service; }
        @Override public void startElement(String ns,String local,String q,Attributes attributes) throws SAXException {
            depth++; if(++count>64 || depth>4) throw new SAXException("XML complexity");
            if(depth==1 && (!ns.equals(SOAP)||!local.equals("Envelope"))) throw new SAXException("Envelope required");
            if(depth==2) { if(bodySeen || !ns.equals(SOAP)||!local.equals("Body")) throw new SAXException("Body required"); bodySeen=true; }
            if(depth==3) { if(action!=null || !ns.equals(service)) throw new SAXException("Single service action required"); action=local; }
            if(depth==4) { if(args.containsKey(local)||!(ns.isEmpty()||ns.equals(service))) throw new SAXException("Duplicate argument"); argument=local; text=new StringBuilder(); }
        }
        @Override public void characters(char[] ch,int start,int length) throws SAXException {
            if(depth==4) { if(text.length()+length>32768) throw new SAXException("Argument too large"); text.append(ch,start,length); }
            else for(int i=start;i<start+length;i++) if(!Character.isWhitespace(ch[i])) throw new SAXException("Unexpected text");
        }
        @Override public void endElement(String ns,String local,String q) {
            if(depth==4) { args.put(argument,text.toString()); argument=null; text=null; }
            depth--;
        }
        @Override public InputSource resolveEntity(String publicId,String systemId) throws SAXException { throw new SAXException("External entity refused"); }
    }
    private static Parsed parse(byte[] bytes,String service) throws Fault {
        if(bytes.length==0 || bytes.length>ReceiverHttp.MAX_BODY) throw new Fault(402,"Invalid Args");
        // Parsing a UTF-8 StringReader makes alternate encoding declarations unable to bypass this gate.
        // DTD/entity declarations are excluded before the parser can resolve anything external.
        String xml=new String(bytes,StandardCharsets.UTF_8);
        if(xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY") || xml.indexOf('\u0000')>=0 || xml.indexOf('\ufffd')>=0) throw new Fault(402,"Invalid Args");
        if(xml.startsWith("\ufeff")) xml=xml.substring(1);
        try {
            SAXParserFactory f=SAXParserFactory.newInstance(); f.setNamespaceAware(true);
            XMLReader reader=f.newSAXParser().getXMLReader();
            Parsed parsed=new Parsed(service); reader.setContentHandler(parsed); reader.setEntityResolver(parsed); reader.setErrorHandler(parsed);
            reader.parse(new InputSource(new StringReader(xml)));
            if(parsed.action==null || !parsed.bodySeen) throw new SAXException("Missing action");
            return parsed;
        } catch(Exception e) { throw new Fault(402,"Invalid Args"); }
    }
}
