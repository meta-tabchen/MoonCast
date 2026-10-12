package com.mooncast.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Focus previews are temporary; only an explicit click persists a theme. */
final class TvThemeDialog {
    private TvThemeDialog() {}
    static void show(Activity activity, SharedPreferences preferences, TvHomeView home) {
        int[] preview = {TvTheme.read(preferences)};
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int gap = dp(activity, 12); content.setPadding(gap, gap, gap, gap);
        TextView label = new TextView(activity);
        label.setTextColor(0xffdfe5ff); label.setTextSize(15);
        label.setPadding(0, 0, 0, gap);
        content.addView(label);
        View artwork = new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                home.drawThemePreview(canvas, getWidth(), getHeight(), preview[0]);
            }
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                setMeasuredDimension(width, Math.min(dp(activity, 120), width * 9 / 16));
            }
        };
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(artwork, new LinearLayout.LayoutParams(-1, dp(activity, 120)));
        TextView hint = new TextView(activity);
        hint.setText(R.string.theme_hint); hint.setTextColor(0xffa6afc5); hint.setTextSize(12);
        hint.setPadding(0, gap, 0, gap); content.addView(hint);
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setBaselineAligned(false);
        Button[] buttons = new Button[TvTheme.IDS.length];
        Runnable refresh = () -> {
            int selected = TvTheme.read(preferences);
            for (int i = 0; i < buttons.length; i++) {
                Button button = buttons[i]; if (button == null) continue;
                String name = activity.getString(TvTheme.LABELS[i]);
                button.setText(i == selected ? activity.getString(R.string.theme_selected, name) : name);
                button.setSelected(i == selected);
                style(activity, button, i == selected);
            }
            label.setText(activity.getString(R.string.theme_preview, activity.getString(TvTheme.LABELS[preview[0]])));
        };
        for (int i = 0; i < buttons.length; i++) {
            final int index = i;
            Button button = new Button(activity); buttons[i] = button;
            button.setId(View.generateViewId()); button.setAllCaps(false);
            button.setTextColor(Color.WHITE); button.setTextSize(13);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setGravity(Gravity.CENTER); button.setMinHeight(0); button.setMinimumHeight(0);
            button.setPadding(dp(activity, 5), 0, dp(activity, 5), 0);
            button.setMaxLines(2); button.setDefaultFocusHighlightEnabled(false);
            button.setOnFocusChangeListener((view, focused) -> {
                if (focused) { preview[0] = index; artwork.invalidate(); }
                refresh.run();
            });
            button.setOnClickListener(view -> {
                TvTheme.save(preferences, index); home.setTheme(index);
                preview[0] = index; artwork.invalidate(); refresh.run();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 56), 1);
            if (i > 0) params.leftMargin = dp(activity, 8);
            choices.addView(button, params);
        }
        content.addView(choices);
        android.widget.ScrollView scroller=new android.widget.ScrollView(activity);
        scroller.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.theme_title)
                .setView(scroller).setNegativeButton(R.string.close, null).create();
        dialog.setOnShowListener(ignored -> {
            Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (close.getId() == View.NO_ID) close.setId(View.generateViewId());
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setNextFocusLeftId(buttons[Math.max(0, i - 1)].getId());
                buttons[i].setNextFocusRightId(buttons[Math.min(buttons.length - 1, i + 1)].getId());
                buttons[i].setNextFocusUpId(buttons[i].getId());
                buttons[i].setNextFocusDownId(close.getId());
            }
            close.setNextFocusUpId(buttons[TvTheme.read(preferences)].getId());
            close.setOnFocusChangeListener((view, focused) -> {
                if (focused) close.setNextFocusUpId(buttons[preview[0]].getId());
            });
            refresh.run(); buttons[preview[0]].requestFocus();
        });
        dialog.show();
    }
    private static void style(Activity activity, Button button, boolean selected) {
        GradientDrawable background = new GradientDrawable(); background.setCornerRadius(dp(activity, 10));
        background.setColor(selected ? 0xff425bc8 : 0xff1b263b);
        background.setStroke(dp(activity, button.hasFocus() ? 3 : 1),
                button.hasFocus() ? 0xffeef3ff : selected ? 0xff8196ff : 0xff39445b);
        button.setBackground(background); button.setElevation(button.hasFocus() ? dp(activity, 5) : 0);
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
