package com.mooncast.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

/** A deliberately quiet, overscan-safe ten-foot UI. Receiver diagnostics live elsewhere. */
public final class TvHomeView extends FrameLayout {
    public interface Listener {
        void onFraming();
        void onAdvanced();
        void onStopStart();
        void onHide();
    }

    private static final int INK = 0xff0e161c;
    private static final int CREAM = 0xfff4f3e9;
    private static final int MUTED = 0xff94a8ae;
    private static final int MINT = 0xff81dfcf;
    private static final int LIME = 0xffd7edb0;
    private final Map<View, Rect> positions = new LinkedHashMap<>();
    private final BrandView brand;
    private final TextView status, heading, instruction, receiver, trusted;
    private final CastArtwork artwork;
    private final ActionCard framing, advanced, hide;
    private final ReceiverButton toggle;
    private boolean receiving, playbackActive, compact, controlsEnabled = true;
    private String hint = "";

    public TvHomeView(Context context, Listener listener) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundColor(INK);

        brand = new BrandView(context);
        brand.setContentDescription(getResources().getString(R.string.home_brand));
        addView(brand);
        status = label(getResources().getString(R.string.home_waiting), 13, MUTED);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setCompoundDrawablePadding(dp(7));
        addView(status);
        heading = label(getResources().getString(R.string.home_hero), 48, CREAM);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLetterSpacing(-0.035f);
        heading.setLineSpacing(0, 0.97f);
        addView(heading);
        instruction = label(getResources().getString(R.string.home_instructions), 15, MUTED);
        instruction.setLineSpacing(dp(3), 1);
        instruction.setMaxLines(2);
        instruction.setEllipsize(TextUtils.TruncateAt.END);
        addView(instruction);
        receiver = label("MoonCast TV", 29, CREAM);
        receiver.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        receiver.setSingleLine(true);
        receiver.setEllipsize(TextUtils.TruncateAt.END);
        receiver.setLetterSpacing(-0.02f);
        addView(receiver);
        artwork = new CastArtwork(context);
        artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(artwork);

