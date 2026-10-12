package com.mooncast.transfer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class TransferTest {
    static int passed;
    static final InetAddress LOOP=InetAddress.getLoopbackAddress();
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static void rejects(Throwing action,String label)throws Exception{try{action.run();throw new AssertionError(label);}catch(IOException expected){passed++;System.out.println("PASS "+label);}}
    interface Throwing{void run()throws Exception;}
    static final class Receiver implements AutoCloseable,ApkTransferServer.Listener{
        final File dir;final ApkTransferServer server;final ApkTransferServer.Session session;
        final CountDownLatch done=new CountDownLatch(1);volatile File received;volatile int errors;
        Receiver()throws Exception{dir=Files.createTempDirectory("apk-server-test").toFile();server=new ApkTransferServer(dir,LOOP,p->p.isLoopbackAddress(),"12345678-1234-1234-1234-123456789abc",this);session=server.start();}
        public void onProgress(long n,long total){}
        public void onReceived(File f,String hash){received=f;done.countDown();}
        public void onError(String s){errors++;}
        public void close(){server.close();for(File f:dir.listFiles())f.delete();dir.delete();}
        ApkTransferClient client(){return new ApkTransferClient(LOOP,session.port);}
    }
    static File apk(boolean valid)throws Exception{
        File f=File.createTempFile("ordinary-", ".apk");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(f))){out.putNextEntry(new ZipEntry(valid?"AndroidManifest.xml":"split.apk"));out.write(new byte[]{3,0,8,0,8,0,0,0});out.closeEntry();}
        return f;
    }
    static String raw(Receiver r,String request)throws Exception{
        try(Socket socket=new Socket(LOOP,r.session.port)){socket.setSoTimeout(3000);socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));socket.shutdownOutput();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=socket.getInputStream().read(b))!=-1)out.write(b,0,n);return out.toString("US-ASCII");}
    }
    static String headers(long length,String hash){return "PUT /v1/apk HTTP/1.1\r\nContent-Length: "+length+"\r\nX-APK-Name: test.apk\r\nX-APK-SHA256: "+hash+"\r\n\r\n";}
    static void put16(byte[] bytes,int at,int value){bytes[at]=(byte)value;bytes[at+1]=(byte)(value>>>8);}
    static void put32(byte[] bytes,int at,long value){put16(bytes,at,(int)value);put16(bytes,at+2,(int)(value>>>16));}
    static int get16(byte[] bytes,int at){return (bytes[at]&255)|((bytes[at+1]&255)<<8);}
    static int get32(byte[] bytes,int at){return get16(bytes,at)|(get16(bytes,at+2)<<16);}
    static void rejectBytes(byte[] bytes,String label)throws Exception{
        File file=File.createTempFile("apk-directory-", ".apk");
        try{Files.write(file.toPath(),bytes);rejects(()->ApkTransfer.checkContainer(file),label);}finally{file.delete();}
    }
    static void directoryChecks(File valid)throws Exception{
        byte[] base=Files.readAllBytes(valid.toPath());int end=base.length-22,central=get32(base,end+16);
        byte[] changed=base.clone();put16(changed,end+4,1);rejectBytes(changed,"multi-disk EOCD rejected before ZipFile");
        changed=base.clone();put16(changed,end+8,65535);put16(changed,end+10,65535);rejectBytes(changed,"ZIP64 EOCD sentinel rejected before ZipFile");
        changed=base.clone();put16(changed,end+8,ApkTransfer.MAX_ZIP_ENTRIES+1);put16(changed,end+10,ApkTransfer.MAX_ZIP_ENTRIES+1);rejectBytes(changed,"excessive entry count rejected before ZipFile");
        changed=base.clone();put32(changed,end+16,central+1);rejectBytes(changed,"central directory outside exact bounds rejected");
        changed=base.clone();changed[central]=0;rejectBytes(changed,"invalid central entry signature rejected");
        changed=base.clone();put16(changed,central+28,ApkTransfer.MAX_ENTRY_NAME_BYTES+1);rejectBytes(changed,"oversized central filename rejected");
        changed=base.clone();put16(changed,central+30,65535);rejectBytes(changed,"central entry length overrun rejected");
        changed=base.clone();put32(changed,central+20,0xffffffffL);rejectBytes(changed,"ZIP64 entry size sentinel rejected");
        changed=base.clone();put32(changed,central+42,central);rejectBytes(changed,"payload offset into central directory rejected");
        changed=java.util.Arrays.copyOf(base,base.length+1);rejectBytes(changed,"trailing bytes after EOCD rejected");
        changed=base.clone();put16(changed,end+20,1);rejectBytes(changed,"truncated ZIP comment rejected");

        // Add a ZIP64 extra field without a sentinel: this must also fail closed.
        int extra=central+46+get16(base,central+28);
        changed=new byte[base.length+4];System.arraycopy(base,0,changed,0,extra);
        System.arraycopy(base,extra,changed,extra+4,base.length-extra);
        put16(changed,extra,1);put16(changed,extra+2,0);put16(changed,central+30,4);
        put32(changed,end+4+12,get32(base,end+12)+4);
        rejectBytes(changed,"ZIP64 extra field rejected without sentinel");

        // Sparse files exercise declared allocation bounds without allocating huge test data.
        File sparse=File.createTempFile("apk-directory-bound-", ".apk");
        try(RandomAccessFile file=new RandomAccessFile(sparse,"rw")){
            long directorySize=ApkTransfer.MAX_DIRECTORY_BYTES+1;
            byte[] record=new byte[22];put32(record,0,0x06054b50L);put16(record,8,1);put16(record,10,1);put32(record,12,directorySize);
            file.setLength(directorySize+22);file.seek(directorySize);file.write(record);
            rejects(()->ApkTransfer.checkContainer(sparse),"oversized actual central directory rejected before allocation");
            file.setLength(ApkTransfer.MAX_BYTES+1);
            rejects(()->ApkTransfer.checkContainer(sparse),"oversized archive rejected before tail allocation");
        }finally{sparse.delete();}

        File commented=File.createTempFile("apk-max-comment-", ".apk");
        try{
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(commented))){
                out.setComment(new String(new char[65535]).replace('\0','c'));
                out.putNextEntry(new ZipEntry("AndroidManifest.xml"));out.write(new byte[]{3,0,8,0,8,0,0,0});out.closeEntry();
            }
            ApkTransfer.checkContainer(commented);check(true,"maximum legal ZIP comment fits bounded tail scan");
        }finally{commented.delete();}

        // APK v2/v3 signing blocks live before the central directory; a gap stays valid.
        File signingGap=File.createTempFile("apk-signing-gap-", ".apk");
        try{
            changed=new byte[base.length+32];System.arraycopy(base,0,changed,0,central);
            System.arraycopy(base,central,changed,central+32,base.length-central);put32(changed,end+32+16,central+32);
            Files.write(signingGap.toPath(),changed);ApkTransfer.checkContainer(signingGap);
            check(true,"pre-central signing-block space remains supported");
        }finally{signingGap.delete();}
    }
    static void resourceChecks()throws Exception{
        File file=File.createTempFile("apk-resource-bound-", ".apk");
        try{
            for(int extra=0;extra<=1;extra++){
                try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){
                    out.putNextEntry(new ZipEntry("AndroidManifest.xml"));out.write(new byte[]{3,0,8,0,8,0,0,0});out.closeEntry();
                    out.putNextEntry(new ZipEntry("resources.arsc"));
                    byte[] block=new byte[65536];long remaining=ApkTransfer.MAX_RESOURCE_TABLE_BYTES+extra;
                    while(remaining>0){int bytes=(int)Math.min(block.length,remaining);out.write(block,0,bytes);remaining-=bytes;}
                    out.closeEntry();
                }
                if(extra==0){ApkTransfer.checkContainer(file);check(true,"resource table at 64 MiB structural limit accepted");}
                else rejects(()->ApkTransfer.checkContainer(file),"compressed resource table above 64 MiB rejected before metadata parsing");
            }
            byte[] deceptive=Files.readAllBytes(file.toPath());int end=deceptive.length-22,first=get32(deceptive,end+16);
            int resource=first+46+get16(deceptive,first+28)+get16(deceptive,first+30)+get16(deceptive,first+32);
            put32(deceptive,resource+24,8);Files.write(file.toPath(),deceptive);
            rejects(()->ApkTransfer.checkContainer(file),"actual inflated resource bytes cannot bypass a falsely small declared size");
        }finally{file.delete();}
    }
    public static void main(String[]args)throws Exception{
        File valid=apk(true),invalid=apk(false);String hash=ApkTransfer.hash(valid);
        try{
            ApkTransfer.checkContainer(valid);check(true,"ordinary APK structural acceptance");
            directoryChecks(valid);
            resourceChecks();
            rejects(()->ApkTransfer.checkContainer(invalid),"ZIP bundle without manifest rejected");
            rejects(()->ApkTransfer.checkName("name.apks"),"APKS rejected");
            rejects(()->ApkTransfer.checkName("name.xapk"),"XAPK rejected");
            rejects(()->ApkTransfer.checkName("../name.apk"),"traversal name rejected");
            try(Receiver r=new Receiver();ApkTransferClient c=r.client()){
                check(r.session.port==48152,"fixed port 48152");
                check(c.info().contains("\"pairing\":false") && c.info().contains(r.session.receiverId),"capability and stable receiver ID available without pairing");
                check(raw(r,"GET /info HTTP/1.1\r\nHost: localhost\r\n\r\n").contains("application/json"),"GET info works without Content-Length");
                check(raw(r,"POST /v1/pair HTTP/1.1\r\nContent-Length: 6\r\n\r\n123456").startsWith("HTTP/1.1 404"),"pairing endpoint removed");
                try(ServerSocket conflict=new ServerSocket()){
                    rejects(()->conflict.bind(new InetSocketAddress(LOOP,48152)),"fixed port owned while receiver enabled");
                }
                c.send(valid,"demo.apk",hash,null);check(r.done.await(1,TimeUnit.SECONDS),"valid file delivered");
                check(ApkTransfer.hash(r.received).equals(hash),"received bytes match SHA-256");
                rejects(()->c.send(valid,"demo.apk",hash,null),"one-upload window prevents replay");
                r.server.close();check(r.received.isFile(),"server close preserves completed APK for install review");
            }
            try(Receiver r=new Receiver();ApkTransferClient c=r.client()){
                rejects(()->c.send(valid,"demo.apk",new String(new char[64]).replace('\0','0'),null),"hash mismatch rejected");
                Thread.sleep(50);check(r.dir.list().length==0,"hash mismatch staging cleanup");
                rejects(()->c.send(valid,"demo.apk",hash,null),"failed upload consumes receive window");
            }
            try(Receiver r=new Receiver();ApkTransferClient c=r.client()){
                check(raw(r,headers(valid.length(),hash)+"short").startsWith("HTTP/1.1 400"),"truncated request rejected");
                Thread.sleep(50);check(r.dir.list().length==0,"truncated staging cleanup");
            }
            try(Receiver r=new Receiver();ApkTransferClient c=r.client()){
                check(raw(r,headers(ApkTransfer.MAX_BYTES+1,hash)).startsWith("HTTP/1.1 413"),"oversized request rejected before file");
                check(raw(r,"POST /v1/pair HTTP/1.1\r\nContent-Length: 6\r\nContent-Length: 6\r\n\r\n123456").startsWith("HTTP/1.1 400"),"duplicate content length rejected");
                check(raw(r,"POST /v1/pair HTTP/1.1\r\nContent-Length: 6\r\nTransfer-Encoding: chunked\r\n\r\n123456").startsWith("HTTP/1.1 400"),"TE and CL ambiguity rejected");
                check(raw(r,"POST http://elsewhere/v1/pair HTTP/1.1\r\nContent-Length: 6\r\n\r\n123456").startsWith("HTTP/1.1 404"),"proxy style URLs rejected");
                check(raw(r,"POST /v1/pair HTTP/1.1\r\nX-Long: "+new String(new char[5000]).replace('\0','a')+"\r\nContent-Length: 6\r\n\r\n123456").startsWith("HTTP/1.1 431"),"oversized headers rejected");
                java.lang.reflect.Field deadline=ApkTransferServer.class.getDeclaredField("deadlineNanos");deadline.setAccessible(true);deadline.setLong(r.server,System.nanoTime()-1);
                check(raw(r,headers(1,hash)+"x").startsWith("HTTP/1.1 410"),"expired receive window rejected");
            }
            try(Receiver r=new Receiver();ApkTransferClient c=r.client()){
                try(Socket slow=new Socket(LOOP,r.session.port)){
                    slow.getOutputStream().write(headers(valid.length(),hash).getBytes(StandardCharsets.US_ASCII));slow.getOutputStream().write(1);slow.getOutputStream().flush();
                    long until=System.currentTimeMillis()+2000;while(r.dir.list().length==0 && System.currentTimeMillis()<until)Thread.sleep(10);
                    check(r.dir.list().length==1,"active upload stages privately");
                    check(raw(r,headers(1,hash)+"x").startsWith("HTTP/1.1 409"),"concurrent upload rejected");
                    r.server.close();Thread.sleep(100);check(r.dir.list().length==0,"close interrupts upload and cleans partial");
                }
            }
            try(Receiver r=new Receiver();ApkTransferClient c=r.client()){
                c.close();rejects(()->c.info(),"canceled client cannot connect");
            }
            try(ServerSocket conflict=new ServerSocket(48152,1,LOOP)){
                File dir=Files.createTempDirectory("apk-port-conflict").toFile();
                try(ApkTransferServer server=new ApkTransferServer(dir,LOOP,p->true,"12345678-1234-1234-1234-123456789abc",new ApkTransferServer.Listener(){public void onProgress(long a,long b){}public void onReceived(File f,String h){}public void onError(String e){}})){
                    rejects(()->server.start(),"port conflict fails without random-port fallback");
                }finally{dir.delete();}
            }
            try(Receiver r=new Receiver()){
                for(int i=0;i<60;i++)check(raw(r,"GET /info HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 200"),"bounded info request "+i);
                check(raw(r,"GET /info HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 429"),"request rate limit enforced");
            }
            // A peer which accepts TCP but never reads must not trap cancellation in a write.
            File large=File.createTempFile("large-apk-", ".apk");
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(large))){
                out.putNextEntry(new ZipEntry("AndroidManifest.xml"));out.write(new byte[]{3,0,8,0,8,0,0,0});out.closeEntry();
                out.putNextEntry(new ZipEntry("assets/payload"));byte[] block=new byte[65536];java.util.Random random=new java.util.Random(19);
                for(int i=0;i<256;i++){random.nextBytes(block);out.write(block);}out.closeEntry();
            }
            try(ServerSocket stalled=new ServerSocket(0,1,LOOP);ApkTransferClient client=new ApkTransferClient(LOOP,stalled.getLocalPort())){
                CountDownLatch ended=new CountDownLatch(1);
                Thread sender=new Thread(()->{try{client.send(large,"large.apk",hash,null);}catch(IOException expected){}finally{ended.countDown();}});
                sender.start();try(Socket accepted=stalled.accept()){
                    Thread.sleep(100);client.close();check(ended.await(2,TimeUnit.SECONDS),"client close unblocks stalled upload writes");
                }
            }finally{large.delete();}
            // Exercise the exact watchdog method with an accelerated deadline, avoiding a five-minute test.
            try(ServerSocket stalled=new ServerSocket(0,1,LOOP);ApkTransferClient client=new ApkTransferClient(LOOP,stalled.getLocalPort())){
                java.lang.reflect.Method connect=ApkTransferClient.class.getDeclaredMethod("connect");connect.setAccessible(true);
                Socket socket=(Socket)connect.invoke(client);
                try(Socket accepted=stalled.accept()){
                    java.lang.reflect.Method deadline=ApkTransferClient.class.getDeclaredMethod("deadline",Socket.class,long.class);deadline.setAccessible(true);deadline.invoke(client,socket,30L);
                    Thread.sleep(100);check(socket.isClosed(),"absolute client watchdog closes socket");
                }
            }
            System.out.println("All "+passed+" checks passed.");
        }finally{valid.delete();invalid.delete();}
    }
}
