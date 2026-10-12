package com.mooncast.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.View;

/** Live remote editor. OK commits, Back restores, Menu previews complete-image reset. */
final class CropEditorOverlay extends View {
    interface Listener { void finished(boolean applied); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private VideoFraming framing;
    private VideoFraming.Snapshot before;
    private String profile;
    private int selected;
    private final int[] labels={R.string.edit_zoom,R.string.edit_pan_x,R.string.edit_pan_y,R.string.edit_left,R.string.edit_top,R.string.edit_right,R.string.edit_bottom};
    CropEditorOverlay(Context c,Listener l){super(c);listener=l;setFocusable(true);setFocusableInTouchMode(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setVisibility(GONE);}
    void begin(VideoFraming f){if(isEditing())finish(false);framing=f;before=f.snapshot();profile=f.profileKey();selected=0;f.preview(f.settings().withMode(VideoFraming.MANUAL));setVisibility(VISIBLE);requestFocus();announce();invalidate();}
    boolean isEditing(){return getVisibility()==VISIBLE&&framing!=null;}
    void refresh(){if(!isEditing())return;if((!profile.equals(framing.profileKey())||before.profileEpoch()!=framing.profileEpoch())){finish(false);return;}invalidate();}
    void finish(boolean apply){if(!isEditing())return;if(apply&&profile.equals(framing.profileKey())&&before.profileEpoch()==framing.profileEpoch())framing.applyEdits();else framing.cancel(before);framing=null;before=null;setVisibility(GONE);listener.finished(apply);}
    private int value(){FramingSettings s=framing.settings();return switch(selected){case 0->s.zoom();case 1->s.panX();case 2->s.panY();case 3->s.left();case 4->s.top();case 5->s.right();default->s.bottom();};}
    private void adjust(int direction,int step){FramingSettings s=framing.settings();int v=value()+direction*step;s=switch(selected){case 0->s.withZoom(v);case 1->s.withPan(v,s.panY());case 2->s.withPan(s.panX(),v);case 3->s.withEdge(0,v);case 4->s.withEdge(1,v);case 5->s.withEdge(2,v);default->s.withEdge(3,v);};framing.preview(s.withMode(VideoFraming.MANUAL));announce();invalidate();}
    private void announce(){setContentDescription(getContext().getString(labels[selected])+": "+value()+"%");}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(!isEditing())return super.onKeyDown(key,e);if(before.profileEpoch()!=framing.profileEpoch()||!profile.equals(framing.profileKey())){finish(false);return true;}switch(key){
        case KeyEvent.KEYCODE_DPAD_UP:selected=(selected+labels.length-1)%labels.length;announce();invalidate();return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:selected=(selected+1)%labels.length;announce();invalidate();return true;
        case KeyEvent.KEYCODE_DPAD_LEFT:adjust(-1,e.getRepeatCount()>=8?5:1);return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT:adjust(1,e.getRepeatCount()>=8?5:1);return true;
        case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:if(e.getRepeatCount()==0)finish(true);return true;
        case KeyEvent.KEYCODE_BACK:if(e.getRepeatCount()==0)finish(false);return true;
        case KeyEvent.KEYCODE_MENU:if(e.getRepeatCount()==0){framing.resetProfile();announce();invalidate();}return true;
        default:return true;
    }}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(!isEditing())return;float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),hud=Math.min(h,140*d),pad=22*d;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(196,9,17,29));c.drawRect(0,h-hud,w,h,paint);
        paint.setColor(0xff7ee2c6);paint.setTextSize(19*d);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText(getContext().getString(R.string.live_crop_title),pad,h-hud+27*d,paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);paint.setTextSize(18*d);paint.setColor(Color.WHITE);c.drawText(getContext().getString(labels[selected])+"    ◀  "+value()+"%  ▶",pad,h-hud+54*d,paint);
        FramingSettings s=framing.settings();paint.setTextSize(12*d);paint.setColor(0xffc7d4e7);c.drawText(getContext().getString(R.string.crop_four_values,s.left(),s.top(),s.right(),s.bottom(),s.zoom(),s.panX(),s.panY()),pad,h-hud+77*d,paint);
        c.drawText(getContext().getString(R.string.live_crop_keys),pad,h-hud+100*d,paint);paint.setColor(0xffffd28a);c.drawText(getContext().getString(R.string.crop_subtitle_warning),pad,h-hud+123*d,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);paint.setColor(0x997ee2c6);c.drawRect(framing.displayRect(),paint);paint.setStyle(Paint.Style.FILL);
    }
}
