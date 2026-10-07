package com.mooncast.host;

import android.content.Context;
import android.media.AudioManager;
import android.util.AtomicFile;
import java.io.*;

/** Only adjusts STREAM_MUSIC, leaving capture samples and other stream settings untouched. */
final class LocalAudioMute {
    private final MediaVolumeSession session;
    private final AudioManager manager;
    LocalAudioMute(Context context){
        manager=context.getSystemService(AudioManager.class);
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"local-media-volume"));
        session=new MediaVolumeSession(new MediaVolumeSession.Device(){
            public int volume(){return manager.getStreamVolume(AudioManager.STREAM_MUSIC);}
            public void volume(int value){manager.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);}
        },new MediaVolumeSession.Journal(){
            public Integer saved(){
                try(DataInputStream in=new DataInputStream(file.openRead())){
                    int value=in.readInt();
                    if(value<0 || value>manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))throw new IOException("音量恢复记录无效");
                    return value;
                }catch(FileNotFoundException e){return null;}
                catch(IOException e){throw new IllegalStateException("读取音量恢复记录失败",e);}
            }
            public void save(int volume){
                FileOutputStream stream=null;
                try{stream=file.startWrite();new DataOutputStream(stream).writeInt(volume);file.finishWrite(stream);}
                catch(IOException e){if(stream!=null)file.failWrite(stream);throw new IllegalStateException("保存音量失败，未静音手机",e);}
            }
            public void clear(){file.delete();}
        });
    }
    void silence(){if(manager.isVolumeFixed())throw new IllegalStateException("设备使用固定媒体音量");session.silence();}
    void restore(){session.restore();}
}
