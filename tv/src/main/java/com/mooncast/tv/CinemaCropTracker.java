package com.mooncast.tv;

/** A cinema session acquires one stable border and holds it until explicit rescan or resize. */
final class CinemaCropTracker {
    record State(CropGeometry.Bounds crop,CropGeometry.Bounds candidate,int stable,int required,boolean locked,
                 CropGeometry.Bounds safetyCandidate,int safetyStable,boolean safetyExpanded) {}
    private CropGeometry.Bounds crop=CropGeometry.Bounds.FULL,candidate;
    private int stable,required=8;
    private boolean locked,safetyExpanded;
    private CropGeometry.Bounds safetyCandidate;
    private int safetyStable;
    CropGeometry.Bounds bounds(){return crop;}
    boolean locked(){return locked;}
    int stableSamples(){return stable;}
    int requiredSamples(){return required;}
    boolean safetyExpanded(){return safetyExpanded;}
    State snapshot(){return new State(crop,candidate,stable,required,locked,safetyCandidate,safetyStable,safetyExpanded);}
    void restore(State state){if(state==null){reset();return;}crop=state.crop();candidate=state.candidate();stable=state.stable();required=state.required();locked=state.locked();safetyCandidate=state.safetyCandidate();safetyStable=state.safetyStable();safetyExpanded=state.safetyExpanded();}
    void reset(){crop=CropGeometry.Bounds.FULL;candidate=null;stable=0;required=8;locked=false;safetyCandidate=null;safetyStable=0;safetyExpanded=false;}
    void rejectSample(){safetyCandidate=null;safetyStable=0;if(!locked){candidate=null;stable=0;required=8;}}
    boolean consider(CropGeometry.Bounds detected){
        return considerDetection(detected==null?null:new CropGeometry.Detection(detected,1,
                detected.left()>=.29f||detected.bottom()>=.29f));
    }
    boolean considerDetection(CropGeometry.Detection evidence){
        if(locked)return false;
        if(evidence==null||evidence.confidence()<.87f){rejectSample();return false;}
        CropGeometry.Bounds detected=evidence.bounds();
        if(detected==null||detected.near(CropGeometry.Bounds.FULL)){rejectSample();return false;}
        int needed=evidence.wideBorders()?12:8;
        if(candidate!=null&&close(candidate,detected)){
            stable++;
            required=Math.max(required,needed);
            // Retain all content observed during acquisition instead of averaging away an edge.
            candidate=new CropGeometry.Bounds(Math.min(candidate.left(),detected.left()),
                    Math.min(candidate.bottom(),detected.bottom()),Math.max(candidate.right(),detected.right()),
                    Math.max(candidate.top(),detected.top()));
        }else{candidate=detected;stable=1;required=needed;}
        if(stable<required)return false;
        crop=candidate;locked=true;return true;
    }
    /** Two consecutive samples widen only. Never re-acquire/tighten until explicit rescan. */
    boolean protect(CropGeometry.Bounds expanded){
        if(!locked||expanded==null||expanded.near(crop)){safetyCandidate=null;safetyStable=0;return false;}
        CropGeometry.Bounds safe=new CropGeometry.Bounds(Math.min(crop.left(),expanded.left()),
                Math.min(crop.bottom(),expanded.bottom()),Math.max(crop.right(),expanded.right()),
                Math.max(crop.top(),expanded.top()));
        if(safe.near(crop)){safetyCandidate=null;safetyStable=0;return false;}
        if(safetyCandidate!=null&&safetyCandidate.near(safe))safetyStable++;
        else{safetyCandidate=safe;safetyStable=1;}
        if(safetyStable<2)return false;
        crop=safetyCandidate;safetyExpanded=true;safetyCandidate=null;safetyStable=0;
        return true;
    }
    private static boolean close(CropGeometry.Bounds a,CropGeometry.Bounds b){
        return Math.abs(a.left()-b.left())<=.0126f&&Math.abs(a.right()-b.right())<=.0126f
                &&Math.abs(a.bottom()-b.bottom())<=.0126f&&Math.abs(a.top()-b.top())<=.0126f;
    }
}
