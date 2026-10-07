package com.mooncast.host;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

/** Isolated storage namespace: never overwrites real user's receiver settings. */
public final class PreferencesInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context target=getTargetContext();
            Context isolated=new android.content.ContextWrapper(target){
                @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("test-"+name,mode);}
            };
            var legacy=isolated.getSharedPreferences("legacy",0);legacy.edit().clear().putInt("scaleMode",1).putBoolean("audio",true).commit();
            isolated.getSharedPreferences("receiver-profiles",0).edit().clear().commit();
            ReceiverProfiles profiles=new ReceiverProfiles(isolated,legacy);
            if(profiles.settings().getInt("scaleMode",0)!=1 || !profiles.settings().getBoolean("audio",false))throw new AssertionError("migration");
            profiles.select(1);profiles.settings().edit().putInt("scaleMode",4).putBoolean("audio",false).commit();
            profiles.select(2);profiles.settings().edit().putInt("scaleMode",0).putBoolean("audio",true).commit();
            profiles=new ReceiverProfiles(isolated,legacy);
            if(profiles.selected()!=2 || profiles.settings().getInt("scaleMode",-1)!=0)throw new AssertionError("restart persistence");
            profiles.select(1);if(profiles.settings().getInt("scaleMode",-1)!=4 || profiles.settings().getBoolean("audio",true))throw new AssertionError("profile isolation");
            profiles.select(0);if(profiles.settings().getInt("scaleMode",-1)!=1)throw new AssertionError("original defaults preserved");
            result.putString("result","PASS: legacy migration, independent receiver settings, selected-profile and restart persistence");finish(-1,result);
        }catch(Throwable e){result.putString("error",e.toString());finish(0,result);}
    }
}
