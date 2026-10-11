package com.mooncast.tv;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Generated content only; pure source geometry/model evidence, not a device rendering test. */
public final class TvFramingTest {
    public static void main(String[] args){
        edgesAndTransforms();globalPreferences();migrationAndMalformedPreferences();transactionsAndVideoBoundaries();wideBarsAndSafety();
        System.out.println("PASS: TV independent 4-edge crop, 1% zoom/pan, uniform transforms, global Auto default/persistence, source/rotation isolation, transactional Apply/Cancel/Reset and conservative wide bars");
    }
    private static void edgesAndTransforms(){
        FramingSettings s=FramingSettings.DEFAULT.withEdge(FramingSettings.LEFT,10)
                .withEdge(FramingSettings.TOP,20).withEdge(FramingSettings.RIGHT,30)
                .withEdge(FramingSettings.BOTTOM,5);
        check(s.mode()==FramingSettings.MANUAL,"edge edit enters manual");
        check(s.left()==10&&s.top()==20&&s.right()==30&&s.bottom()==5,"four independent values");
        close(s.manualBounds().left(),.10f,"left");close(s.manualBounds().top(),.80f,"top-down top");
        close(s.manualBounds().bottom(),.05f,"bottom-up conversion");
        FramingSettings extremes=FramingSettings.DEFAULT.withEdge(0,45).withEdge(2,45)
                .withEdge(1,45).withEdge(3,45);
        check(extremes.left()==45&&extremes.right()==35&&extremes.top()==45&&extremes.bottom()==35,"selected edge respects 20% minimum");
        close(extremes.manualBounds().width(),.20f,"minimum width");close(extremes.manualBounds().height(),.20f,"minimum height");
        FramingSettings malformed=new FramingSettings(300,-1,100,99,-50,999,-900,400);
        check(malformed.left()==0&&malformed.right()==45&&malformed.top()==45&&malformed.bottom()==0
                &&malformed.zoom()==300&&malformed.panX()==-100&&malformed.panY()==100,"invalid values bounded");
        FramingTransform t=FramingTransform.calculate(1920,1080,1920,1080,s.manualBounds(),s);
        close(t.scale(),1080f/810,"uniform manual scale");
        close(t.translateX()+192*t.scale(),t.contentLeft(),"left mapping");
        close(t.translateY()+216*t.scale(),t.contentTop(),"top mapping");
        close(t.matrixX()*1920/1920,t.matrixY()*1080/1080,"uniform non-stretched pixels");
        FramingTransform zoom=FramingTransform.calculate(1920,1080,1920,1080,s.manualBounds(),s.withZoom(101));
        close(zoom.scale()/t.scale(),1.01f,"exact 1% zoom step");
        FramingTransform portrait=FramingTransform.calculate(1080,1920,1920,1080,CropGeometry.Bounds.FULL,FramingSettings.DEFAULT);
        close(portrait.contentWidth(),607.5f,"portrait keeps full image");close(portrait.contentHeight(),1080,"portrait height");
        FramingTransform left=FramingTransform.calculate(1080,1920,1920,1080,CropGeometry.Bounds.FULL,FramingSettings.DEFAULT.withPan(-100,0));
        close(left.contentLeft(),0,"pan to letterbox edge");
        FramingTransform right=FramingTransform.calculate(1080,1920,1920,1080,CropGeometry.Bounds.FULL,FramingSettings.DEFAULT.withPan(100,0));
        close(right.contentLeft()+right.contentWidth(),1920,"pan opposite edge");
        FramingSettings large=FramingSettings.DEFAULT.withZoom(200).withPan(100,-100);
        FramingTransform overflow=FramingTransform.calculate(1920,1080,1920,1080,CropGeometry.Bounds.FULL,large);
        close(overflow.contentLeft(),0,"overflow positive pan");close(overflow.contentTop()+overflow.contentHeight(),1080,"overflow negative pan");
        FramingSettings portraitCrop=FramingSettings.DEFAULT.withEdge(0,34).withEdge(2,34);
        close(portraitCrop.manualBounds().width(),.32f,"34% pillar crop is representable");
        Random random=new Random(2302);
        for(int i=0;i<1000;i++){
            FramingSettings value=new FramingSettings(random.nextInt(4),random.nextInt(90),random.nextInt(90),
                    random.nextInt(90),random.nextInt(90),random.nextInt(400),random.nextInt(401)-200,random.nextInt(401)-200);
            CropGeometry.Bounds b=value.mode()==FramingSettings.MANUAL?value.manualBounds():CropGeometry.Bounds.FULL;
            FramingTransform a=FramingTransform.calculate(1080,1920,1920,1080,b,value);
            close(a.matrixX()*1920/1080,a.matrixY()*1080/1920,"random uniform pixel scale");
            check(a.clipLeft()>=0&&a.clipTop()>=0&&a.clipRight()<=1920&&a.clipBottom()<=1080
                    &&a.clipRight()>a.clipLeft()&&a.clipBottom()>a.clipTop(),"bounded nonempty clip");
        }
    }
    private static FramingProfiles profiles(Map<String,String> values){
        return new FramingProfiles(new FramingProfiles.Store(){
            public String read(String key){return values.get(key);}
            public void write(String key,String value){values.put(key,value);}
        });
    }
    private static void globalPreferences(){
        Map<String,String> values=new HashMap<>();
        FramingProfiles preferences=profiles(values);
        FramingSession session=new FramingSession(preferences,"airplay_mirror");
        check(session.mode()==FramingSettings.CINEMA,"first launch defaults to Auto black-bar removal");
        check(session.settings().equals(FramingSettings.DEFAULT),"first launch has neutral manual settings");
        check(!session.locked()&&session.effectiveBounds().near(CropGeometry.Bounds.FULL),"Auto starts with full image until stable evidence");
        check(values.isEmpty(),"opening app never invents an explicit user choice");
        String[] sources={"airplay_mirror","airplay_media","dlna_media","media","other-phone"};
        int[][] sizes={{1920,1080},{1080,1920},{1280,720},{720,1280},{1080,1080}};
        for(String source:sources)for(int[] size:sizes){
            check(FramingProfiles.GLOBAL_KEY.equals(FramingProfiles.key(source,size[0],size[1])),"every source/orientation uses one key");
            session.sourceProfile(source);session.videoSize(size[0],size[1]);
            check(session.mode()==FramingSettings.CINEMA,"Auto default follows every source/orientation");
        }
        for(int mode=FramingSettings.FIT;mode<=FramingSettings.MANUAL;mode++){
            FramingSettings choice=new FramingSettings(mode,12,7,22,4,123,-21,13);
            session.snapshot();session.preview(choice);session.applyEdits();
            check(values.size()==1&&values.containsKey(FramingProfiles.GLOBAL_KEY),"only global user choice is stored");
            for(String source:sources)for(int[] size:sizes){
                session.sourceProfile(source);session.videoSize(size[0],size[1]);
                check(session.settings().equals(choice),"mode and all chosen manual values survive source and rotation");
                FramingSession restarted=new FramingSession(profiles(values),source);
                restarted.videoSize(size[0],size[1]);
                check(restarted.settings().equals(choice),"all global values survive process restart for every source/size");
                check(!restarted.locked(),"restart never restores detected coordinates");
            }
        }
        session.preview(FramingSettings.DEFAULT.withEdge(0,1));
        check(session.sourceCropped(),"crop status reports even 1% source removal");
        session.preview(FramingSettings.DEFAULT.withZoom(200).withPan(50,-50));
        session.mode(FramingSettings.FIT);
        check(session.mode()==FramingSettings.FIT&&session.settings().zoom()==100
                &&session.settings().panX()==0&&session.settings().panY()==0,"explicit Fit restores full picture");
        session.sourceProfile("airplay_media");session.videoSize(1920,1080);
        session.sourceProfile("dlna_media");session.videoSize(1080,1920);
        check(session.mode()==FramingSettings.FIT,"explicit Fit survives source and orientation changes");
        check(new FramingSession(profiles(values),"airplay_mirror").mode()==FramingSettings.FIT,"explicit Fit remains Fit after restart");
        session.mode(FramingSettings.CINEMA);
        check(new FramingSession(profiles(values),"dlna_media").mode()==FramingSettings.CINEMA,"explicit Auto is persisted too");
        FramingProfiles memoryOnly=new FramingProfiles(null);
        FramingSession withoutAndroid=new FramingSession(memoryOnly,"media");
        withoutAndroid.mode(FramingSettings.FILL);withoutAndroid.sourceProfile("airplay_mirror");
        check(withoutAndroid.mode()==FramingSettings.FILL,"in-memory sessions also retain global choice");
    }
    private static void migrationAndMalformedPreferences(){
        Map<String,String> values=new HashMap<>();
        values.put("framing.v2.airplay_mirror.landscape","1,0,0,0,0,100,0,0");
        values.put("framing.v2.airplay_mirror.portrait","3,12,7,22,4,123,-21,13");
        values.put("framing.v2.dlna_media.landscape","0,0,0,0,0,100,0,0");
        FramingProfiles preferences=profiles(values);
        for(String source:new String[]{"airplay_mirror","airplay_media","dlna_media"}){
            FramingSession upgraded=new FramingSession(preferences,source);
            upgraded.videoSize(1080,1920);
            check(upgraded.settings().equals(FramingSettings.DEFAULT),"legacy untimestamped profiles never determine global preference");
        }
        FramingSession upgraded=new FramingSession(preferences,"dlna_media");
        upgraded.mode(FramingSettings.FIT);
        check(new FramingSession(profiles(values),"airplay_mirror").mode()==FramingSettings.FIT,"new explicit choice supersedes all old profiles");
        for(String invalid:new String[]{"", "broken,values", "2,0,0,0,0,100,0,NaN", "2,0,0,0,0,100,0,0,0",
                "2147483648,0,0,0,0,100,0,0", "-1,0,0,0,0,100,0,0", "4,0,0,0,0,100,0,0"}){
            values.put(FramingProfiles.GLOBAL_KEY,invalid);
            check(preferences.load().equals(FramingSettings.DEFAULT),"malformed record safely falls back to Auto: "+invalid);
        }
        values.put(FramingProfiles.GLOBAL_KEY,"3,100,-2,100,100,999,-900,400");
        FramingSettings bounded=preferences.load();
        check(bounded.left()==45&&bounded.top()==0&&bounded.right()==35&&bounded.bottom()==45
                &&bounded.zoom()==300&&bounded.panX()==-100&&bounded.panY()==100,"numeric global values are validated");
        check(!FramingProfiles.key("https://private.invalid/token",1,2).contains("private"),"preference key excludes URLs and phone identities");
    }
    private static void transactionsAndVideoBoundaries(){
        Map<String,String> values=new HashMap<>();
        FramingProfiles preferences=profiles(values);
        FramingSession session=new FramingSession(preferences,"airplay_mirror");
        FramingSession other=new FramingSession(profiles(values),"dlna_media");
        FramingSettings manual=FramingSettings.DEFAULT.withEdge(0,12).withEdge(1,7)
                .withEdge(2,22).withEdge(3,4).withZoom(123).withPan(-21,13);
        var before=session.snapshot();session.preview(manual);
        check(other.settings().equals(FramingSettings.DEFAULT),"uncommitted preview is private to editor");
        session.cancel(before);
        check(session.settings().equals(FramingSettings.DEFAULT)&&!session.editing,"Cancel restores all fields");
        check(values.isEmpty(),"Cancel never persists preview");
        session.snapshot();session.preview(manual);session.applyEdits();
        other.refreshGlobalSettings();
        check(other.settings().equals(manual),"other existing renderer follows the committed global preference");
        before=session.snapshot();session.resetProfile();
        check(session.settings().equals(FramingSettings.FULL_IMAGE),"Reset previews Fit and neutral manual values");
        other.refreshGlobalSettings();check(other.settings().equals(manual),"Reset preview never changes another renderer");
        session.cancel(before);
        check(session.settings().equals(manual),"Cancel Reset restores committed manual settings");
        session.snapshot();session.resetProfile();session.applyEdits();
        check(new FramingSession(profiles(values),"dlna_media").settings().equals(FramingSettings.FULL_IMAGE),"Apply Reset persists full-image Fit globally");
        for(int i=0;i<4;i++){
            session.snapshot();session.preview(manual);session.applyEdits();
            before=session.snapshot();session.preview(manual.withZoom(188));
            if(i==0)session.videoSize(1080,1920);
            else if(i==1)session.videoSize(2160,3840);
            else if(i==2)session.sourceProfile("dlna_media");
            else session.sourceProfile("dlna_media");
            check(!session.editing&&session.profileEpoch()!=before.profileEpoch(),"source/size boundary invalidates open edit");
            check(session.settings().equals(manual),"source/size boundary discards preview without changing global choice");
            session.cancel(before);
            check(session.settings().equals(manual)&&preferences.load().equals(manual),"obsolete Cancel cannot overwrite global choice");
        }
        before=session.snapshot();session.preview(manual.withZoom(188));
        session.videoSize(1920,1080);session.videoSize(1080,1920);
        var newer=session.snapshot();session.preview(manual.withZoom(155));session.cancel(before);
        check(session.editing&&session.settings().zoom()==155,"old Cancel cannot affect a newer transaction after round-trip rotation");
        session.cancel(newer);check(session.settings().equals(manual),"newer Cancel still restores its own transaction");
        before=session.snapshot();session.preview(manual.withZoom(177));
        other.mode(FramingSettings.FIT);session.refreshGlobalSettings();session.cancel(before);
        check(session.mode()==FramingSettings.FIT&&!session.editing,"new committed global preference wins over stale Cancel");
        session.mode(FramingSettings.CINEMA);
        CropGeometry.Bounds bounds=new CropGeometry.Bounds(.1f,0,.9f,1);
        acquire(session,bounds);
        before=session.snapshot();session.preview(manual);session.cancel(before);
        check(session.locked()&&session.effectiveBounds().near(bounds),"Cancel on same video preserves pre-editor automatic lock");
        for(int i=0;i<4;i++){
            acquire(session,bounds);before=session.snapshot();session.preview(manual);
            if(i==0)session.videoSize(2560,1440);
            else if(i==1)session.sourceProfile("dlna_media");
            else if(i==2)session.sourceProfile("dlna_media");
            else session.reset();
            session.cancel(before);
            check(session.mode()==FramingSettings.CINEMA&&!session.locked()
                    &&session.effectiveBounds().near(CropGeometry.Bounds.FULL),"new geometry/video/rescan cannot restore stale detector coordinates");
        }
        acquire(session,bounds);session.snapshot();session.applyEdits();
        check(new FramingSession(profiles(values),"dlna_media").effectiveBounds().near(CropGeometry.Bounds.FULL),"Auto saves mode, never the detected crop");
        other.refreshGlobalSettings();
        check(!other.locked()&&other.effectiveBounds().near(CropGeometry.Bounds.FULL),"automatic crop is never shared with another video renderer");
        session.sourceProfile("dlna_media");
        for(int i=0;i<7;i++)session.tracker.consider(bounds);
        session.videoSize(640,360);session.tracker.consider(bounds);
        check(!session.locked()&&session.stableSamples()==1,"resize discards acquisition evidence as well as locked coordinates");
    }
    private static void acquire(FramingSession session,CropGeometry.Bounds bounds){
        session.reset();for(int i=0;i<8;i++)session.tracker.consider(bounds);
        check(session.locked(),"fixture acquired automatic crop");
    }
    private static void wideBarsAndSafety(){
        int w=320,h=180;
        byte[] portrait=new byte[w*h*4];paint(portrait,w,109,0,211,h,40,110,200);
        var evidence=CropGeometry.detectDetailed(portrait,w,h);
        check(evidence!=null&&evidence.wideBorders(),"true 9:16 inside 16:9 accepted as wide bars");
        close(evidence.bounds().left(),108f/w,"wide left includes safety pixel");
        close(evidence.bounds().right(),1-108f/w,"wide right includes safety pixel");
        byte[] actualSample=new byte[160*90*4];paint(actualSample,160,55,0,105,90,40,110,200);
        var sampledEvidence=CropGeometry.detectDetailed(actualSample,160,90);
        check(sampledEvidence!=null&&sampledEvidence.wideBorders(),"wide crop works at actual 160x90 readback size");
        close(sampledEvidence.bounds().left(),54f/160,"actual-size safety margin");
        CinemaCropTracker tracker=new CinemaCropTracker();
        for(int i=0;i<11;i++)tracker.considerDetection(evidence);
        check(!tracker.locked()&&tracker.requiredSamples()==12,"wide bars require extra stable evidence");
        tracker.considerDetection(evidence);check(tracker.locked(),"wide bars eventually lock");
        for(int i=0;i<50;i++)tracker.consider(null);
        check(tracker.bounds().near(evidence.bounds()),"dark frames preserve locked wide crop");
        byte[] laterCaption=portrait.clone();paint(laterCaption,w,70,8,92,12,255,255,255);
        CropGeometry.Bounds wider=CropGeometry.expandForVisibleContent(laterCaption,w,h,tracker.bounds());
        check(wider.near(CropGeometry.Bounds.FULL),"caption in formerly-black bar requires expansion");
        check(!tracker.protect(wider),"single transient overlay does not jump crop");
        tracker.protect(CropGeometry.expandForVisibleContent(new byte[w*h*4],w,h,tracker.bounds()));
        check(!tracker.protect(wider),"dark frame breaks safety acquisition without losing crop");
        check(tracker.protect(wider)&&tracker.safetyExpanded(),"persistent later caption expands locked crop");
        check(tracker.locked()&&tracker.bounds().near(CropGeometry.Bounds.FULL),"expanded frame stays held");
        for(int i=0;i<20;i++)tracker.considerDetection(evidence);
        check(tracker.bounds().near(CropGeometry.Bounds.FULL),"cannot tighten again before explicit rescan");
        tracker.reset();
        check(!tracker.safetyExpanded(),"rescan clears safety hold status");
        for(int i=0;i<11;i++)tracker.considerDetection(evidence);
        tracker.consider(null);tracker.considerDetection(evidence);
        check(!tracker.locked()&&tracker.stableSamples()==1,"dark gap breaks acquisition confidence");
        check(CropGeometry.detect(new byte[w*h*4],w,h)==null,"black frame rejected");
        byte[] faded=new byte[w*h*4];paint(faded,w,109,0,211,h,28,28,28);
        check(CropGeometry.detect(faded,w,h)==null,"dim gray scene cannot acquire crop");
        byte[] darkObject=new byte[w*h*4];paint(darkObject,w,120,40,200,140,80,100,150);
        check(CropGeometry.detect(darkObject,w,h)==null,"isolated object in dark scene rejected");
        byte[] letterbox=new byte[w*h*4];paint(letterbox,w,0,24,w,156,40,110,200);
        var clear=CropGeometry.detect(letterbox,w,h);
        check(clear!=null&&clear.bottom()>0,"letterbox detects before subtitles");
        paint(letterbox,w,155,8,165,10,255,255,255);
        var subtitles=CropGeometry.detect(letterbox,w,h);
        check(subtitles!=null&&subtitles.bottom()<=8f/h,"subtitle pixels never removed");
        byte[] indicator=portrait.clone();paint(indicator,w,10,70,13,72,255,255,255);
        var guarded=CropGeometry.detect(indicator,w,h);
        check(guarded==null||guarded.left()<=10f/w,"sparse bright side content never discarded");
        byte[] asymmetric=new byte[w*h*4];paint(asymmetric,w,109,0,w,h,40,110,200);
        check(CropGeometry.detect(asymmetric,w,h).near(CropGeometry.Bounds.FULL),"unpaired edge not cropped");
        byte[] tiny=new byte[w*h*4];paint(tiny,w,145,0,175,h,80,110,200);
        check(CropGeometry.detect(tiny,w,h)==null,"extreme sliver source rejected");
        check(CropGeometry.detect(new byte[2],w,h)==null,"malformed sample rejected");
    }
    private static void paint(byte[] p,int w,int l,int b,int r,int t,int red,int green,int blue){
        for(int y=b;y<t;y++)for(int x=l;x<r;x++){int i=(y*w+x)*4;p[i]=(byte)red;p[i+1]=(byte)green;p[i+2]=(byte)blue;p[i+3]=(byte)255;}
    }
    private static void close(float actual,float expected,String label){if(Math.abs(actual-expected)>.002f)throw new AssertionError(label+": "+actual+" != "+expected);}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
