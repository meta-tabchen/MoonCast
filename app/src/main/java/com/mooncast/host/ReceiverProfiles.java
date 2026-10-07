package com.mooncast.host;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

/** Local preference profiles, explicitly selected by the user; never paired-client credentials. */
final class ReceiverProfiles {
    private static final List<String> IDS=List.of("default","ipad","tv","computer");
    private final Context context;
    private final SharedPreferences index;
    ReceiverProfiles(Context context,SharedPreferences legacy){
        this.context=context;index=context.getSharedPreferences("receiver-profiles",0);
        if(!index.getBoolean("migrated",false)){
            SharedPreferences.Editor editor=settings(0).edit();
            for(var entry:legacy.getAll().entrySet()){
                Object value=entry.getValue();String key=entry.getKey();
                if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof String)editor.putString(key,(String)value);
            }
            // Migration may run before the UI knows about the fixed-video default.
            if(!legacy.contains("scaleMode"))editor.putInt("scaleMode",CropGeometry.VIDEO_REGION);
            editor.putBoolean("videoRegionIntroduced",true).commit();index.edit().putBoolean("migrated",true).commit();
        }
    }
    int selected(){return Math.max(0,Math.min(IDS.size()-1,index.getInt("selected",0)));}
    void select(int position){if(position<0 || position>=IDS.size())throw new IllegalArgumentException("profile");index.edit().putInt("selected",position).apply();}
    SharedPreferences settings(){return settings(selected());}
    SharedPreferences settings(int position){return context.getSharedPreferences("profile-"+IDS.get(position),0);}
}
