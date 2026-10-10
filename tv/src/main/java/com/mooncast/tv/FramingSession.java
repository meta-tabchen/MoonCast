package com.mooncast.tv;

/** Pure transaction/state model shared by the TV renderer and portable lifecycle tests. */
class FramingSession {
    static final int FIT=FramingSettings.FIT,FILL=FramingSettings.FILL,
            CINEMA=FramingSettings.CINEMA,MANUAL=FramingSettings.MANUAL;
    record Snapshot(String profileKey,long profileEpoch,int sourceWidth,int sourceHeight,
                    FramingSettings settings,CinemaCropTracker.State cinema) {}
    protected final CinemaCropTracker tracker=new CinemaCropTracker();
    private final FramingProfiles profiles;
    protected FramingSettings settings=FramingSettings.DEFAULT;
    private String source,profileKey;
    protected int width=1920,height=1080;
    protected boolean editing;
    private long profileEpoch;

    FramingSession(FramingProfiles profiles,String source){this.profiles=profiles;this.source=source;selectProfile();}
    void sourceProfile(String value){
        String key=FramingProfiles.key(value,width,height);source=value;
        if(!key.equals(profileKey)){selectProfile();apply();}
    }
    private void selectProfile(){
        profileKey=FramingProfiles.key(source,width,height);profileEpoch++;
        settings=profiles.load(profileKey);editing=false;tracker.reset();
    }
    void videoSize(int w,int h){
        if(w<=0||h<=0||(w==width&&h==height))return;
        width=w;height=h;
        if(!FramingProfiles.key(source,w,h).equals(profileKey))selectProfile();
        else tracker.reset();
        apply();
    }
    FramingSettings settings(){return settings;}
    Snapshot snapshot(){editing=true;return new Snapshot(profileKey,profileEpoch,width,height,settings,tracker.snapshot());}
    void preview(FramingSettings value){
        if(value==null)return;
        if(settings.mode()!=value.mode())tracker.reset();
        settings=value;apply();
    }
    void applyEdits(){profiles.save(profileKey,settings);editing=false;apply();}
    void cancel(Snapshot before){
        editing=false;
        // Epoch also prevents an obsolete edit surviving landscape -> portrait -> landscape.
        if(before!=null&&profileEpoch==before.profileEpoch()&&profileKey.equals(before.profileKey())){
            settings=before.settings();
            if(width==before.sourceWidth()&&height==before.sourceHeight())tracker.restore(before.cinema());
            else tracker.reset();
        }
        apply();
    }
    void resetProfile(){settings=FramingSettings.DEFAULT;tracker.reset();apply();}
    void mode(int value){
        FramingSettings preset=settings.withMode(value);
        if(value!=MANUAL)preset=preset.withZoom(100).withPan(0,0);
        preview(preset);applyEdits();
    }
    int mode(){return settings.mode();}
    /** Compatibility helper; the v0.2 editor uses four independent withEdge() values. */
    void manual(int x,int y){preview(new FramingSettings(MANUAL,x,y,x,y,settings.zoom(),settings.panX(),settings.panY()));}
    int horizontal(){return settings.left();}
    int vertical(){return settings.top();}
    /** Rescan only; it does not discard saved manual framing. */
    void reset(){tracker.reset();apply();}
    boolean locked(){return tracker.locked();}
    boolean safetyExpanded(){return tracker.safetyExpanded();}
    int stableSamples(){return tracker.stableSamples();}
    int requiredSamples(){return tracker.requiredSamples();}
    int sourceWidth(){return width;}
    int sourceHeight(){return height;}
    String profileKey(){return profileKey;}
    long profileEpoch(){return profileEpoch;}
    boolean portrait(){return height>width;}
    CropGeometry.Bounds effectiveBounds(){return switch(settings.mode()){
        case CINEMA->tracker.bounds();case MANUAL->settings.manualBounds();default->CropGeometry.Bounds.FULL;
    };}
    boolean sourceCropped(){
        CropGeometry.Bounds b=effectiveBounds();
        return b.left()>0||b.bottom()>0||b.right()<1||b.top()<1;
    }
    /** Renderer callback. No Android types or side effects in the pure model. */
    void apply(){}
}
