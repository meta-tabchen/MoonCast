package com.mooncast.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cinema standby and a genuinely translucent, surface-independent playback control layer. */
@android.annotation.SuppressLint("ViewConstructor")
public final class TvHomeView extends FrameLayout {
    public interface Listener {
        void onFraming();
        void onAdvanced();
        default void onReceiveApk() {}
        default void onEndCast() {}
        void onStopStart();
        void onHide();
    }
    private static final int WHITE=0xfff5f6ff, MUTED=0xffa6afc5, BLUE=0xff5070ff;
    private final Map<View,Rect> positions=new LinkedHashMap<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF glass=new RectF();
    private final TextView brand,status,heading,instruction,receiver,trusted;
    private final CastMark castMark;
    private final Action framing,advanced,hide,toggle,receiveApk,endCast;
    private final TvBackdrop backdrop;
    private int theme;
    private boolean receiving,playbackActive,controlsEnabled=true,compact;
    private String hint="";

    public TvHomeView(Context context,Listener listener){
        super(context);setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);
        setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Never set an opaque View background: TextureViews remain visible and attached below us.
        setBackgroundColor(Color.TRANSPARENT);
        backdrop=new TvBackdrop(getResources());
        theme=TvTheme.read(context.getSharedPreferences("receiver-settings",Context.MODE_PRIVATE));
        brand=label("MoonCast",26,WHITE);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));addView(brand);
        status=label("",12,MUTED);status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);addView(status);
        heading=label(getResources().getString(R.string.cinema_ready),38,WHITE);
        heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heading.setGravity(Gravity.CENTER);addView(heading);
        instruction=label("",13,MUTED);instruction.setGravity(Gravity.CENTER);instruction.setMaxLines(2);addView(instruction);
        receiver=label("MoonCast TV",19,WHITE);receiver.setGravity(Gravity.CENTER);addView(receiver);
        trusted=label(getResources().getString(R.string.home_trusted),11,MUTED);trusted.setGravity(Gravity.CENTER_VERTICAL);addView(trusted);
        castMark=new CastMark(context);addView(castMark);
        framing=new Action(0,R.string.home_framing,R.string.home_framing_detail,false);
        advanced=new Action(1,R.string.home_advanced,R.string.home_advanced_detail,false);
        hide=new Action(2,R.string.gs_connect_title,R.string.gs_phone_detail,true);
        toggle=new Action(3,R.string.home_start,0,false);
        receiveApk=new Action(4,R.string.apk_home,0,false);
        endCast=new Action(3,R.string.cinema_end_cast,0,false);
        framing.setOnClickListener(v->listener.onFraming());advanced.setOnClickListener(v->listener.onAdvanced());
        hide.setOnClickListener(v->listener.onHide());toggle.setOnClickListener(v->listener.onStopStart());
        receiveApk.setOnClickListener(v->listener.onReceiveApk());endCast.setOnClickListener(v->listener.onEndCast());
        for(Action a:new Action[]{framing,advanced,hide,toggle,receiveApk,endCast}){a.setId(View.generateViewId());addView(a);}
        endCast.setVisibility(GONE);configureFocus();setReceiving(false);setStatus(getResources().getString(R.string.home_waiting),false);
    }
    public void setTheme(int value){if(value<0||value>=TvTheme.IDS.length)return;theme=value;invalidate();}
    void drawThemePreview(Canvas canvas,int width,int height,int value){backdrop.draw(canvas,width,height,value);}
    int selectedTheme(){return theme;}
    public void setReceiverName(String value){receiver.setText(value);receiver.setContentDescription(getResources().getString(R.string.home_receiver_accessibility,value));}
    public void setStatus(String value,boolean ready){status.setText(getResources().getString(R.string.home_status_line,value));status.setTextColor(ready?0xffb6c4ff:MUTED);}
    public void setReceiving(boolean value){boolean started=value&&!receiving&&toggle.hasFocus();receiving=value;toggle.title(value?R.string.home_stop:R.string.home_start);if(started)requestPrimaryFocus();}
    public void setModeLabel(String value){framing.subtitle(value);}
    public void setHint(String value){hint=value==null?"":value.trim();updateInstruction();}
    public void setPlaybackActive(boolean value){
        if(playbackActive==value)return;
        playbackActive=value;
        heading.setText(value?R.string.cinema_casting:R.string.cinema_ready);
        hide.title(value?R.string.home_return_playback:R.string.gs_connect_title);
        hide.subtitle(value?"":getResources().getString(R.string.gs_phone_detail));
        endCast.setVisibility(value?VISIBLE:GONE);receiveApk.setVisibility(value?GONE:VISIBLE);toggle.setVisibility(value?GONE:VISIBLE);
        updateInstruction();configureFocus();requestLayout();invalidate();
        // A session can end while focus is on End cast. Move to a still-visible control.
        if(getVisibility()==VISIBLE&&findFocus()!=null)requestPrimaryFocus();
    }
    public void setControlsEnabled(boolean value){controlsEnabled=value;for(Action a:actions()){a.setEnabled(value);a.setAlpha(value?1f:.45f);}}
    public void requestPrimaryFocus(){if(controlsEnabled)(playbackActive?hide:receiving?framing:toggle).requestFocus();}
    boolean isPlaybackOverlay(){return playbackActive;}
    private Action[] actions(){return new Action[]{framing,advanced,hide,toggle,receiveApk,endCast};}
    private void configureFocus(){
        framing.setNextFocusRightId(advanced.getId());advanced.setNextFocusLeftId(framing.getId());advanced.setNextFocusRightId(hide.getId());hide.setNextFocusLeftId(advanced.getId());
        framing.setNextFocusLeftId(framing.getId());hide.setNextFocusRightId(hide.getId());
        for(Action a:actions()){a.setNextFocusUpId(a.getId());a.setNextFocusDownId(a.getId());}
        if(playbackActive){
            hide.setNextFocusDownId(endCast.getId());endCast.setNextFocusUpId(hide.getId());endCast.setNextFocusDownId(advanced.getId());
            framing.setNextFocusUpId(endCast.getId());advanced.setNextFocusUpId(endCast.getId());
            advanced.setNextFocusDownId(hide.getId());framing.setNextFocusDownId(hide.getId());
            endCast.setNextFocusLeftId(framing.getId());endCast.setNextFocusRightId(advanced.getId());
        }else{
            framing.setNextFocusDownId(receiveApk.getId());advanced.setNextFocusDownId(receiveApk.getId());hide.setNextFocusDownId(toggle.getId());
            receiveApk.setNextFocusUpId(advanced.getId());receiveApk.setNextFocusRightId(toggle.getId());receiveApk.setNextFocusLeftId(receiveApk.getId());
            toggle.setNextFocusUpId(hide.getId());toggle.setNextFocusLeftId(receiveApk.getId());toggle.setNextFocusRightId(toggle.getId());
        }
    }
    private void updateInstruction(){instruction.setText(playbackActive?getResources().getString(R.string.cinema_playback_continues):hint.isEmpty()?getResources().getString(R.string.home_instructions):hint);}
    private TextView label(String text,float size,int color){TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setEllipsize(TextUtils.TruncateAt.END);v.setMaxLines(1);v.setFocusable(false);return v;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private float units(int n){return n/getResources().getDisplayMetrics().density;}
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);positions.clear();
        float width=units(w),height=units(h),safe=40,cw=width-safe*2;
        compact=height<560;float header=compact?25:30;
        framing.mini=playbackActive;advanced.mini=playbackActive;hide.mini=playbackActive;
        for(Action a:actions())a.refreshText();
        place(brand,safe,safe,180,header);place(status,width-safe-320,safe,320,header);
        place(trusted,safe,height-safe-25,Math.max(0,cw-370),25);
        if(playbackActive){
            float cardW=Math.min(386,cw),cardH=Math.min(370,height-138),x=(width-cardW)/2,y=(height-cardH)/2+8;
            glass.set(dp(x),dp(y),dp(x+cardW),dp(y+cardH));
            float inner=cardW-40;
            place(castMark,width/2-29,y+20,58,46);heading.setTextSize(27);place(heading,x+20,y+78,inner,38);
            receiver.setTextSize(15);place(receiver,x+20,y+120,inner,23);
            instruction.setTextSize(11);place(instruction,x+20,y+146,inner,20);
            float bh=46,gap=10,primaryY=y+cardH-174;
            place(hide,x+20,primaryY,inner,bh);place(endCast,x+20,primaryY+bh+gap,inner,bh);
            place(framing,x+20,y+cardH-58,(inner-10)/2,38);place(advanced,width/2+5,y+cardH-58,(inner-10)/2,38);

        }else{
            glass.setEmpty();float heroY=safe+header+22,cardY=height-safe-126;
            float heroH=cardY-heroY-22;
            place(castMark,width/2-32,heroY,64,52);
            heading.setTextSize(compact?32:40);place(heading,safe,heroY+66,cw,52);
            receiver.setTextSize(19);place(receiver,safe,heroY+122,cw,30);
            instruction.setTextSize(13);place(instruction,width*.2f,heroY+160,width*.6f,Math.max(36,heroH-160));
            float gap=14,item=(cw-gap*2)/3;
            place(framing,safe,cardY,item,72);place(advanced,safe+item+gap,cardY,item,72);place(hide,safe+(item+gap)*2,cardY,item,72);
            place(receiveApk,width-safe-347,height-safe-34,175,44);place(toggle,width-safe-160,height-safe-30,160,36);

        }
        updateInstruction();
    }
    private void place(View v,float x,float y,float w,float h){Rect r=new Rect(dp(x),dp(y),dp(x+w),dp(y+h));positions.put(v,r);v.measure(MeasureSpec.makeMeasureSpec(Math.max(0,r.width()),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,r.height()),MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean c,int l,int t,int r,int b){for(Map.Entry<View,Rect> e:positions.entrySet()){Rect p=e.getValue();e.getKey().layout(p.left,p.top,p.right,p.bottom);}}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(!playbackActive){
            backdrop.draw(c,getWidth(),getHeight(),theme);
        }else{
            // This scrim never samples, swaps, pauses, detaches or obscures the video with standby art.
            c.drawColor(0x28050a14);
            paint.setColor(0xdb101623);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(glass,dp(23),dp(23),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x555e6b86);c.drawRoundRect(glass,dp(23),dp(23),paint);paint.setStyle(Paint.Style.FILL);
        }
    }
    private final class Action extends FrameLayout {
        private final TextView title,subtitle;private final Icon icon;private final boolean primary;private boolean mini;
        Action(int kind,int titleId,int subId,boolean primary){
            super(TvHomeView.this.getContext());this.primary=primary;setFocusable(true);setClickable(true);setDefaultFocusHighlightEnabled(false);setClipChildren(false);
            title=label(getResources().getString(titleId),16,WHITE);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            subtitle=label(subId==0?"":getResources().getString(subId),11,MUTED);icon=new Icon(getContext(),kind);
            for(View v:new View[]{title,subtitle,icon}){v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(v);}
            setOnFocusChangeListener((v,f)->{style();animate().cancel();animate().scaleX(f?1.025f:1f).scaleY(f?1.025f:1f).setDuration(120).start();});style();description();
        }
        void title(int id){title.setText(id);description();}
        void subtitle(String value){subtitle.setText(value);description();}
        void description(){setContentDescription(title.getText()+(subtitle.getText().length()==0?"":", "+subtitle.getText()));}
        void refreshText(){title.setTextSize(mini?14:15);subtitle.setVisibility(mini?GONE:VISIBLE);}
        void style(){GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(12));d.setColor(primary?BLUE:hasFocus()?0xe8374566:0xd51a2132);d.setStroke(dp(hasFocus()?2:1),hasFocus()?0xffe5ebff:0x5545506b);setBackground(d);setElevation(hasFocus()?dp(8):0);}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);int iconSize=dp(22),textWidth=Math.max(1,w-dp(62));icon.measure(MeasureSpec.makeMeasureSpec(iconSize,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(iconSize,MeasureSpec.EXACTLY));title.measure(MeasureSpec.makeMeasureSpec(textWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(23),MeasureSpec.EXACTLY));subtitle.measure(MeasureSpec.makeMeasureSpec(textWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(18),MeasureSpec.EXACTLY));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int w=r-l,h=b-t,x=dp(46),iy=(h-dp(22))/2;icon.layout(dp(15),iy,dp(37),iy+dp(22));boolean sub=!mini&&subtitle.getText().length()>0;int ty=(h-dp(sub?42:23))/2;title.layout(x,ty,w-dp(12),ty+dp(23));subtitle.layout(x,ty+dp(24),w-dp(12),ty+dp(42));}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());}
        @Override protected void onDetachedFromWindow(){animate().cancel();super.onDetachedFromWindow();}
    }
    private final class CastMark extends View {
        CastMark(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/64f,getHeight()/52f);drawCast(c,64,52,0xff7d8aff);c.restore();}
    }
    private void drawCast(Canvas c,float w,float h,int color){paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w/14);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(w*.08f,h*.19f,w*.08f,h*.08f,paint);c.drawLine(w*.08f,h*.08f,w*.91f,h*.08f,paint);c.drawLine(w*.91f,h*.08f,w*.91f,h*.84f,paint);c.drawLine(w*.91f,h*.84f,w*.66f,h*.84f,paint);c.drawArc(-w*.42f,h*.25f,w*.53f,h*1.41f,-90,90,false,paint);c.drawArc(-w*.2f,h*.51f,w*.31f,h*1.15f,-90,90,false,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(w*.055f,h*.83f,w*.045f,paint);}
    private final class Icon extends View {
        private final int kind;Icon(Context c,int kind){super(c);this.kind=kind;}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);paint.setColor(0xffdce3ff);paint.setStrokeWidth(1.6f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
            if(kind==0){c.drawRoundRect(2,4,22,20,3,3,paint);c.drawLine(7,8,7,16,paint);c.drawLine(17,8,17,16,paint);}
            else if(kind==1){c.drawCircle(12,12,6,paint);c.drawCircle(12,12,2,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,paint);c.restore();}}
            else if(kind==2){c.drawLine(8,5,18,12,paint);c.drawLine(18,12,8,19,paint);c.drawLine(8,19,8,5,paint);}
            else if(kind==3)c.drawRoundRect(5,5,19,19,3,3,paint);
            else{c.drawRect(4,6,20,21,paint);c.drawLine(4,6,12,2,paint);c.drawLine(12,2,20,6,paint);c.drawLine(12,8,12,16,paint);}
            paint.setStyle(Paint.Style.FILL);c.restore();
        }
    }
}
