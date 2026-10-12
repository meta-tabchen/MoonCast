package com.mooncast.host;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

/** Shared light visual language; contains no session or network state. */
final class PhoneVisuals {
    static final int INK=0xff101828, MUTED=0xff78869e, BLUE=0xff087cff, CANVAS=0xfff8faff;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Context c,int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(c,radius));return shape;
    }
    static void button(Button b,boolean primary){
        Context c=b.getContext();b.setAllCaps(false);b.setTextSize(16);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        b.setMinHeight(dp(c,52));b.setMinimumHeight(dp(c,52));b.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));
        b.setStateListAnimator(null);b.setElevation(0);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffa3afbf,primary?Color.WHITE:BLUE}));
        GradientDrawable shape=surface(c,primary?BLUE:0xffedf3fc,18);
        shape.setColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffe9edf4,primary?BLUE:0xffedf3fc}));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22087cff),shape,null));
    }
    static ImageView icon(Context c,int resource,int size,int color){
        ImageView image=new ImageView(c);image.setImageResource(resource);image.setImageTintList(ColorStateList.valueOf(color));
        image.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(c,size),dp(c,size)));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;
    }
    private PhoneVisuals(){}
}
