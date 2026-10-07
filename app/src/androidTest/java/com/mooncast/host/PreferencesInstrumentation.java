package com.mooncast.host;

import android.content.Context;

/** Isolated preference namespace. Called by the single registered instrumentation runner. */
public final class PreferencesInstrumentation {
    static void verify(Context target){
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
    }
}
