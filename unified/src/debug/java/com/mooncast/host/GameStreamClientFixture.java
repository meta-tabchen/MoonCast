package com.mooncast.host;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.NvConnectionListener;

import android.app.Service;
import android.content.Intent;
import android.media.*;
import android.os.*;
import android.util.*;
import android.util.Base64;
import com.limelight.nvstream.jni.MoonBridge;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import org.xmlpull.v1.*;

/** Deterministic loopback protocol/decoder integration, isolated from the host process. */
public abstract class GameStreamClientFixture extends Service {
    public static final int STATUS=1,STOP=2;
    private volatile boolean started,closed;
    private volatile String error="",decoderName="",outputFormat="";private volatile int decodedWidth,decodedHeight;private volatile long lastDecodeElapsed;
    private final AtomicInteger decoded=new AtomicInteger(),received=new AtomicInteger();
    private record Unit(byte[] bytes,int type){}
    private final ArrayBlockingQueue<Unit> units=new ArrayBlockingQueue<>(32);
    private MediaCodec decoder;
    private Thread decodeThread,connectionThread;
    private KeyManager[] keyManagers;
    private TrustManager[] trustManagers;
    private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),m->{
        if(m.what==STOP){shutdown();return true;}
        if(m.what==STATUS && m.replyTo!=null)try{Bundle b=new Bundle();b.putLong("nonce",m.getData().getLong("nonce"));b.putInt("pid",android.os.Process.myPid());b.putBoolean("started",this.started);b.putInt("decoded",decoded.get());b.putLong("lastDecodeElapsed",lastDecodeElapsed);b.putString("decoder",decoderName);b.putString("format",outputFormat);b.putInt("width",decodedWidth);b.putInt("height",decodedHeight);b.putInt("received",received.get());b.putString("error",this.error);Message r=Message.obtain(null,STATUS);r.setData(b);m.replyTo.send(r);}catch(RemoteException ignored){}return true;
    }));
    @Override public IBinder onBind(Intent intent){onStartCommand(intent,0,0);return binder.getBinder();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){shutdown();return START_NOT_STICKY;}
        if(connectionThread!=null)return START_NOT_STICKY;
        String client=intent.getStringExtra("client"),run=intent.getStringExtra("run");if(run==null||!run.matches("[a-f0-9]{32}")){shutdown();return START_NOT_STICKY;}int width=intent.getIntExtra("width",640),height=intent.getIntExtra("height",360);
        if(!"one".equals(client) && !"two".equals(client)){shutdown();return START_NOT_STICKY;}
        connectionThread=new Thread(()->{try{
            MoonBridge.setupBridge(new VideoDecoderRenderer(){
                public int setup(int f,int w,int h,int fps){return GameStreamClientFixture.this.setup(f,w,h,fps);}public void start(){}public void stop(){}public void cleanup(){}public int getCapabilities(){return MoonBridge.CAPABILITY_DIRECT_SUBMIT;}public void setHdrMode(boolean on,byte[] m){}
                public int submitDecodeUnit(byte[] b,int n,int t,int frame,int type,char latency,long receive,long enqueue){return GameStreamClientFixture.this.frame(b,n,t);}
            },new AudioRenderer(){public int setup(MoonBridge.AudioConfiguration c,int rate,int frames){return 0;}public void start(){}public void stop(){}public void cleanup(){}public void playDecodedAudio(short[] b){}},new NvConnectionListener(){
                public void stageStarting(String s){}public void stageComplete(String s){}public void stageFailed(String s,int flags,int code){error="stage="+s+", code="+code;}public void connectionStarted(){started=true;}public void connectionTerminated(int code){started=false;}public void connectionStatusUpdate(int s){}public void displayMessage(String s){}public void displayTransientMessage(String s){}public void rumble(short a,short b,short c){}public void rumbleTriggers(short a,short b,short c){}public void setHdrMode(boolean a,byte[] b){}public void setMotionEventState(short a,byte b,short c){}public void setControllerLED(short a,byte b,byte c,byte d){}
            });
            File dir=new File(getCacheDir(),"native-generated-test/"+run+"/"+client);
            X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Files.readAllBytes(new File(dir,"host-cert.pem").toPath())));
            String pem=new String(Files.readAllBytes(new File(dir,"host-key.pem").toPath()),java.nio.charset.StandardCharsets.US_ASCII).replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
            PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(pem,Base64.DEFAULT)));
            KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null);store.setKeyEntry("client",key,new char[0],new java.security.cert.Certificate[]{cert});
            KeyManagerFactory keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());keys.init(store,new char[0]);
            byte[] hostCert=CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(new File(getCacheDir(),"native-generated-test/"+run+"/host/host-cert.pem"))).getEncoded();
            TrustManager[] trust={new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException();}public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{if(c.length==0 || !Arrays.equals(c[0].getEncoded(),hostCert))throw new CertificateException("test host changed");}}};
            keyManagers=keys.getKeyManagers();trustManagers=trust;
            SSLContext tls=SSLContext.getInstance("TLS");tls.init(keyManagers,trustManagers,new SecureRandom());
            String info=http(tls,"/serverinfo?uniqueid=mooncast-test-"+client);
            byte[] aes=new byte[16],iv=new byte[16];new SecureRandom().nextBytes(aes);iv[3]=(byte)(client.equals("one")?1:2);
            StringBuilder hex=new StringBuilder();for(byte b:aes)hex.append(String.format(Locale.ROOT,"%02x",b&255));
            String launch=http(tls,"/launch?uniqueid=mooncast-test-"+client+"&appid=1&mode="+width+"x"+height+"x30&rikey="+hex+"&rikeyid="+(iv[3]&255)+"&localAudioPlayMode=0&surroundAudioInfo=196610&"+MoonBridge.getLaunchUrlQueryParameters());
            String rtsp=value(launch,"sessionUrl0");if(rtsp==null)throw new IOException("test launch failed");
            int result=MoonBridge.startConnection("127.0.0.1",value(info,"appversion"),value(info,"GfeVersion"),rtsp,Integer.parseInt(value(info,"ServerCodecModeSupport")),width,height,30,4000,1024,0,0x302ca,1,3000,aes,iv,1,1,0);
            if(result!=0)error="startConnection="+result;
        }catch(Throwable e){error=e.toString();android.util.Log.e("MoonCastTestClient",error,e);}},"TestMoonlightConnect");connectionThread.start();return START_NOT_STICKY;
    }
    private String http(SSLContext tls,String path)throws Exception{
        // Match Moonlight's fresh TLS-context workaround for each HTTP operation.
        SSLContext fresh=SSLContext.getInstance("TLS");fresh.init(keyManagers,trustManagers,new SecureRandom());
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://127.0.0.1:47984"+path).openConnection();c.setSSLSocketFactory(fresh.getSocketFactory());c.setHostnameVerifier((host,session)->host.equals("127.0.0.1"));c.setRequestProperty("Connection","close");c.setConnectTimeout(5000);c.setReadTimeout(5000);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}finally{c.disconnect();}
    }
    private static String value(String xml,String key)throws Exception{XmlPullParser p=Xml.newPullParser();p.setInput(new StringReader(xml));for(int event=p.next();event!=XmlPullParser.END_DOCUMENT;event=p.next())if(event==XmlPullParser.START_TAG && p.getName().equals(key))return p.nextText();return null;}
    public int setup(int format,int width,int height,int fps){try{
        if(format!=1)throw new IllegalArgumentException("H264 test only");decoder=MediaCodec.createDecoderByType("video/avc");decoderName=decoder.getName();MediaFormat media=MediaFormat.createVideoFormat("video/avc",width,height);media.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);decoder.configure(media,null,null,0);decoder.start();
        decodeThread=new Thread(()->{try{MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long number=0;while(!closed){Unit unit=units.poll(10,TimeUnit.MILLISECONDS);if(unit!=null){int input=decoder.dequeueInputBuffer(100000);if(input<0)throw new IOException("decoder input timed out");var buffer=decoder.getInputBuffer(input);if(buffer==null || buffer.capacity()<unit.bytes.length)throw new IOException("decoder input size");buffer.clear();buffer.put(unit.bytes);decoder.queueInputBuffer(input,0,unit.bytes.length,number++*33333,unit.type==0?0:MediaCodec.BUFFER_FLAG_CODEC_CONFIG);}int output;while((output=decoder.dequeueOutputBuffer(info,0))>=0){if(info.size>0){lastDecodeElapsed=SystemClock.elapsedRealtime();decoded.incrementAndGet();}decoder.releaseOutputBuffer(output,false);}if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat changed=decoder.getOutputFormat();outputFormat=changed.toString();decodedWidth=changed.containsKey("crop-left")&&changed.containsKey("crop-right")?changed.getInteger("crop-right")-changed.getInteger("crop-left")+1:changed.getInteger(MediaFormat.KEY_WIDTH);decodedHeight=changed.containsKey("crop-top")&&changed.containsKey("crop-bottom")?changed.getInteger("crop-bottom")-changed.getInteger("crop-top")+1:changed.getInteger(MediaFormat.KEY_HEIGHT);}}}catch(Exception e){if(!closed)error=e.toString();}finally{try{decoder.stop();}catch(Exception ignored){}decoder.release();}},"TestVideoDecode");decodeThread.start();return 0;
    }catch(Exception e){error=e.toString();return -1;}}
    public int frame(byte[] bytes,int length,int type){received.incrementAndGet();return units.offer(new Unit(Arrays.copyOf(bytes,length),type))?0:-1;}
    
    private void shutdown(){if(closed)return;closed=true;new Thread(()->{try{MoonBridge.interruptConnection();if(connectionThread!=null)connectionThread.join(3000);MoonBridge.stopConnection();if(decodeThread!=null)decodeThread.join(1000);}catch(Exception ignored){}android.os.Process.killProcess(android.os.Process.myPid());},"TestClientStop").start();stopSelf();}
    @Override public void onDestroy(){shutdown();super.onDestroy();}
}
