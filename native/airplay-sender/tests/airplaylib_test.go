package airplaylib

import (
    "bytes"
    "bufio"
    "context"
    "io"
    "net"
    "runtime"
    "time"
    "doubletake/internal/airplay"
    "encoding/binary"
    "testing"
)

type testHandler struct{}
func (testHandler) OnDeviceFound(string) {}
func (testHandler) OnConnected() {}
func (testHandler) OnDisconnected(string) {}
func (testHandler) OnPinRequired() {}
func (testHandler) OnError(string) {}
func (testHandler) OnLog(string) {}

func TestAirPlay1Clamp(t *testing.T) {
    for _, tc := range []struct{ w,h,wantW,wantH int }{
        {1920,1080,1280,720}, {1080,1920,1280,720},
        {720,1280,1280,720}, {640,481,640,480}, {2048,1536,960,720},
    } {
        w,h := clampAirPlay1(tc.w,tc.h)
        if w != tc.wantW || h != tc.wantH {t.Fatalf("%dx%d -> %dx%d, want %dx%d",tc.w,tc.h,w,h,tc.wantW,tc.wantH)}
    }
}

func TestAirPlay1Framing(t *testing.T) {
    s:=NewSession(testHandler{})
    var buf bytes.Buffer
    frame:=[]byte{0,0,0,1,0x65,1,2}
    s.sendFrameAirPlay1(&buf,frame,true,1280,720,42)
    all:=buf.Bytes()
    store:=[]byte("[vsize]1280,720")
    if len(all)!=256+len(store)+len(frame) {t.Fatalf("length %d",len(all))}
    if binary.LittleEndian.Uint32(all[0:4])!=uint32(len(store)) || binary.LittleEndian.Uint16(all[4:6])!=1 {t.Fatal("invalid store header")}
    if binary.LittleEndian.Uint64(all[8:16])!=42 || !bytes.Equal(all[128:128+len(store)],store) {t.Fatal("invalid store body/timestamp")}
    trigger:=all[128+len(store):]
    if binary.LittleEndian.Uint32(trigger[:4])!=uint32(len(frame)) || binary.LittleEndian.Uint16(trigger[4:6])!=0 || !bytes.Equal(trigger[128:],frame) {t.Fatal("invalid trigger")}
    buf.Reset()
    s.sendFrameAirPlay1(&buf,frame,true,0,0,0)
    if buf.Len()!=0 {t.Fatal("missing dimensions must not emit store")}
}

func TestIdleDisconnectIsIdempotent(t *testing.T) {
    s:=NewSession(testHandler{})
    s.Disconnect()
    s.Disconnect()
    if s.HasAudio() || s.StreamWidth()!=0 || s.StreamHeight()!=0 {t.Fatal("idle session has stream state")}
    s.SendAudio([]byte{0,0})
}

func TestDisconnectUnblocksBackpressuredAudioAndVideo(t *testing.T) {
    s:=NewSession(testHandler{})
    r,w:=io.Pipe()
    defer r.Close()
    s.pipeW=w
    s.audioCapture,s.audioW=airplay.NewPipeAudioCapture()
    videoDone,audioDone:=make(chan struct{}),make(chan struct{})
    go func(){s.SendFrame([]byte{0,0,0,1,0x65},true);close(videoDone)}()
    go func(){s.SendAudio(make([]byte,4096));close(audioDone)}()
    select {case <-audioDone:t.Fatal("audio should initially backpressure");case <-time.After(10*time.Millisecond):}
    s.Disconnect()
    for _,done:=range []chan struct{}{videoDone,audioDone} {
        select {case <-done:case <-time.After(time.Second):t.Fatal("Disconnect did not unblock a sender")}
    }
    s.Disconnect()
}

type recordingHandler struct{testHandler;events chan string}
func(h recordingHandler)OnConnected(){h.events<-"connected"}
func(h recordingHandler)OnDisconnected(string){h.events<-"disconnected"}
func(h recordingHandler)OnError(string){h.events<-"error"}
func(h recordingHandler)OnPinRequired(){h.events<-"pin"}

func TestDisconnectCancelsBlockedHandshake(t *testing.T) {
    listener,err:=net.Listen("tcp","127.0.0.1:0");if err!=nil{t.Fatal(err)}
    defer listener.Close()
    request,closed:=make(chan struct{}),make(chan struct{})
    go func(){
        conn,err:=listener.Accept();if err!=nil{return};defer conn.Close()
        reader:=bufio.NewReader(conn)
        for {line,err:=reader.ReadString('\n');if err!=nil{return};if line=="\r\n"{break}}
        close(request)
        io.Copy(io.Discard,reader)
        close(closed)
    }()
    h:=recordingHandler{events:make(chan string,16)}
    s:=NewSession(h)
    s.Connect("127.0.0.1",listener.Addr().(*net.TCPAddr).Port,"",1280,720,30)
    select{case <-request:case <-time.After(time.Second):t.Fatal("no info request")}
    s.Disconnect()
    select{case <-closed:case <-time.After(time.Second):t.Fatal("cancelled handshake left transport open")}
    select{case e:=<-h.events:t.Fatalf("late callback: %s",e);case <-time.After(20*time.Millisecond):}
    s.mu.Lock();defer s.mu.Unlock()
    if s.client!=nil||s.mirror!=nil||s.cancel!=nil||s.audioW!=nil||s.pipeW!=nil{t.Fatal("cancelled session regained ownership")}
}

func TestImmediateDisconnectPreventsLateConnect(t *testing.T) {
    h:=recordingHandler{events:make(chan string,1000)}
    s:=NewSession(h)
    // Occupy the only P until Disconnect. A delayed connect goroutine must see
    // cancellation already installed, including when it never started running.
    old:=runtime.GOMAXPROCS(1);defer runtime.GOMAXPROCS(old)
    for i:=0;i<200;i++ {s.Connect("127.0.0.1",1,"",1280,720,30);s.Disconnect()}
    time.Sleep(30*time.Millisecond)
    select{case e:=<-h.events:t.Fatalf("late callback after immediate cancel: %s",e);default:}
    s.mu.Lock();defer s.mu.Unlock()
    if s.client!=nil||s.cancel!=nil{t.Fatal("late connect restored session ownership")}
}

func TestOldGenerationCannotPublish(t *testing.T) {
    s:=NewSession(testHandler{})
    ctx,cancel:=context.WithCancel(context.Background());defer cancel()
    epoch:=s.epoch
    if !s.current(ctx,epoch){t.Fatal("new generation not current")}
    s.Disconnect()
    if s.current(ctx,epoch){t.Fatal("old generation remained current")}
}
