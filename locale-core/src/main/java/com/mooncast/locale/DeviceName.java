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
    private static AtomicFile file(Context c) { return new AtomicFile(new File(c.getFilesDir(), "device-display-name.txt")); }
    public static String get(Context c) {
        try { byte[] bytes=file(c).readFully(); if(bytes.length<=252) { String value=normalize(new String(bytes,StandardCharsets.UTF_8)); if(!value.isEmpty())return value; } }
        catch (Exception ignored) { }
        String legacy=c.getSharedPreferences("profile-default",Context.MODE_PRIVATE).getString("name", "");
        return normalize(legacy).isEmpty()?normalize("MoonCast · "+Build.MODEL):normalize(legacy);
    }
    static String normalize(String value) {
        if(value==null)return "";
        StringBuilder clean=new StringBuilder();
        value.trim().codePoints().filter(c->!Character.isISOControl(c)).forEach(clean::appendCodePoint);
        String result=clean.toString();
        while(result.getBytes(StandardCharsets.UTF_8).length>60)result=result.substring(0,result.offsetByCodePoints(result.length(),-1));
        return result.trim();
    }
    public static void show(Activity activity, Runnable saved) {
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(get(activity));input.setSelectAllOnFocus(true);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.device_name_title)
            .setMessage(R.string.device_name_hint).setView(input).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=normalize(input.getText().toString());
            if(value.isEmpty()){input.setError(activity.getString(R.string.device_name_invalid));return;}
            AtomicFile target=file(activity);FileOutputStream out=null;
            try { out=target.startWrite();out.write(value.getBytes(StandardCharsets.UTF_8));target.finishWrite(out); }
            catch(Exception failure){if(out!=null)target.failWrite(out);input.setError(activity.getString(R.string.device_name_failed));return;}
            saved.run();dialog.dismiss();Toast.makeText(activity,R.string.device_name_saved,Toast.LENGTH_LONG).show();
        }));dialog.show();
    }
}
