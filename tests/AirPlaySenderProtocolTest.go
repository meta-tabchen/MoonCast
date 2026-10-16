package airplay

import (
    "bufio"
    "bytes"
    "context"
    "fmt"
    "io"
    "net"
    "strconv"
    "strings"
    "testing"
    "time"
    "howett.net/plist"
)

// Run against the exact prepared source behind the unchanged shipped AAR.
// A synthetic receiver rejects empty /video SETUP and accepts session/stream
// bplists. This proves request shape, not physical-TV compatibility or pairing.
func TestMoonCastNegotiatedSetupWire(t *testing.T) {
    for _, bare := range []bool{true, false} {
        t.Run(fmt.Sprintf("bare=%v", bare), func(t *testing.T) {
            listener, err := net.Listen("tcp", "127.0.0.1:0"); if err != nil { t.Fatal(err) }; defer listener.Close()
            video, err := net.Listen("tcp", "127.0.0.1:0"); if err != nil { t.Fatal(err) }; defer video.Close()
            go func() { c,e:=video.Accept(); if e==nil { defer c.Close(); io.Copy(io.Discard,c) } }()
            result := make(chan error,1)
            go func() {
                conn,e:=listener.Accept(); if e!=nil {result<-e;return}; defer conn.Close()
                conn.SetDeadline(time.Now().Add(5*time.Second))
                r:=bufio.NewReader(conn); phase:=0
                for {
                    line,e:=r.ReadString('\n');if e!=nil {result<-e;return}
                    fields:=strings.Fields(line)
                    if len(fields)!=3 || fields[2]!="RTSP/1.0" {result<-fmt.Errorf("bad protocol line %q",line);return}
                    headers:=map[string]string{}
                    for {line,e=r.ReadString('\n');if e!=nil {result<-e;return};if line=="\r\n" {break}; k,v,ok:=strings.Cut(strings.TrimSpace(line),":");if ok {headers[strings.ToLower(k)]=strings.TrimSpace(v)}}
                    n,_:=strconv.Atoi(headers["content-length"]);body:=make([]byte,n);if _,e=io.ReadFull(r,body);e!=nil {result<-e;return}
                    if headers["cseq"]=="" || headers["user-agent"]=="" {result<-fmt.Errorf("missing common headers");return}
                    if fields[0]=="SETUP" {
                        if bare {
                            if !strings.HasSuffix(fields[1],"/video") || n!=0 {result<-fmt.Errorf("bare baseline changed");return}
                            fmt.Fprint(conn,"HTTP/1.1 501 Not Implemented\r\nContent-Length: 0\r\n\r\n")
                            result<-nil;return
                        }
                        if !strings.HasPrefix(fields[1],"rtsp://") || strings.HasSuffix(fields[1],"/video") || headers["content-type"]!="application/x-apple-binary-plist" || !bytes.HasPrefix(body,[]byte("bplist00")) {result<-fmt.Errorf("invalid negotiated SETUP");return}
                        var setup map[string]interface{};if _,e=plist.Unmarshal(body,&setup);e!=nil {result<-e;return}
                        streams,_:=setup["streams"].([]interface{})
                        response:=map[string]interface{}{}
                        if phase==0 {
                            if len(streams)!=0 || setup["timingProtocol"]!="NTP" || setup["isScreenMirroringSession"]!=true {result<-fmt.Errorf("missing session phase");return}
                        } else {
                            if len(streams)!=1 {result<-fmt.Errorf("missing stream");return}
                            stream:=streams[0].(map[string]interface{});want:=96;if phase==2 {want=110}
                            if plistInt(stream["type"])!=want || plistInt(stream["streamConnectionID"])==0 {result<-fmt.Errorf("bad stream phase");return}
                            port:=6100;if want==110 {port=video.Addr().(*net.TCPAddr).Port}
                            response["streams"]=[]interface{}{map[string]interface{}{"type":int64(want),"dataPort":int64(port),"controlPort":int64(6101)}}
                        }
                        data,_:=plist.Marshal(response,plist.BinaryFormat);if e=writeRTSPTestResponse(conn,200,nil,data);e!=nil {result<-e;return};phase++
                    } else {
                        if phase!=3 {result<-fmt.Errorf("media control before all SETUPs");return}
                        if e=writeRTSPTestResponse(conn,200,nil,nil);e!=nil {result<-e;return}
                        if fields[0]=="TEARDOWN" {result<-nil;return}
                    }
                }
            }()
            ctx,cancel:=context.WithTimeout(context.Background(),5*time.Second);defer cancel()
            client:=NewAirPlayClient("127.0.0.1",listener.Addr().(*net.TCPAddr).Port)
            if err=client.Connect(ctx);err!=nil {t.Fatal(err)};defer client.Close()
            client.fpKey=make([]byte,16);client.fpIV=make([]byte,16);client.info=&ReceiverInfo{}
            if bare {
                _,err=client.SetupMirrorAirPlay1(ctx)
                if err==nil || !strings.Contains(err.Error(),"HTTP 501") {t.Fatalf("expected synthetic 501, got %v",err)}
            } else {
                session,e:=client.SetupMirror(ctx,StreamConfig{FPS:30});if e!=nil {t.Fatal(e)};session.Close()
            }
            if e:=<-result;e!=nil {t.Fatal(e)}
        })
    }
}
