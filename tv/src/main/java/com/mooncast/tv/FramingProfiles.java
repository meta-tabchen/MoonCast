package com.mooncast.tv;

/** One app-wide user choice, separate from transient per-video detected crop coordinates. */
public final class FramingProfiles {
    public static final String GLOBAL_KEY="framing.v3.global";
    public interface Store {
        String read(String key);
        void write(String key,String value);
    }
    private final Store store;
    private FramingSettings memory=FramingSettings.DEFAULT;
    public FramingProfiles(Store store){this.store=store;}
    public static String key(String source,int width,int height){
        // Retained for editor/session compatibility. Never include identities or dimensions.
        return GLOBAL_KEY;
    }
    public FramingSettings load(){
        if(store==null)return memory;
        String value=store.read(GLOBAL_KEY);
        // v0.2 source/orientation records contain no last-choice timestamp. Picking one
        // would be arbitrary, so an upgrade with no global choice starts in Auto too.
        // Old records are ignored permanently; detected bounds are never serialized.
        if(value==null)return FramingSettings.DEFAULT;
        try{
            String[] v=value.split(",",-1);
            if(v.length!=8)return FramingSettings.DEFAULT;
            int mode=Integer.parseInt(v[0]);
            if(mode<FramingSettings.FIT||mode>FramingSettings.MANUAL)return FramingSettings.DEFAULT;
            return new FramingSettings(mode,Integer.parseInt(v[1]),
                    Integer.parseInt(v[2]),Integer.parseInt(v[3]),Integer.parseInt(v[4]),
                    Integer.parseInt(v[5]),Integer.parseInt(v[6]),Integer.parseInt(v[7]));
        }catch(NumberFormatException invalid){return FramingSettings.DEFAULT;}
    }
    public void save(FramingSettings value){
        memory=value;
        if(store!=null)store.write(GLOBAL_KEY,value.mode()+","+value.left()+","+value.top()+","+
                value.right()+","+value.bottom()+","+value.zoom()+","+value.panX()+","+value.panY());
    }
}
