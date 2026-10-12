package com.mooncast.host;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.LinkedHashMap;
import java.util.function.IntConsumer;

/** A single accessible selection; actions stay in the content panel beneath it. */
final class ProtocolTabs extends RadioGroup {
    private static final int ACCENT = 0xff635bff;
    private final LinkedHashMap<Integer, Integer> modes = new LinkedHashMap<>();
    private boolean syncing;

    ProtocolTabs(Context context, IntConsumer selected) {
        super(context);
        setOrientation(HORIZONTAL);
        setBaselineAligned(false);
        setMinimumHeight(dp(56));
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        setBackground(PhoneVisuals.surface(context, 0xfff5f5f7, 999));
        setTag("protocol_tabs");
        setOnCheckedChangeListener((group, id) -> {
            if (!syncing && modes.containsKey(id)) selected.accept(modes.get(id));
        });
    }

    RadioButton addTab(String label, String tag, int mode) {
        RadioButton tab = new RadioButton(getContext()) {
            @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                super.onSizeChanged(width, height, oldWidth, oldHeight);
                int labelWidth = (int) Math.ceil(getPaint().measureText(getText().toString()));
                int inset = Math.max(dp(6), (width - dp(20) - dp(6) - labelWidth) / 2);
                setPaddingRelative(inset, dp(8), inset, dp(8));
            }
        };
        tab.setId(View.generateViewId());
        modes.put(tab.getId(), mode);
        tab.setTag(tag);
        tab.setText(label);
        tab.setContentDescription(label);
        tab.setButtonDrawable(null);
        tab.setAllCaps(false);
        tab.setTextSize(14);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tab.setGravity(Gravity.CENTER);
        tab.setMaxLines(2);
        tab.setMinWidth(0);
        tab.setMinimumWidth(0);
        tab.setMinHeight(dp(48));
        tab.setMinimumHeight(dp(48));
        tab.setPadding(dp(4), dp(8), dp(4), dp(8));
        tab.setStateListAnimator(null);
        tab.setElevation(0);
        ColorStateList colors = new ColorStateList(new int[][] {
            {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}
        }, new int[] {0xffa6acb8, ACCENT, 0xff667085});
        tab.setTextColor(colors);
        int iconResource = switch (mode) {
            case CastSessionCoordinator.AIRPLAY -> R.drawable.ic_cast;
            case CastSessionCoordinator.MOONLIGHT -> R.drawable.ic_gamepad;
            default -> R.drawable.ic_browser;
        };
        Drawable icon = getContext().getDrawable(iconResource).mutate();
        icon.setBounds(0, 0, dp(20), dp(20));
        tab.setCompoundDrawablesRelative(icon, null, null, null);
        tab.setCompoundDrawablePadding(dp(6));
        tab.setCompoundDrawableTintList(colors);
        StateListDrawable backgrounds = new StateListDrawable();
        backgrounds.addState(new int[] {android.R.attr.state_focused}, surface(0xffeae8ff));
        backgrounds.addState(new int[] {android.R.attr.state_checked}, surface(Color.WHITE));
        backgrounds.addState(new int[] {}, surface(Color.TRANSPARENT));
        tab.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22635bff), backgrounds, null));
        RadioGroup.LayoutParams layout = new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.MATCH_PARENT, 1);
        if (getChildCount() > 0) layout.setMarginStart(dp(2));
        addView(tab, layout);
        return tab;
    }

    void select(int mode) {
        syncing = true;
        try {
            for (var entry : modes.entrySet()) {
                if (entry.getValue() == mode) { check(entry.getKey()); return; }
            }
        } finally { syncing = false; }
    }

    private GradientDrawable surface(int color) {
        return PhoneVisuals.surface(getContext(), color, 999);
    }
    private int dp(int value) { return PhoneVisuals.dp(getContext(), value); }
}
