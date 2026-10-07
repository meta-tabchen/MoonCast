package com.mooncast.host;

/** Stabilizes borders independently of frame rate, so a paused video can settle too. */
final class AutoCropTracker {
    private CropGeometry.Bounds crop=CropGeometry.Bounds.FULL,candidate=CropGeometry.Bounds.FULL;
    private int count;
    CropGeometry.Bounds bounds(){return crop;}
    void reset(){crop=candidate=CropGeometry.Bounds.FULL;count=0;}
    boolean consider(CropGeometry.Bounds detected){
        if(detected==null){count=0;return false;}
        if(detected.near(candidate))count++;else{candidate=detected;count=1;}
        // Opening player controls should not immediately undo an established video crop.
        boolean expands=candidate.width()>crop.width()+.018f || candidate.height()>crop.height()+.018f;
        int required=expands?6:3;
        if(count>=required && !crop.near(candidate)){crop=candidate;return true;}
        return false;
    }
}
