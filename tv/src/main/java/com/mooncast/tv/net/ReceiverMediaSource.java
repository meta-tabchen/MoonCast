package com.mooncast.tv.net;

import android.net.Uri;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/** Use directly in DefaultMediaSourceFactory. Do not wrap in DefaultDataSource, which enables file/content
 * access and would bypass the per-resource HTTP-only policy for nested playlists. */
@androidx.media3.common.util.UnstableApi
public final class ReceiverMediaSource extends BaseDataSource {
    private final MediaHttpConnection transport;
    private boolean opened;
    private ReceiverMediaSource(IntSupplier allowedLoopbackPort){super(true);transport=new MediaHttpConnection(allowedLoopbackPort);}
    public static DataSource.Factory factory(IntSupplier allowedLoopbackPort){return ()->new ReceiverMediaSource(allowedLoopbackPort);}
    @Override public long open(DataSpec spec) throws IOException {
        close();transferInitializing(spec);
        if(spec.httpMethod!=DataSpec.HTTP_METHOD_GET && spec.httpMethod!=DataSpec.HTTP_METHOD_HEAD)throw new IOException("Only HTTP GET/HEAD media requests are supported");
        if(spec.httpBody!=null && spec.httpBody.length>0)throw new IOException("Media request bodies are not supported");
        try {
            long length=transport.open(spec.uri.toString(),spec.position,spec.length,spec.httpRequestHeaders,spec.httpMethod==DataSpec.HTTP_METHOD_HEAD);
            opened=true;transferStarted(spec);return length;
        }catch(MediaHttpConnection.PositionException e){throw new DataSourceException(e,PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);}
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        int read=transport.read(buffer,offset,length);if(read>0)bytesTransferred(read);return read;
    }
    @Override public Uri getUri(){return transport.uri()==null?null:Uri.parse(transport.uri().toString());}
    @Override public Map<String,List<String>> getResponseHeaders(){return transport.headers();}
    @Override public void close(){transport.close();if(opened){opened=false;transferEnded();}}
}
