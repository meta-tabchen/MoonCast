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
    private FramingSettings committed;
    protected int width=1920,height=1080;
    protected boolean editing;
    // An edit belongs to one video/size and one transaction, despite the global storage key.
    private long profileEpoch=1;

    FramingSession(FramingProfiles profiles,String source){this.profiles=profiles;settings=committed=profiles.load();}
    /** Begin a new video, including another video from the same protocol/source class. */
    void sourceProfile(String value){
        newVideoGeometry();apply();
    }
    private void newVideoGeometry(){
        settings=committed=profiles.load();profileEpoch++;editing=false;tracker.reset();
    }
    /** Called when either renderer commits the one shared application preference. */
    void refreshGlobalSettings(){
        FramingSettings latest=profiles.load();
        if(latest.equals(committed))return;
        settings=committed=latest;profileEpoch++;editing=false;tracker.reset();apply();
    }
    void videoSize(int w,int h){
        if(w<=0||h<=0||(w==width&&h==height))return;
        width=w;height=h;
        newVideoGeometry();
        apply();
    }
    FramingSettings settings(){return settings;}
    Snapshot snapshot(){refreshGlobalSettings();profileEpoch++;editing=true;return new Snapshot(profileKey(),profileEpoch,width,height,settings,tracker.snapshot());}
    void preview(FramingSettings value){
        if(value==null)return;
        if(settings.mode()!=value.mode())tracker.reset();
        settings=value;apply();
    }
    void applyEdits(){committed=settings;editing=false;profileEpoch++;profiles.save(settings);apply();}
    void cancel(Snapshot before){
        // A source/size change already discarded the preview. An obsolete Cancel must
        // neither restore an old detector lock nor overwrite a newer global choice/edit.
        if(before!=null&&editing&&profileEpoch==before.profileEpoch()&&profileKey().equals(before.profileKey())){
            editing=false;profileEpoch++;
            settings=before.settings();
            if(width==before.sourceWidth()&&height==before.sourceHeight())tracker.restore(before.cinema());
            else tracker.reset();
        }
        apply();
    }
    /** Explicit full-image choice, distinct from the first-launch Auto default. */
    void resetProfile(){settings=FramingSettings.FULL_IMAGE;tracker.reset();apply();}
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
    /** Rescan only; it does not discard the committed global user framing. */
    void reset(){newVideoGeometry();apply();}
    boolean locked(){return tracker.locked();}
    boolean safetyExpanded(){return tracker.safetyExpanded();}
    int stableSamples(){return tracker.stableSamples();}
    int requiredSamples(){return tracker.requiredSamples();}
    int sourceWidth(){return width;}
    int sourceHeight(){return height;}
    String profileKey(){return FramingProfiles.GLOBAL_KEY;}
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
