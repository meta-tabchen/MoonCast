package com.mooncast.host;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only SAF source; no filesystem path or broad storage permission required. */
final class DocumentMedia implements OriginalMediaServer.Source {
    private final ContentResolver resolver;
    private final Uri uri;
    private final String name,mime;
    private final long length;
    DocumentMedia(Context context,Uri uri) throws IOException {
        this.uri=uri;resolver=context.getContentResolver();mime=resolver.getType(uri);
        if(mime==null || !(mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("image/")))throw new IOException("Select a video, audio or image document");
        String label="Media";long size=-1;
        try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null && c.moveToFirst()){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);if(n>=0 && !c.isNull(n))label=c.getString(n);if(s>=0 && !c.isNull(s))size=c.getLong(s);}
        }
        name=label;length=size<0?-1:size;
    }
    public String name(){return name;} public String mime(){return mime;} public long length(){return length;}
    public InputStream open() throws IOException {InputStream in=resolver.openInputStream(uri);if(in==null)throw new FileNotFoundException();return in;}
}
