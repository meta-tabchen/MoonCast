package com.mooncast.tv.apk;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Generated binary Android manifests; not installable or trusted application fixtures. */
public final class ApkManifestHostTest {
    static final String[] STRINGS={"manifest","application","split","feature.camera","isSplitRequired","uses-split","isFeatureSplit","meta-data","name","com.android.vending.splits.required","requiredSplitTypes","value","false","true","com.android.vending.splits","resource","0"};
    public static void main(String[] args)throws Exception{
        ApkSingleManifest.check(manifest(-1,0,0,false,false));
        ApkSingleManifest.check(manifest(4,0x12,0,false,false));
        reject(manifest(2,3,3,false,false),"lone split");
        reject(manifest(4,0x12,-1,false,false),"split-required base");
        reject(manifest(6,0x12,-1,false,false),"feature split");
        reject(manifest(10,1,0x7f010001,false,false),"referenced required split types");
        reject(manifest(-1,0,0,true,false),"uses-split");
        reject(manifest(-1,0,0,false,true),"Play required split marker without value");
        ApkSingleManifest.check(metadata(9,11,0x12,0));
        ApkSingleManifest.check(metadata(9,11,3,12));
        ApkSingleManifest.check(metadata(9,11,3,16));
        ApkSingleManifest.check(metadata(14,15,1,0x7f010001));
        reject(metadata(9,11,0x12,-1),"Play required=true");
        reject(metadata(9,11,3,13),"Play required string true");
        reject(metadata(9,11,1,0x7f010001),"Play required unresolved value");
        reject(metadata(9,15,1,0x7f010001),"Play required unresolved resource");
        byte[] bad=manifest(-1,0,0,false,false);bad[4]=0;reject(bad,"wrong XML length");
        reject(new byte[]{3,0,8,0,8,0,0,0},"missing root/application");
        if(args.length>0)for(String path:args)ApkSingleManifest.requireStandalone(new File(path));
        System.out.println("PASS standalone binary manifest policy: ordinary/false/optional metadata allowed; split, split-required, feature, referenced flag, uses-split, required Play marker, truncation and missing application rejected");
    }
    static void reject(byte[] data,String label)throws Exception{try{ApkSingleManifest.check(data);}catch(IOException expected){return;}throw new AssertionError("Accepted "+label);}
    static byte[] manifest(int attr,int type,int value,boolean usesSplit,boolean play)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();body.write(pool());
        boolean rootAttr=attr==2||attr==6||attr==10;
        body.write(start(0,rootAttr?attr:-1,type,value));
        body.write(start(1,rootAttr?-1:attr,type,value));
        if(usesSplit){body.write(start(5,-1,0,0));body.write(end(5));}
        if(play){body.write(start(7,8,3,9));body.write(end(7));}
        body.write(end(1));body.write(end(0));
        ByteArrayOutputStream all=new ByteArrayOutputStream();u16(all,3);u16(all,8);u32(all,8+body.size());all.write(body.toByteArray());return all.toByteArray();
    }
    static byte[] metadata(int name,int valueName,int type,int value)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();body.write(pool());
        body.write(start(0,-1,0,0));body.write(start(1,-1,0,0));
        body.write(start(7,8,3,name,valueName,type,value));body.write(end(7));body.write(end(1));body.write(end(0));
        ByteArrayOutputStream all=new ByteArrayOutputStream();u16(all,3);u16(all,8);u32(all,8+body.size());all.write(body.toByteArray());return all.toByteArray();
    }
    static byte[] pool()throws IOException{
        ByteArrayOutputStream strings=new ByteArrayOutputStream();int[] offsets=new int[STRINGS.length];
        for(int i=0;i<STRINGS.length;i++){offsets[i]=strings.size();byte[] b=STRINGS[i].getBytes(StandardCharsets.UTF_8);strings.write(b.length);strings.write(b.length);strings.write(b);strings.write(0);}
        while(strings.size()%4!=0)strings.write(0);
        ByteArrayOutputStream out=new ByteArrayOutputStream();u16(out,1);u16(out,28);u32(out,28+4*STRINGS.length+strings.size());u32(out,STRINGS.length);u32(out,0);u32(out,0x100);u32(out,28+4*STRINGS.length);u32(out,0);for(int n:offsets)u32(out,n);out.write(strings.toByteArray());return out.toByteArray();
    }
    static byte[] start(int name,int... attrs)throws IOException{
        int count=attrs[0]<0?0:attrs.length/3;
        ByteArrayOutputStream out=new ByteArrayOutputStream();u16(out,0x102);u16(out,16);u32(out,36+count*20);u32(out,1);u32(out,-1);u32(out,-1);u32(out,name);u16(out,20);u16(out,20);u16(out,count);u16(out,0);u16(out,0);u16(out,0);
        for(int i=0;i<count;i++){u32(out,-1);u32(out,attrs[i*3]);u32(out,-1);u16(out,8);out.write(0);out.write(attrs[i*3+1]);u32(out,attrs[i*3+2]);}return out.toByteArray();
    }
    static byte[] end(int name)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();u16(o,0x103);u16(o,16);u32(o,24);u32(o,1);u32(o,-1);u32(o,-1);u32(o,name);return o.toByteArray();}
    static void u16(OutputStream o,int n)throws IOException{o.write(n);o.write(n>>>8);}
    static void u32(OutputStream o,int n)throws IOException{u16(o,n);u16(o,n>>>16);}
}
