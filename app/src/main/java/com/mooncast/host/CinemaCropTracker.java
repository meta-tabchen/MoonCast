package com.mooncast.host;

/** A cinema session acquires one stable border and holds it until explicit rescan or resize. */
final class CinemaCropTracker {
    private CropGeometry.Bounds crop=CropGeometry.Bounds.FULL,candidate;
    private int stable;
    private boolean locked;
    CropGeometry.Bounds bounds(){return crop;}
    boolean locked(){return locked;}
    void reset(){crop=CropGeometry.Bounds.FULL;candidate=null;stable=0;locked=false;}
    boolean consider(CropGeometry.Bounds detected){
        if(locked)return false;
        if(detected==null){candidate=null;stable=0;return false;}
        // A full-frame UI is not a reason to stop looking for a subsequent video.
        if(detected.near(CropGeometry.Bounds.FULL)){candidate=null;stable=0;return false;}
        if(candidate!=null && candidate.near(detected))stable++;
        else{candidate=detected;stable=1;}
        if(stable<8)return false;
        crop=candidate;locked=true;return true;
    }
}
