package com.mooncast.tv.apk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import com.mooncast.transfer.ApkTransfer;
import com.mooncast.transfer.ApkTransferServer;
import com.mooncast.tv.R;
import com.mooncast.tv.net.LanAccess;
import java.io.File;
import java.net.Inet4Address;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One explicit foreground-only receive session; every install requires a remote click. */
public final class ApkReceiveController {
    private static final int REQUEST_SOURCES=7431, REQUEST_INSTALL=7432;
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final NsdManager nsd;
    private final File root;
    private final String receiverId;
    private AlertDialog dialog, permissionDialog;
    private TextView message;
    private Button install;
    private ApkTransferServer server;
    private NsdManager.RegistrationListener registration;
    private File directory, pending;
    private String expectedHash;
    private Uri grantedUri;
    private int generation;
    private boolean external,closed,validating,installerActive;
    private long pendingUntil;
    private java.util.concurrent.Future<?> validationTask;
    private String receivingNetwork;
    private int reviewNotice;
    private final ApkSettingsReturn settingsReturn=new ApkSettingsReturn();
    private final Runnable expiry=this::expire;
    private void expire(){if(installerActive)stopReceiving();else {cancel();android.widget.Toast.makeText(activity,R.string.apk_expired,android.widget.Toast.LENGTH_LONG).show();}}

