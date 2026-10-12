#!/usr/bin/env python3
"""Execute both production metadata methods against an explicit AOSP archive-flag model.

This is a host compatibility regression, not Android cryptographic verification.
The real API29 old-failure and final unrun device boundary are documented separately.
"""
from pathlib import Path
import json, os, subprocess, tempfile
import xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def method(path,signature):
 s=(R/path).read_text();start=s.index(signature);i=s.index('{',start);depth=1;j=i+1
 while depth:
  depth+=(s[j]=='{')-(s[j]=='}');j+=1
 return s[start:j]
phone=method('app/src/main/java/com/mooncast/host/ApkStager.java','static Selection metadata(')
tv=method('tv/src/main/java/com/mooncast/tv/apk/ApkMetadataValidator.java','public static Metadata inspect(').replace('com.mooncast.transfer.ApkTransfer','ApkTransfer').replace('java.nio.charset.StandardCharsets.US_ASCII','java.nio.charset.StandardCharsets.US_ASCII')
# Resolve the same production resources as Android Context.getString; do not replace
# the localized metadata method with an English-only approximation.
resource_names = ('phone_apk_bad_metadata', 'phone_apk_signer_missing')
translations = []
for locale in ('values', 'values-zh'):
    entries = {e.attrib['name']: ''.join(e.itertext())
               for e in ET.parse(R / 'app/src/main/res' / locale / 'phone_localization.xml').getroot()}
    translations.append([entries[name] for name in resource_names])
resource_fixture = 'class R {static class string {' + ''.join(
    'static final int ' + name + '=' + str(i) + ';' for i, name in enumerate(resource_names)) + '}}\n'
resource_fixture += 'class Strings {static final String[][] values={' + ','.join(
    '{' + ','.join(json.dumps(value, ensure_ascii=False) for value in row) + '}'
    for row in translations) + '};}\n'
source='''import java.io.*;import java.security.*;''' + resource_fixture + ''' 
class Build {static class VERSION {static int SDK_INT;}}
class Signature {byte[] toByteArray(){return new byte[]{1,2,3};}}
class SigningInfo {Signature[] getApkContentsSigners(){return new Signature[]{new Signature()};}}
class ApplicationInfo {String[] splitSourceDirs;String sourceDir,publicSourceDir;}
class PackageInfo {ApplicationInfo applicationInfo=new ApplicationInfo();String packageName="fixture.test",versionName="1";String[] splitNames;SigningInfo signingInfo;Signature[] signatures;int versionCode=1;long getLongVersionCode(){return 1;}}
class PackageManager {
 static final int GET_SIGNATURES=64,GET_SIGNING_CERTIFICATES=0x08000000;int flags;boolean signed=true,valid=true;
 PackageInfo getPackageArchiveInfo(String path,int f){flags=f;if(!valid)return null;PackageInfo p=new PackageInfo();
 // Android10 PackageManager.java: collectCertificates is gated by GET_SIGNATURES.
 if(signed&&(f&GET_SIGNATURES)!=0){p.signatures=new Signature[]{new Signature()};if((f&GET_SIGNING_CERTIFICATES)!=0)p.signingInfo=new SigningInfo();}return p;}
 CharSequence getApplicationLabel(ApplicationInfo i){return "Fixture";}}
class Context {final PackageManager pm=new PackageManager();final int language;Context(){this(0);}Context(int language){this.language=language;}PackageManager getPackageManager(){return pm;}String getString(int id){return Strings.values[language][id];}}
class ApkTransfer {static final long MAX_BYTES=1000000;static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new RuntimeException(e);}}static String hex(byte[] b){return java.util.HexFormat.of().formatHex(b);}}
class ApkSingleManifest {static void requireStandalone(File f){/* Container behavior covered by separate real-fixture tests. */}}
class Phone {
 static class Selection {final String signers;Selection(File f,String n,String l,String p,String v,String h,String s){signers=s;}}
 static String displayText(String s,int n){return s;}
'''+phone+'''
}
class Tv {
 static class Metadata {final String signers;Metadata(String a,String b,String c,String s,String h,long n){signers=s;}}
 static void requirePrivateFile(File d,File f) throws IOException {if(!f.isFile())throw new IOException();}
 static String sha256(File f) throws Exception {return ApkTransfer.hex(ApkTransfer.sha256().digest(java.nio.file.Files.readAllBytes(f.toPath())));}
 static String hex(byte[] b){return ApkTransfer.hex(b);}static String safe(String s){return s;}
'''+tv+'''
}
public class ArchiveSignatureFixture {
 static int checks;static void check(boolean x){checks++;if(!x)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception {
 File f=File.createTempFile("fixture-", ".apk");try{java.nio.file.Files.write(f.toPath(),new byte[]{1,2,3});String h=Tv.sha256(f);
 for(int sdk:new int[]{26,27,28,29,30,35}){Build.VERSION.SDK_INT=sdk;Context c=new Context();
 check(Phone.metadata(c,f,"fixture.apk",h).signers.matches("[a-f0-9]{64}"));check((c.pm.flags&64)!=0);check(sdk<28||(c.pm.flags&0x08000000)!=0);
 check(Tv.inspect(c.pm,f.getParentFile(),f,h).signers.matches("[a-f0-9]{64}"));check((c.pm.flags&64)!=0);check(sdk<28||(c.pm.flags&0x08000000)!=0);
 c.pm.signed=false;try{Phone.metadata(c,f,"fixture.apk",h);throw new AssertionError("unsigned phone accepted");}catch(IOException expected){checks++;}
 try{Tv.inspect(c.pm,f.getParentFile(),f,h);throw new AssertionError("unsigned TV accepted");}catch(IOException expected){checks++;}
 }
 for(int language:new int[]{0,1}){Context localized=new Context(language);localized.pm.signed=false;
 try{Phone.metadata(localized,f,"fixture.apk",h);throw new AssertionError("unsigned localized APK accepted");}
 catch(IOException expected){check(expected.getMessage().equals(localized.getString(R.string.phone_apk_signer_missing)));}
 localized.pm.valid=false;
 try{Phone.metadata(localized,f,"fixture.apk",h);throw new AssertionError("invalid localized metadata accepted");}
 catch(IOException expected){check(expected.getMessage().equals(localized.getString(R.string.phone_apk_bad_metadata)));}
 }
 Context c=new Context();Build.VERSION.SDK_INT=29;PackageInfo old=c.pm.getPackageArchiveInfo(f.toString(),0x08000000);check(old.signingInfo==null&&old.signatures==null);
 PackageInfo fixed=c.pm.getPackageArchiveInfo(f.toString(),0x08000000|64);check(fixed.signingInfo.getApkContentsSigners().length==1);
 System.out.println("PASS "+checks+" production phone/TV archive metadata checks; AOSP API29 old flags fail/new flags collect signers; unsigned rejected; English/Chinese production error resources checked. Host model only.");
 }finally{f.delete();}}
}
'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'ArchiveSignatureFixture.java';p.write_text(source, encoding='utf-8')
 java=Path(os.environ['JAVA_HOME'])/'bin'
 subprocess.run([java/'javac','-encoding','UTF-8','-d',tmp,p],check=True)
 subprocess.run([java/'java','-cp',tmp,'ArchiveSignatureFixture'],check=True)
