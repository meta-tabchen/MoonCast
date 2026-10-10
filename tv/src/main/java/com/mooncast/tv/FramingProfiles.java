package com.mooncast.tv;

/** Persistence boundary kept Android-free so isolation and malformed settings are testable. */
public final class FramingProfiles {
    public interface Store {
        String read(String key);
        void write(String key,String value);
    }
    private final Store store;
    public FramingProfiles(Store store){this.store=store;}
    public static String key(String source,int width,int height){
        // Source classes only. Do not persist URLs, phone identities, or media names.
        String safe=switch(source==null?"":source){
            case "airplay_mirror","airplay_media","dlna_media"->source;
            default->"media";
        };
        return "framing.v2."+safe+"."+(height>width?"portrait":"landscape");
    }
    public FramingSettings load(String key){
        String value=store==null?null:store.read(key);
        if(value==null)return FramingSettings.DEFAULT;
        try{
            String[] v=value.split(",",-1);
            if(v.length!=8)return FramingSettings.DEFAULT;
            return new FramingSettings(Integer.parseInt(v[0]),Integer.parseInt(v[1]),
                    Integer.parseInt(v[2]),Integer.parseInt(v[3]),Integer.parseInt(v[4]),
                    Integer.parseInt(v[5]),Integer.parseInt(v[6]),Integer.parseInt(v[7]));
        }catch(NumberFormatException invalid){return FramingSettings.DEFAULT;}
    }
    public void save(String key,FramingSettings value){
        if(store!=null)store.write(key,value.mode()+","+value.left()+","+value.top()+","+
                value.right()+","+value.bottom()+","+value.zoom()+","+value.panX()+","+value.panY());
    }
}