    public ApkReceiveController(Activity activity) {
        this.activity=activity;nsd=(NsdManager)activity.getSystemService(Activity.NSD_SERVICE);
        root=new File(activity.getCacheDir(),"apk-receive");
        android.content.SharedPreferences preferences=activity.getSharedPreferences("apk-receiver",Activity.MODE_PRIVATE);
        String id=preferences.getString("receiver-id",null);
        if(id==null){id=UUID.randomUUID().toString();preferences.edit().putString("receiver-id",id).apply();}
        receiverId=id;
        // Only this feature's private stale files, left by process death, are removed.
        worker.execute(this::cleanStale);
    }
    public void show(Inet4Address address,int prefix,String networkKey) {
        if(closed)return;
        if(dialog!=null && dialog.isShowing())return;
        cancel();reviewNotice=0;final int epoch=generation;
        showDialog();
        if(address==null || !address.isSiteLocalAddress()){message.setText(R.string.apk_network_required);return;}
        receivingNetwork=networkKey;
        File dir=new File(root,UUID.randomUUID().toString());directory=dir;
        worker.execute(()->{
            ApkTransferServer starting=null;
            try {
                if(!dir.mkdirs()&&!dir.isDirectory())throw new java.io.IOException("Private storage unavailable");
                LanAccess lan=new LanAccess(address,prefix);
                ApkTransferServer created=new ApkTransferServer(dir,address,peer->peer.isSiteLocalAddress() && lan.allows(peer),receiverId,new ApkTransferServer.Listener(){
                    public void onProgress(long received,long total){main.post(()->{if(epoch==generation && pending==null)message.setText(activity.getString(R.string.apk_receiving,received/1048576,total/1048576));});}
                    public void onReceived(File file,String sha256){main.post(()->{
                        if(epoch!=generation){cleanup(dir);return;}
                        stopReceiving();pending=file;expectedHash=sha256;pendingUntil=android.os.SystemClock.elapsedRealtime()+10*60_000L;
                        main.removeCallbacks(expiry);main.postDelayed(expiry,10*60_000L);validate(false);
                    });}
                    public void onError(String error){main.post(()->{if(epoch==generation && pending==null){stopReceiving();message.setText(activity.getString(R.string.apk_receive_failed,error));}});}
                });
                starting=created;
                ApkTransferServer.Session session=created.start();
                main.post(()->{
                    if(epoch!=generation){created.close();cleanup(dir);return;}
                    server=created;
                    message.setText(activity.getString(R.string.tv_apk_ready,address.getHostAddress(),session.port));
                    register(epoch,session.port);
                    main.postDelayed(expiry,Math.max(1,session.expiresAtMillis-System.currentTimeMillis()));
                });
            }catch(Exception e){if(starting!=null)starting.close();cleanup(dir);main.post(()->{if(epoch==generation)message.setText(activity.getString(R.string.apk_receive_failed,e.getMessage()));});}
        });
    }
    private void showDialog() {
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
        message=new TextView(activity);message.setTextSize(18);message.setText(R.string.apk_starting);body.addView(message);
        install=new Button(activity);install.setAllCaps(false);install.setTextSize(19);install.setPadding(dp(28),dp(18),dp(28),dp(18));
        android.widget.LinearLayout.LayoutParams actionLayout=new android.widget.LinearLayout.LayoutParams(-1,-2);actionLayout.topMargin=dp(20);install.setLayoutParams(actionLayout);
        styleInstallButton();install.setText(R.string.apk_waiting_file);setInstallEnabled(false);install.setOnClickListener(v->installClicked());body.addView(install);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle(R.string.apk_receive_title).setView(scroll)
                .setNegativeButton(R.string.apk_cancel,(d,w)->cancel()).create();
        dialog.setOnCancelListener(d->cancel());dialog.show();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private void styleInstallButton() {
        android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled},buttonShape(0xff26364b,0xff405169,1));
        background.addState(new int[]{android.R.attr.state_focused},buttonShape(0xffa8ddff,0xffffffff,3));
        background.addState(new int[]{android.R.attr.state_pressed},buttonShape(0xff8dcfff,0xffffffff,2));
        background.addState(new int[]{},buttonShape(0xff4caeff,0xff88caff,1));
        install.setBackground(background);
        install.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffaab8cc,0xff07192b}));
        install.setOnFocusChangeListener((v,focused)->{v.setScaleX(focused?1.025f:1f);v.setScaleY(focused?1.025f:1f);});
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private android.graphics.drawable.GradientDrawable buttonShape(int color,int stroke,int width) {
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(14*activity.getResources().getDisplayMetrics().density);
        shape.setStroke(dp(width),stroke);return shape;
    }
    private void setInstallEnabled(boolean enabled) {
        if(install==null)return;
        install.setEnabled(enabled);
        if(enabled)install.setText(activity.getPackageManager().canRequestPackageInstalls()?R.string.apk_install:R.string.apk_allow_install);
        else install.setText(installerActive?R.string.apk_opening_installer:settingsReturn.awaiting()?R.string.apk_waiting_settings:validating?R.string.apk_checking_file:R.string.apk_waiting_file);
    }
    public void onPause(){settingsReturn.onPause();}
    /** Always recheck after actual foreground return, even when an OEM omits its result. */
    public void onResume(){
        if(!settingsReturn.onResume())return;
        external=false;
        if(dialog==null||pending==null)return;
        if(android.os.SystemClock.elapsedRealtime()>pendingUntil){cancel();return;}
        reviewNotice=activity.getPackageManager().canRequestPackageInstalls()?R.string.apk_permission_allowed:R.string.apk_permission_denied;
        validate(false);
    }
    public void saveState(android.os.Bundle state){
        if(!settingsReturn.awaiting()||pending==null||directory==null)return;
        android.os.Bundle review=new android.os.Bundle();
        review.putString("directory",directory.getName());review.putString("file",pending.getName());review.putString("hash",expectedHash);
        review.putLong("until",System.currentTimeMillis()+Math.max(0,pendingUntil-android.os.SystemClock.elapsedRealtime()));
        state.putBundle("apk-source-review",review);
    }
    public void restoreState(android.os.Bundle state){
        android.os.Bundle review=state==null?null:state.getBundle("apk-source-review");if(review==null)return;
        String dir=review.getString("directory",""),name=review.getString("file",""),hash=review.getString("hash","");
        long remaining=review.getLong("until")-System.currentTimeMillis();
        if(!dir.matches("[a-f0-9-]{36}")||!name.matches("mooncast-apk-[a-zA-Z0-9-]+\\.apk")||!hash.matches("[a-f0-9]{64}")||remaining<=0||remaining>10*60_000L){showExpiredReview();return;}
        File restoredDir=new File(root,dir),file=new File(restoredDir,name);
        try{ApkMetadataValidator.requirePrivateFile(restoredDir,file);}catch(java.io.IOException invalid){showExpiredReview();return;}
        directory=restoredDir;pending=file;expectedHash=hash;pendingUntil=android.os.SystemClock.elapsedRealtime()+remaining;
        settingsReturn.begin();settingsReturn.onPause();external=true;showDialog();
        message.setText(R.string.apk_returning_settings);main.postDelayed(expiry,remaining);
    }
    private void showExpiredReview(){showDialog();message.setText(R.string.apk_restore_expired);}
    private void register(int epoch,int port) {
        NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("MoonCast TV APK");info.setServiceType(ApkTransfer.SERVICE_TYPE);info.setPort(port);
        info.setAttribute("v","2");info.setAttribute("apk","1");
        info.setAttribute("pairing","none");info.setAttribute("id",receiverId);
        NsdManager.RegistrationListener listener=new NsdManager.RegistrationListener(){
            public void onServiceRegistered(NsdServiceInfo value){main.post(()->{if(epoch!=generation || registration!=this)unregister(this);});}
            public void onRegistrationFailed(NsdServiceInfo value,int error){main.post(()->{if(epoch==generation && pending==null)message.append("\n"+activity.getString(R.string.apk_discovery_failed));});}
            public void onServiceUnregistered(NsdServiceInfo value){}
            public void onUnregistrationFailed(NsdServiceInfo value,int error){}
        };
        registration=listener;
        try{nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,listener);}catch(RuntimeException e){message.append("\n"+activity.getString(R.string.apk_discovery_failed));}
    }
    private void unregister(NsdManager.RegistrationListener listener){try{nsd.unregisterService(listener);}catch(RuntimeException ignored){}}
    private void stopReceiving() {
        receivingNetwork=null;
        if(registration!=null){unregister(registration);registration=null;}
        if(server!=null){server.close();server=null;}
    }
    private void validate(boolean launchAfter) {
        if(pending==null || validating)return;
        if(android.os.SystemClock.elapsedRealtime()>pendingUntil){cancel();return;}
        final int epoch=generation;final File file=pending,dir=directory;final String hash=expectedHash;
        validating=true;setInstallEnabled(false);message.setText(R.string.apk_validating);
        validationTask=worker.submit(()->{
            try {
                ApkMetadataValidator.Metadata metadata=ApkMetadataValidator.inspect(activity.getPackageManager(),dir,file,hash);
                if(!file.setReadOnly())throw new java.io.IOException("Cannot protect received APK");
                main.post(()->{if(epoch!=generation)return;validating=false;validationTask=null;
                    message.setText(activity.getString(R.string.tv_apk_metadata,metadata.label,metadata.packageName,metadata.version,metadata.bytes/1048576.0,metadata.certificateSha256,metadata.sha256));
                    if(reviewNotice!=0)message.append("\n\n"+activity.getString(reviewNotice));
                    // Validation is queued after initial stale cleanup on the same worker.
                    // Keep the recovery lease until here so restored files cannot race cleanup.
                    new File(dir,".review-lease").delete();
                    setInstallEnabled(true);install.requestFocus();
                    if(launchAfter)launchInstaller();
                });
            }catch(Exception e){main.post(()->{if(epoch==generation){validating=false;validationTask=null;setInstallEnabled(false);message.setText(activity.getString(R.string.apk_invalid,e.getMessage()));discardFile();}});}
        });
    }
    private void installClicked() {
        if(pending==null||validating||installerActive||permissionDialog!=null)return;
        stopReceiving();
        final int consentEpoch=generation;
        if(!activity.getPackageManager().canRequestPackageInstalls()) {
            setInstallEnabled(false);
            permissionDialog=new AlertDialog.Builder(activity).setTitle(R.string.apk_permission_title).setMessage(R.string.apk_permission_detail)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.apk_open_settings,(d,w)->{
                        if(pending==null || consentEpoch!=generation)return;
                        external=true;settingsReturn.begin();setInstallEnabled(false);message.setText(R.string.apk_returning_settings);
                        try{
                            try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.FileOutputStream(new File(directory,".review-lease")))){out.writeLong(System.currentTimeMillis()+Math.max(0,pendingUntil-android.os.SystemClock.elapsedRealtime()));}
                            activity.startActivityForResult(systemOnly(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()))),REQUEST_SOURCES);}
                        catch(Exception e){external=false;settingsReturn.reset();new File(directory,".review-lease").delete();setInstallEnabled(true);message.setText(R.string.apk_settings_unavailable);}
                    }).create();
            permissionDialog.setOnDismissListener(d->{permissionDialog=null;if(install!=null && pending!=null && !validating && !installerActive && !settingsReturn.awaiting())setInstallEnabled(true);});
            permissionDialog.show();
            return;
        }
        validate(true);
    }
    @SuppressWarnings("deprecation")
    private void launchInstaller() {
        try {
            ApkMetadataValidator.requirePrivateFile(directory,pending);
            grantedUri=FileProvider.getUriForFile(activity,activity.getPackageName()+".apkfiles",pending);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(grantedUri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
            intent.setClipData(ClipData.newRawUri("APK",grantedUri));
            intent=systemOnly(intent);
            // A receiving Activity can be recreated while Android still reads this URI.
            // Keep only this file's private directory under a narrow recovery lease.
            File lease=new File(directory,".installer-lease");
            try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.FileOutputStream(lease))){out.writeLong(System.currentTimeMillis()+24*60*60_000L);}
            external=true;installerActive=true;setInstallEnabled(false);
            activity.startActivityForResult(intent,REQUEST_INSTALL);
        }catch(Exception e){external=false;installerActive=false;if(directory!=null)new File(directory,".installer-lease").delete();revoke();setInstallEnabled(true);message.setText(R.string.apk_installer_unavailable);}
    }
    private Intent systemOnly(Intent intent) {
        android.content.pm.PackageManager pm=activity.getPackageManager();
        java.util.List<android.content.pm.ResolveInfo> matches=pm.queryIntentActivities(intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY|android.content.pm.PackageManager.MATCH_SYSTEM_ONLY);
        android.content.pm.ActivityInfo chosen=null;
        for(android.content.pm.ResolveInfo match:matches){
            android.content.pm.ActivityInfo info=match.activityInfo;
            int flags=info==null||info.applicationInfo==null?0:info.applicationInfo.flags;
            if(info!=null && info.exported && info.enabled && (flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM|android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0){
                if(chosen!=null)throw new android.content.ActivityNotFoundException("Ambiguous system handler");
                chosen=info;
            }
        }
        if(chosen==null)throw new android.content.ActivityNotFoundException("No system handler");
        return intent.setComponent(new android.content.ComponentName(chosen.packageName,chosen.name));
    }
    public boolean onActivityResult(int request,int result) {
        if(request!=REQUEST_INSTALL&&request!=REQUEST_SOURCES)return false;
        // Settings results are advisory: OEMs can report them before Settings pauses
        // us, or never report them. The next real onResume owns revalidation.
        if(request==REQUEST_SOURCES)return true;
        external=false;installerActive=false;
        if(dialog==null||pending==null)return true;
        if(request==REQUEST_INSTALL){revoke();discardFile();setInstallEnabled(false);message.setText(result==Activity.RESULT_OK?R.string.apk_install_return_ok:R.string.apk_install_return_cancel);}
        return true;
    }
    /** Source settings returns to a review screen; never installs automatically. */
    public void onStop(){stopReceiving();if(!external)cancel();}
    public void onNetworkChanged(String currentKey){
        if(receivingNetwork!=null && !receivingNetwork.equals(currentKey)){
            cancel();android.widget.Toast.makeText(activity,R.string.apk_network_changed,android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void revoke(){if(grantedUri!=null){activity.revokeUriPermission(grantedUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);grantedUri=null;}}
    private void discardFile(){File old=directory;pending=null;expectedHash=null;directory=null;if(old!=null)cleanup(old);}
    public void cancel(){settingsReturn.reset();if(validationTask!=null){validationTask.cancel(true);validationTask=null;}if(permissionDialog!=null){permissionDialog.dismiss();permissionDialog=null;}++generation;main.removeCallbacks(expiry);stopReceiving();revoke();external=false;installerActive=false;validating=false;discardFile();if(dialog!=null){dialog.dismiss();dialog=null;}}
    public void close(){
        if(closed)return;
        if(installerActive || settingsReturn.awaiting() && pending!=null){
            ++generation;main.removeCallbacks(expiry);stopReceiving();
            if(permissionDialog!=null){permissionDialog.dismiss();permissionDialog=null;}
            if(dialog!=null){dialog.dismiss();dialog=null;}
            // Do not revoke the recipient's grant or delete bytes while installer owns them.
            pending=null;directory=null;grantedUri=null;
        }else cancel();
        closed=true;worker.shutdown();
    }
    private void cleanStale(){
        File[] directories=root.listFiles();if(directories==null)return;
        for(File dir:directories){
            File lease=new File(dir,".installer-lease");boolean retained=false;
            File reviewLease=new File(dir,".review-lease");
            try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.FileInputStream(reviewLease))){
                long expiry=in.readLong(),now=System.currentTimeMillis();retained=expiry>now && expiry<=now+10*60_000L;
            }catch(java.io.IOException ignored){}
            try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.FileInputStream(lease))){
                long expiry=in.readLong(),now=System.currentTimeMillis();
                retained=retained || expiry>now && expiry<=now+24*60*60_000L;
            }catch(java.io.IOException ignored){}
            if(!retained)deleteTree(dir);
        }
    }
    private void cleanup(File file){if(worker.isShutdown())deleteTree(file);else worker.execute(()->deleteTree(file));}
    private static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);if(file.exists())file.delete();}
}
