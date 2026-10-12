package com.mooncast.host;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private final LinkedHashMap<Integer, Integer> modes = new LinkedHashMap<>();
    private boolean syncing;

    ProtocolTabs(Context context, IntConsumer selected) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        setBackground(PhoneVisuals.surface(context, 0xffedf1f7, 16));
        setTag("protocol_tabs");
        setOnCheckedChangeListener((group, id) -> {
            if (!syncing && modes.containsKey(id)) selected.accept(modes.get(id));
        });
    }

    RadioButton addTab(String label, String tag, int mode) {
        RadioButton tab = new RadioButton(getContext());
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
        tab.setPadding(dp(4), 0, dp(4), 0);
        tab.setStateListAnimator(null);
        tab.setElevation(0);
        tab.setTextColor(new ColorStateList(new int[][] {
            {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}
        }, new int[] {0xffa3afbf, PhoneVisuals.BLUE, PhoneVisuals.MUTED}));
        StateListDrawable backgrounds = new StateListDrawable();
        backgrounds.addState(new int[] {android.R.attr.state_focused}, surface(Color.WHITE, PhoneVisuals.BLUE));
        backgrounds.addState(new int[] {android.R.attr.state_checked}, surface(Color.WHITE, 0xffdde6f2));
        backgrounds.addState(new int[] {}, surface(Color.TRANSPARENT, Color.TRANSPARENT));
        tab.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22087cff), backgrounds, null));
        RadioGroup.LayoutParams layout = new RadioGroup.LayoutParams(0, dp(48), 1);
        if (getChildCount() > 0) layout.setMarginStart(dp(4));
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

    private GradientDrawable surface(int color, int border) {
        GradientDrawable drawable = PhoneVisuals.surface(getContext(), color, 12);
        drawable.setStroke(dp(1), border);
        return drawable;
    }
    private int dp(int value) { return PhoneVisuals.dp(getContext(), value); }
}
