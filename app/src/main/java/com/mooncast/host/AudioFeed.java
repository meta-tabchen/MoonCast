package com.mooncast.host;

import android.media.AudioRecord;
import java.util.Arrays;

/** Native Sunshine reads 48 kHz stereo float PCM through this ABI. Never records microphone audio. */
public final class AudioFeed {
    private AudioRecord recorder;
    private final Runnable onFailure;
    private boolean failureReported;
    public AudioFeed(){this(()->{});}
    AudioFeed(Runnable onFailure){this.onFailure=onFailure;}
    synchronized void set(AudioRecord value) { recorder = value;failureReported=false; }
    public int read(float[] buffer, int offset, int count, int mode) {
        AudioRecord current;
        synchronized (this) { current = recorder; }
        if (current != null) {
            int n;
            try{n=current.read(buffer, offset, count, AudioRecord.READ_BLOCKING);}
            catch(IllegalStateException e){n=AudioRecord.ERROR_INVALID_OPERATION;}
            if (n > 0) return n;
            if(n<0){
                boolean notify;
                synchronized(this){notify=recorder==current && !failureReported;if(notify)failureReported=true;}
                if(notify)onFailure.run();
            }
        }
        Arrays.fill(buffer, offset, offset+count, 0f);
        try { Thread.sleep(Math.max(1, count*1000L/96000)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return count;
    }
}
