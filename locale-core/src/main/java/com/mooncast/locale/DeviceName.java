package com.mooncast.locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import android.widget.EditText;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** One display name across roles and protocols. Never writes UUIDs or pairing state.
 * Atomic file reads avoid stale per-process SharedPreferences caches. */
public final class DeviceName {
    private DeviceName() {}
    static AtomicFile file(Context c) { return new AtomicFile(new File(c.getFilesDir(), "device-display-name.txt")); }
    interface IoOperation<T>{T run() throws java.io.IOException;}
    // AtomicFile alone is not a cross-process lock: on Android29 a concurrent
    // openRead can roll back a still-active startWrite. Always lock both paths.
    static synchronized <T> T withStorageLock(Context c,IoOperation<T> operation)throws java.io.IOException {
        try(java.io.RandomAccessFile lockFile=new java.io.RandomAccessFile(new File(c.getFilesDir(),"device-display-name.lock"),"rw");
            java.nio.channels.FileChannel channel=lockFile.getChannel();java.nio.channels.FileLock lock=channel.lock()){
            return operation.run();
        }
    }
    public static String get(Context c) {
        try { String value=withStorageLock(c,()->{byte[] bytes=file(c).readFully();return bytes.length<=252?normalize(new String(bytes,StandardCharsets.UTF_8)):"";});if(DeviceNamePolicy.valid(value))return value; }
        catch (Exception ignored) { }
        String legacy=c.getSharedPreferences("profile-default",Context.MODE_PRIVATE).getString("name", "");
        return DeviceNamePolicy.valid(legacy)?normalize(legacy):normalize("MoonCast · "+Build.MODEL);
    }
    static String normalize(String value){return DeviceNamePolicy.normalize(value);}
    public static void save(Context context,String requested)throws java.io.IOException {
        String value=normalize(requested);if(!DeviceNamePolicy.valid(value))throw new IllegalArgumentException("invalid device name");
        withStorageLock(context,()->{AtomicFile target=file(context);FileOutputStream out=null;
            try{out=target.startWrite();out.write(value.getBytes(StandardCharsets.UTF_8));target.finishWrite(out);out=null;if(!java.util.Arrays.equals(target.readFully(),value.getBytes(StandardCharsets.UTF_8)))throw new java.io.IOException("device name readback mismatch");return null;}
            catch(java.io.IOException|RuntimeException failure){if(out!=null)target.failWrite(out);throw failure;}
        });
    }
    public static void show(Activity activity, Runnable saved) {
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(get(activity));input.setSelectAllOnFocus(true);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.device_name_title)
            .setMessage(R.string.device_name_hint).setView(input).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=normalize(input.getText().toString());
            if(!DeviceNamePolicy.valid(value)){input.setError(activity.getString(R.string.device_name_invalid));return;}
            try { save(activity,value); }
            catch(Exception failure){input.setError(activity.getString(R.string.device_name_failed));return;}
            saved.run();dialog.dismiss();Toast.makeText(activity,R.string.device_name_saved,Toast.LENGTH_LONG).show();
        }));dialog.show();
    }
}