        framing = new ActionCard(context, 0, R.string.home_framing, R.string.home_framing_detail);
        advanced = new ActionCard(context, 1, R.string.home_advanced, R.string.home_advanced_detail);
        hide = new ActionCard(context, 2, R.string.home_hide, R.string.home_hide_detail);
        framing.setOnClickListener(v -> listener.onFraming());
        advanced.setOnClickListener(v -> listener.onAdvanced());
        hide.setOnClickListener(v -> listener.onHide());
        addView(framing); addView(advanced); addView(hide);
        trusted = label(getResources().getString(R.string.home_trusted), 11, 0xff778e95);
        trusted.setSingleLine(true);
        trusted.setEllipsize(TextUtils.TruncateAt.END);
        trusted.setGravity(Gravity.CENTER_VERTICAL);
        addView(trusted);
        toggle = new ReceiverButton(context);
        toggle.setOnClickListener(v -> listener.onStopStart());
        addView(toggle);
        View[] targets = {framing, advanced, hide, toggle};
        for (View target : targets) target.setId(View.generateViewId());
        framing.setNextFocusRightId(advanced.getId());
        advanced.setNextFocusLeftId(framing.getId());
        advanced.setNextFocusRightId(hide.getId());
        hide.setNextFocusLeftId(advanced.getId());
        for (View target : new View[]{framing, advanced, hide}) {
            target.setNextFocusDownId(toggle.getId());
            target.setNextFocusUpId(target.getId());
        }
        framing.setNextFocusLeftId(framing.getId());
        hide.setNextFocusRightId(hide.getId());
        toggle.setNextFocusUpId(hide.getId());
        toggle.setNextFocusLeftId(framing.getId());
        toggle.setNextFocusRightId(toggle.getId());
        toggle.setNextFocusDownId(toggle.getId());
        setReceiving(false);
        setStatus(getResources().getString(R.string.home_waiting), false);
    }

    public void setReceiverName(String value) {
        receiver.setText(value);
        receiver.setContentDescription(getResources().getString(R.string.home_receiver_accessibility, value));
    }

    public void setStatus(String value, boolean isReady) {
        status.setText("●  " + value);
        status.setTextColor(isReady ? MINT : MUTED);
        artwork.setReady(isReady);
    }

    /** Receiving is the requested receiver state, not an assertion that video is playing. */
    public void setReceiving(boolean value) {
        receiving = value;
        toggle.setText(value ? R.string.home_stop : R.string.home_start);
        toggle.updateStyle();
    }

    public void setModeLabel(String value) { framing.setSubtitle(value); }
    public void setHint(String value) {
        hint = value == null ? "" : value.trim();
        updateInstruction();
    }
    public void setPlaybackActive(boolean value) {
        playbackActive = value;
        hide.setTitle(getResources().getString(value ? R.string.home_return_playback : R.string.home_hide));
        hide.setSubtitle(getResources().getString(value ? R.string.home_hide_active : R.string.home_hide_detail));
        artwork.setPlaybackActive(value);
    }
    public void setControlsEnabled(boolean value) {
        controlsEnabled = value;
        for (View view : new View[]{framing, advanced, hide, toggle}) {
            view.setEnabled(value);
            view.setAlpha(value ? 1f : 0.45f);
        }
    }
    public void requestPrimaryFocus() {
        if (controlsEnabled) (playbackActive ? hide : receiving ? framing : toggle).requestFocus();
    }

    private void updateInstruction() {
        instruction.setText(hint.isEmpty() ? getResources().getString(compact ? R.string.home_instructions_compact : R.string.home_instructions) : hint);
        instruction.setTextColor(hint.isEmpty() ? MUTED : 0xffe0c99a);
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setFocusable(false);
        view.setFontFeatureSettings("kern");
        return view;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float toDp(int value) { return value / getResources().getDisplayMetrics().density; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        positions.clear();
        float width = toDp(w), height = toDp(h);
        // Android TV's standard 48 dp horizontal / vertical safe area, including compact TVs.
        float inset = 48, cw = Math.max(1, width - inset * 2), ch = Math.max(1, height - inset * 2);
        compact = cw < 700 || ch < 430;
        float headerH = compact ? 24 : 28;
        float headerGap = compact ? 12 : 26;
        float cardH = compact ? 58 : 80;
        float cardGap = compact ? 10 : 24;
        float footerGap = compact ? 8 : 14;
        float footerH = 32;
        float heroH = ch - headerH - headerGap - cardGap - cardH - footerGap - footerH;
        float heroY = inset + headerH + headerGap;
        float cardY = heroY + heroH + cardGap;
        float footerY = cardY + cardH + footerGap;
        float leftW = cw * 0.555f;
        float artLeft = inset + leftW + (compact ? 4 : 12);
        float titleH = compact ? 37 : 108;
        float instructionH = compact ? 32 : 43;
        float instructionY = heroY + titleH + (compact ? 4 : 17);
        float receiverY = instructionY + instructionH + (compact ? 7 : 12);
        heading.setText(compact ? R.string.home_hero_compact : R.string.home_hero);
        heading.setTextSize(compact ? 29 : 47);
        instruction.setTextSize(compact ? 12 : 15);
        instruction.setLineSpacing(dp(compact ? 2 : 3), 1);
        receiver.setTextSize(compact ? 22 : 29);
        status.setTextSize(compact ? 11 : 13);
        trusted.setTextSize(compact ? 10 : 11);
        updateInstruction();
        place(brand, inset, inset, compact ? 149 : 171, headerH);
        place(status, inset + cw * 0.53f, inset, cw * 0.47f, headerH);
        place(heading, inset, heroY, leftW, titleH);
        place(instruction, inset, instructionY, leftW, instructionH);
        place(receiver, inset, receiverY, leftW, compact ? 30 : 38);
        place(artwork, artLeft, heroY - (compact ? 5 : 9), width - inset - artLeft, heroH + (compact ? 8 : 12));
        float gap = compact ? 10 : 14;
        float itemW = (cw - gap * 2) / 3;
        framing.setCompact(compact); advanced.setCompact(compact); hide.setCompact(compact);
        place(framing, inset, cardY, itemW, cardH);
        place(advanced, inset + itemW + gap, cardY, itemW, cardH);
        place(hide, inset + (itemW + gap) * 2, cardY, itemW, cardH);
        float toggleW = compact ? 116 : 139;
        toggle.setTextSize(compact ? 11 : 12);
        place(trusted, inset + 1, footerY, cw - toggleW - 20, footerH);
        place(toggle, inset + cw - toggleW, footerY, toggleW, footerH);
    }
    private void place(View view, float x, float y, float w, float h) {
        Rect rect = new Rect(dp(x), dp(y), dp(x + w), dp(y + h));
        positions.put(view, rect);
        view.measure(MeasureSpec.makeMeasureSpec(Math.max(0, rect.width()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(0, rect.height()), MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (Map.Entry<View, Rect> entry : positions.entrySet()) {
            Rect p = entry.getValue(); entry.getKey().layout(p.left, p.top, p.right, p.bottom);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(getWidth() * .80f, getHeight() * .37f, getWidth() * .52f,
                new int[]{0xff152a30, 0xff111e25, INK}, new float[]{0, .52f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        p.setShader(null); p.setColor(0xff263339); p.setStrokeWidth(dp(.6f));
        Rect footer = positions.get(trusted);
        if (footer != null) canvas.drawLine(dp(48), footer.top - dp(compact ? 4 : 7), getWidth() - dp(48), footer.top - dp(compact ? 4 : 7), p);
    }

    private final class BrandView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BrandView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), unit = h / 28f;
            canvas.save(); canvas.scale(unit, unit);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setColor(MINT);
            canvas.drawRoundRect(1, 4, 25, 21, 5, 5, p);
            p.setStyle(Paint.Style.FILL); p.setColor(INK);
            canvas.drawRect(0, 14, 12, 25, p);
            p.setColor(MINT); canvas.drawCircle(3, 21, 2.1f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(1.8f);
            canvas.drawArc(-4, 14, 10, 28, -90, 90, false, p);
            canvas.drawArc(-9, 9, 15, 33, -90, 90, false, p);
            p.setStyle(Paint.Style.FILL); p.setColor(CREAM); p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(20); canvas.drawText("MoonCast", 37, 21, p);
            float tagX = 37 + p.measureText("MoonCast") + 9;
            p.setColor(0xff273a40); canvas.drawRoundRect(tagX, 8, tagX + 22, 22, 4, 4, p);
            p.setColor(0xffb3c8ce); p.setTextSize(8.5f); canvas.drawText("TV", tagX + 5.6f, 18.4f, p);
            canvas.restore();
        }
    }

    private final class ActionCard extends FrameLayout {
        private final TextView title, subtitle;
        private final IconView icon;
        private boolean small;
        ActionCard(Context context, int kind, int titleResource, int subtitleResource) {
            super(context);
            setFocusable(true); setClickable(true); setClipChildren(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            title = label(getResources().getString(titleResource), 17, CREAM);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            subtitle = label(getResources().getString(subtitleResource), 11, MUTED);
            subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END);
            icon = new IconView(context, kind);
            for (View child : new View[]{icon, title, subtitle}) { child.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(child); }
            setSubtitle(getResources().getString(subtitleResource));
            updateStyle(false);
            setOnFocusChangeListener((v, focus) -> {
                updateStyle(focus);
                animate().cancel();
                animate().scaleX(focus ? 1.025f : 1f).scaleY(focus ? 1.025f : 1f)
                        .translationZ(focus ? dp(6) : 0).setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
            });
        }
        void setTitle(String value) {
            title.setText(value);
            setContentDescription(value + ", " + subtitle.getText());
        }
        void setSubtitle(String value) {
            subtitle.setText(value);
            setContentDescription(title.getText() + ", " + value);
        }
        void setCompact(boolean value) {
            boolean changed = small != value;
            small = value; title.setTextSize(value ? 14 : 17); subtitle.setTextSize(value ? 10 : 11);
            if (changed) updateStyle(hasFocus());
        }
        private void updateStyle(boolean focus) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(focus ? LIME : 0xff18252c);
            background.setCornerRadius(dp(small ? 11 : 14));
            background.setStroke(dp(1), focus ? 0xffe7f6cd : 0xff2a3b42);
            setBackground(background);
            title.setTextColor(focus ? 0xff1b2d25 : CREAM);
            subtitle.setTextColor(focus ? 0xff48634e : MUTED);
            icon.color = focus ? 0xff244a39 : 0xff9abbbd; icon.invalidate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec); setMeasuredDimension(w, h);
            int x = dp(small ? 42 : 57), end = dp(small ? 10 : 14);
            icon.measure(MeasureSpec.makeMeasureSpec(dp(small ? 19 : 24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(small ? 19 : 24), MeasureSpec.EXACTLY));
            title.measure(MeasureSpec.makeMeasureSpec(Math.max(0, w - x - end), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(small ? 21 : 24), MeasureSpec.EXACTLY));
            subtitle.measure(MeasureSpec.makeMeasureSpec(Math.max(0, w - x - end), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(small ? 15 : 17), MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = dp(small ? 42 : 57), top = dp(small ? 12 : 19);
            int ix = dp(small ? 14 : 19), iy = dp(small ? 18 : 27);
            icon.layout(ix, iy, ix + icon.getMeasuredWidth(), iy + icon.getMeasuredHeight());
            title.layout(x, top, x + title.getMeasuredWidth(), top + title.getMeasuredHeight());
            int subTop = top + dp(small ? 22 : 28);
            subtitle.layout(x, subTop, x + subtitle.getMeasuredWidth(), subTop + subtitle.getMeasuredHeight());
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setClassName(Button.class.getName());
        }
        @Override protected void onDetachedFromWindow() { animate().cancel(); super.onDetachedFromWindow(); }
    }

    private final class ReceiverButton extends TextView {
        ReceiverButton(Context context) {
            super(context); setFocusable(true); setClickable(true); setGravity(Gravity.CENTER);
            setIncludeFontPadding(false); setSingleLine(true);
            setOnFocusChangeListener((v, focus) -> updateStyle());
        }
        void updateStyle() {
            GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(8));
            bg.setColor(hasFocus() ? 0xff263d40 : Color.TRANSPARENT);
            bg.setStroke(dp(1), hasFocus() ? MINT : Color.TRANSPARENT);
            setBackground(bg); setTextColor(hasFocus() ? CREAM : receiving ? 0xff90a5ab : MINT);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setClassName(Button.class.getName());
        }
    }

    private final class IconView extends View {
        final int kind;
        int color = MINT;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(Context context, int kind) { super(context); this.kind = kind; }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save(); canvas.scale(getWidth() / 24f, getHeight() / 24f);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if (kind == 0) {
                canvas.drawLine(3, 9, 3, 3, p); canvas.drawLine(3, 3, 9, 3, p);
                canvas.drawLine(15, 3, 21, 3, p); canvas.drawLine(21, 3, 21, 9, p);
                canvas.drawLine(3, 15, 3, 21, p); canvas.drawLine(3, 21, 9, 21, p);
                canvas.drawLine(15, 21, 21, 21, p); canvas.drawLine(21, 21, 21, 15, p);
                canvas.drawRoundRect(7, 8, 17, 16, 1.8f, 1.8f, p);
            } else if (kind == 1) {
                for (int y : new int[]{5, 12, 19}) canvas.drawLine(3, y, 21, y, p);
                p.setStyle(Paint.Style.FILL); p.setColor(((View) getParent()).hasFocus() ? LIME : 0xff18252c);
                canvas.drawCircle(8, 5, 3, p); canvas.drawCircle(16, 12, 3, p); canvas.drawCircle(10, 19, 3, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(color);
                canvas.drawCircle(8, 5, 2.5f, p); canvas.drawCircle(16, 12, 2.5f, p); canvas.drawCircle(10, 19, 2.5f, p);
            } else {
                canvas.drawRoundRect(2, 4, 22, 19, 3, 3, p);
                canvas.drawLine(8, 11.5f, 16, 11.5f, p);
                canvas.drawLine(13, 8.5f, 16, 11.5f, p); canvas.drawLine(13, 14.5f, 16, 11.5f, p);
            }
            canvas.restore();
        }
    }

    /** Resolution-independent ambient artwork, drawn from geometry rather than an asset. */
    private final class CastArtwork extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean active, online;
        CastArtwork(Context context) { super(context); }
        void setReady(boolean value) { online = value; invalidate(); }
        void setPlaybackActive(boolean value) { active = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float scale = Math.min(getWidth() / 360f, getHeight() / 250f);
            canvas.save(); canvas.translate((getWidth() - 360 * scale) / 2, (getHeight() - 250 * scale) / 2); canvas.scale(scale, scale);
            // Soft orbital glow and two quiet ellipses give the picture room to breathe.
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(186, 123, 170, new int[]{0x253f9383, 0x062d786e, 0x002d786e}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(186, 123, 170, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.75f); p.setColor(0xff294047);
            canvas.save(); canvas.rotate(-18, 183, 132); canvas.drawOval(15, 25, 350, 229, p); canvas.restore();
            p.setColor(0xff20383e); canvas.save(); canvas.rotate(18, 183, 130); canvas.drawOval(43, -2, 329, 254, p); canvas.restore();
            p.setStyle(Paint.Style.FILL); p.setColor(LIME); canvas.drawCircle(309, 49, 3, p);
            p.setColor(0xff7aafaa); canvas.drawCircle(30, 159, 2, p);
            // A single luminous display, subtly lifted from its shadow.
            p.setShader(new RadialGradient(180, 226, 138, new int[]{0x6002090c, 0x0002090c}, null, Shader.TileMode.CLAMP));
            canvas.save(); canvas.scale(1, .19f); canvas.drawCircle(180, 1150, 138, p); canvas.restore(); p.setShader(null);
            p.setColor(0xff101b23); canvas.drawRoundRect(25, 44, 327, 218, 13, 13, p);
            p.setShader(new LinearGradient(23, 30, 314, 210, new int[]{0xff648f93, 0xff29464e, 0xff456267}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(22, 36, 324, 212, 13, 13, p); p.setShader(null);
            p.setColor(0xff142a32); canvas.drawRoundRect(24, 38, 322, 209, 11, 11, p);
            canvas.save(); path.reset(); path.addRoundRect(new RectF(31, 45, 315, 198), 6, 6, Path.Direction.CW); canvas.clipPath(path);
            p.setShader(new LinearGradient(55, 33, 285, 197, new int[]{0xff153740, 0xff347774, 0xffa4b883}, new float[]{0, .57f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(31, 45, 315, 198, p); p.setShader(null);
            p.setShader(new RadialGradient(250, 58, 124, new int[]{0xffcbdcb0, 0x2084b69b, 0x00000000}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(250, 58, 124, p); p.setShader(null);
            p.setColor(0xffdfe8b6); canvas.drawCircle(253, 77, 22, p);
            // The wide, flowing ridgeline mirrors the screen's cinematic proportions.
            path.reset(); path.moveTo(14, 146); path.cubicTo(93, 116, 113, 182, 188, 137); path.cubicTo(243, 105, 286, 121, 333, 104); path.lineTo(333, 207); path.lineTo(14, 207); path.close();
            p.setShader(new LinearGradient(165, 108, 165, 199, 0xff6ea395, 0xff224f53, Shader.TileMode.CLAMP)); canvas.drawPath(path, p); p.setShader(null);
            path.reset(); path.moveTo(17, 188); path.cubicTo(79, 144, 139, 132, 200, 163); path.cubicTo(243, 185, 284, 152, 338, 158); path.lineTo(338, 213); path.lineTo(17, 213); path.close();
            p.setShader(new LinearGradient(168, 151, 168, 207, 0xff224e51, 0xff153239, Shader.TileMode.CLAMP)); canvas.drawPath(path, p); p.setShader(null);
            canvas.restore();
            p.setColor(online ? MINT : 0xff59767c); canvas.drawCircle(174, 204, 1.2f, p);
            // Phone with matching art: obvious casting, without protocol logos or tutorial clutter.
            canvas.save(); canvas.rotate(9, 282, 181);
            p.setColor(0x65040c10); canvas.drawRoundRect(258, 123, 322, 232, 11, 11, p);
            p.setShader(new LinearGradient(250, 117, 311, 226, 0xff8bb7ad, 0xff466465, Shader.TileMode.CLAMP)); canvas.drawRoundRect(252, 118, 312, 229, 11, 11, p); p.setShader(null);
            p.setColor(0xff10252d); canvas.drawRoundRect(254, 120, 310, 227, 9, 9, p);
            p.setShader(new LinearGradient(258, 130, 304, 219, 0xff417f79, 0xff163c45, Shader.TileMode.CLAMP)); canvas.drawRoundRect(259, 129, 305, 216, 5, 5, p); p.setShader(null);
            p.setColor(0xffd3dfb1); canvas.drawCircle(292, 146, 8, p);
            path.reset(); path.moveTo(259, 180); path.cubicTo(281, 163, 285, 188, 305, 173); path.lineTo(305, 216); path.lineTo(259, 216); path.close(); p.setColor(0xff2d5b5b); canvas.drawPath(path, p);
            p.setColor(0xff93b4ae); canvas.drawRoundRect(275, 123, 289, 125, 1, 1, p); canvas.drawRoundRect(275, 220, 290, 222, 1, 1, p);
            canvas.restore();
            // Small floating connection mark.
            p.setColor(0xff203f43); canvas.drawCircle(49, 201, 20, p);
            p.setStyle(Paint.Style.STROKE); p.setColor(online ? LIME : 0xff8fa8a6); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(39, 191, 59, 211, 220, 100, false, p); canvas.drawArc(43, 195, 55, 207, 220, 100, false, p);
            p.setStyle(Paint.Style.FILL); canvas.drawCircle(49, 204, 1.8f, p);
            if (active) { p.setColor(MINT); canvas.drawCircle(316, 38, 4, p); }
            canvas.restore();
        }
    }
}
