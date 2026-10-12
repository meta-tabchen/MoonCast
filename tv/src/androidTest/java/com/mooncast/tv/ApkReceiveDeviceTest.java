package com.mooncast.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import com.mooncast.tv.apk.ApkMetadataValidator;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Validation-only suite; never launches or approves an Android installer. */
final class ApkReceiveDeviceTest {
    private final Instrumentation instrumentation;
    private final Bundle arguments;
    ApkReceiveDeviceTest(Instrumentation instrumentation,Bundle arguments){this.instrumentation=instrumentation;this.arguments=arguments;}
    void run(){
        Bundle result=new Bundle();File dir=null;
        try {
            Context context=instrumentation.getTargetContext();
            dir=new File(context.getCacheDir(),"apk-receive/validation-test");dir.mkdirs();
            File apk=new File(dir,"fixture.apk");
            Files.copy(new File(context.getApplicationInfo().sourceDir).toPath(),apk.toPath(),StandardCopyOption.REPLACE_EXISTING);
            String hash=ApkMetadataValidator.sha256(apk);
            if(android.os.Build.VERSION.SDK_INT>=28) {
                android.content.pm.PackageManager pm=context.getPackageManager();
                android.content.pm.PackageInfo modern=pm.getPackageArchiveInfo(apk.getAbsolutePath(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                android.content.pm.PackageInfo both=pm.getPackageArchiveInfo(apk.getAbsolutePath(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES|android.content.pm.PackageManager.GET_SIGNATURES);
                int oldCount=modern!=null&&modern.signingInfo!=null?modern.signingInfo.getApkContentsSigners().length:0;
                int newCount=both!=null&&both.signingInfo!=null?both.signingInfo.getApkContentsSigners().length:both!=null&&both.signatures!=null?both.signatures.length:0;
                Bundle diagnostic=new Bundle();diagnostic.putString("stream","Archive signature compatibility: modern-only="+oldCount+", combined="+newCount+"\n");instrumentation.sendStatus(0,diagnostic);
                check(newCount>0,"Combined archive flags did not verify signer");
            }
            ApkMetadataValidator.Metadata info=ApkMetadataValidator.inspect(context.getPackageManager(),dir,apk,hash);
            check(info.packageName.equals(context.getPackageName()),"Parsed package differs");
            check(!info.certificateSha256.isEmpty(),"Missing certificate fingerprint");
            check(info.bytes==apk.length(),"Wrong size");
            check(FileProvider.getUriForFile(context,context.getPackageName()+".apkfiles",apk).getScheme().equals("content"),"Must use content URI");
            ProviderInfo provider=context.getPackageManager().resolveContentProvider(context.getPackageName()+".apkfiles",0);
            check(provider!=null && !provider.exported && provider.grantUriPermissions,"Unsafe provider manifest");
            final File privateDir=dir;
            reject(()->ApkMetadataValidator.inspect(context.getPackageManager(),privateDir,apk,"0000000000000000000000000000000000000000000000000000000000000000"),"Wrong hash accepted");
            File invalid=new File(dir,"invalid.apk");Files.write(invalid.toPath(),new byte[]{0,1,2,3});
            reject(()->ApkMetadataValidator.inspect(context.getPackageManager(),privateDir,invalid,ApkMetadataValidator.sha256(invalid)),"Invalid archive accepted");
            File outside=new File(context.getCacheDir(),"outside-test.apk");
            try {
                Files.write(outside.toPath(),new byte[]{1});
                reject(()->ApkMetadataValidator.requirePrivateFile(privateDir,outside),"Path escape accepted");
                reject(()->FileProvider.getUriForFile(context,context.getPackageName()+".apkfiles",outside),"Provider exposed unrelated cache");
            } finally {outside.delete();}
            File bundle=new File(dir,"bundle.xapk");Files.copy(apk.toPath(),bundle.toPath(),StandardCopyOption.REPLACE_EXISTING);
            reject(()->ApkMetadataValidator.requirePrivateFile(privateDir,bundle),"Bundle extension accepted");
            if(arguments!=null && "true".equals(arguments.getString("ui")))testReceiveUi(context,new File(context.getApplicationInfo().sourceDir));
            result.putString("stream","APK_RECEIVE_VALIDATION_PASSED: package/certificate/hash, corruption, wrong hash, private boundary, scoped provider, bundle rejection. No installer or permission approval attempted.\n");
            instrumentation.finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","APK_RECEIVE_VALIDATION_FAILED\n"+android.util.Log.getStackTraceString(e));instrumentation.finish(Activity.RESULT_CANCELED,result);}
        finally {if(dir!=null){File[] children=dir.listFiles();if(children!=null)for(File child:children)child.delete();dir.delete();}}
    }
    private void testReceiveUi(Context context,File fixture)throws Exception {
        android.content.Intent intent=new android.content.Intent(context,TvActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=instrumentation.startActivitySync(intent);
        com.mooncast.tv.apk.ApkReceiveController controller=(com.mooncast.tv.apk.ApkReceiveController)field(activity,"apkReceiver");
        android.net.ConnectivityManager cm=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.Network network=cm.getActiveNetwork();
        android.net.LinkAddress chosen=null;
        for(android.net.LinkAddress address:cm.getLinkProperties(network).getLinkAddresses())
            if(address.getAddress() instanceof java.net.Inet4Address && address.getAddress().isSiteLocalAddress()){chosen=address;break;}
        check(chosen!=null,"No private emulator address");
        final java.net.Inet4Address ip=(java.net.Inet4Address)chosen.getAddress();final int prefix=chosen.getPrefixLength();
        final String key=network+":"+ip.getHostAddress()+":"+prefix;
        instrumentation.runOnMainSync(()->controller.show(ip,prefix,key));
        waitFor(()->field(controller,"server")!=null,15000,"Receiver did not start");
        com.mooncast.transfer.ApkTransferServer server=(com.mooncast.transfer.ApkTransferServer)field(controller,"server");
        com.mooncast.transfer.ApkTransferServer.Session session=(com.mooncast.transfer.ApkTransferServer.Session)field(server,"session");
        try(com.mooncast.transfer.ApkTransferClient client=new com.mooncast.transfer.ApkTransferClient(ip,session.port)){
            client.send(fixture,"fixture.apk",com.mooncast.transfer.ApkTransfer.hash(fixture),(received,total)->{});
        }
        waitFor(()->field(controller,"pending")!=null && ((android.widget.Button)field(controller,"install")).isEnabled(),60000,"Metadata review did not complete");
        check(field(controller,"server")==null,"Receiver left open after receipt");
        check(((android.widget.TextView)field(controller,"message")).getText().toString().contains(context.getPackageName()),"Review is missing verified package name");
        File pending=(File)field(controller,"pending");
        File evidence=new File(context.getExternalFilesDir(null),"apk-receive-evidence");evidence.mkdirs();
        android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        if(screenshot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(evidence,"metadata-review.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
        // Only the app's explanation dialog is exercised, and only when permission is
        // absent. Never open Android Settings, an installer or an approval screen.
        if(!context.getPackageManager().canRequestPackageInstalls()){
            instrumentation.runOnMainSync(()->{try{((android.widget.Button)field(controller,"install")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            Object prompt=field(controller,"permissionDialog");check(prompt!=null,"Missing install-source explanation");
            instrumentation.runOnMainSync(()->{try{java.lang.reflect.Method m=controller.getClass().getDeclaredMethod("installClicked");m.setAccessible(true);m.invoke(controller);}catch(Exception e){throw new RuntimeException(e);}});
            check(field(controller,"permissionDialog")==prompt,"Repeated click stacked explanations");
            instrumentation.runOnMainSync(()->((android.app.AlertDialog)prompt).cancel());
            check(field(controller,"permissionDialog")==null && pending.exists(),"Explanation cancel lost review state");
        }
        // Cancel the pending APK; no system install handoff has been made.
        instrumentation.runOnMainSync(controller::cancel);
        waitFor(()->!pending.exists(),10000,"Cancel retained temporary APK");
        instrumentation.runOnMainSync(()->controller.show(ip,prefix,key));
        waitFor(()->field(controller,"server")!=null,15000,"Second receive session failed");
        instrumentation.runOnMainSync(controller::onStop);
        check(field(controller,"server")==null && field(controller,"dialog")==null,"Background left receiver open");
        instrumentation.runOnMainSync(activity::finish);
    }
    private static Object field(Object target,String name)throws Exception {java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private interface Condition{boolean ready()throws Exception;}
    private static void waitFor(Condition condition,long timeout,String error)throws Exception {long until=android.os.SystemClock.elapsedRealtime()+timeout;while(!condition.ready()){if(android.os.SystemClock.elapsedRealtime()>until)throw new AssertionError(error);Thread.sleep(100);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private interface Check{void run()throws Exception;}
    private static void reject(Check check,String message)throws Exception {try{check.run();}catch(Exception expected){return;}throw new AssertionError(message);}
}
