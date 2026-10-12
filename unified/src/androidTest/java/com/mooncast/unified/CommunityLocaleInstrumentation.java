package com.mooncast.unified;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Separate default-process runner. Only the androidTest APK contains these test entry points. */
public final class CommunityLocaleInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments=arguments==null?new Bundle():arguments; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(!"locale".equals(arguments.getString("suite")))throw new IllegalArgumentException("Use suite=locale");
            String phase=arguments.getString("phase","resources");
            // These phases deliberately reference no app R IDs or obfuscated business classes.
            // They can instrument the matching-signed release APK before and after install -r.
            if(phase.startsWith("upgrade-")) {
                result.putString("stream",upgrade(phase).toString()+"\n");
            } else {
                if((getTargetContext().getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)
                    throw new IllegalStateException("Locale/UI phases require the real debug APK");
                new CommunityLocaleTest(this,arguments).run();
                result.putString("stream","COMMUNITY_LOCALE_PASSED "+phase+"\n");
            }
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("stream","COMMUNITY_LOCALE_FAILED\n"+android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    @SuppressWarnings("deprecation") private JSONObject upgrade(String phase)throws Exception {
        Context context=getTargetContext();
        if(!"com.mooncast.community".equals(context.getPackageName()))throw new AssertionError("Wrong target package");
        String marker=arguments.getString("marker","");
        if(!marker.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Expected a generated test marker");
        File file=new File(context.getFilesDir(),"community-upgrade-validation-marker");
        if("upgrade-seed".equals(phase))Files.write(file.toPath(),marker.getBytes(StandardCharsets.US_ASCII));
        else if(!"upgrade-verify".equals(phase))throw new IllegalArgumentException("Unknown upgrade phase");
        if(!file.isFile()||!marker.equals(new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII)))
            throw new AssertionError("Private app data did not survive the update");
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        long version=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        if(version!=Long.parseLong(arguments.getString("expected_version")))throw new AssertionError("Unexpected installed version "+version);
        if(info.signatures==null||info.signatures.length!=1)throw new AssertionError("Expected one community signing certificate");
        StringBuilder digest=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray()))digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return new JSONObject().put("result","COMMUNITY_UPGRADE_PASSED").put("phase",phase)
            .put("package",context.getPackageName()).put("versionCode",version).put("firstInstallTime",info.firstInstallTime)
            .put("lastUpdateTime",info.lastUpdateTime).put("certificateSha256",digest.toString()).put("marker",marker);
    }
}
