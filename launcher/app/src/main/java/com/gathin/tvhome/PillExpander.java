package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * pills_expand_on_focus (config.xml): the top-bar pills sit as round icons and slide their label out while focused.
 * The width animates from the 42dp circle to the label's width and back; the pill clips to its own rounded outline,
 * so the label is revealed rather than squeezed. Labels that change while collapsed (the network name) are kept for
 * the next expand, and stay the pill's content description throughout.
 */
final class PillExpander {
    private static final long DURATION_MS = 200;

    private static final class State {
        CharSequence label;
        int endPadding, drawablePadding;
        TextUtils.TruncateAt ellipsize;
        ValueAnimator running;
    }

    private final Map<TextView, State> pills = new HashMap<>();
    private final int collapsed, pad;
    private final boolean animate;

    PillExpander(float density, boolean animate) {
        collapsed = Math.round(42 * density);
        pad = Math.round(10 * density);
        this.animate = animate;
    }

    void attach(TextView pill) {
        State s = new State();
        s.label = pill.getText();
        s.endPadding = pill.getPaddingEnd();
        s.drawablePadding = pill.getCompoundDrawablePadding();
        s.ellipsize = pill.getEllipsize();
        pills.put(pill, s);
        // The label must never wrap: mid-animation the pill is narrower than its text, and a multi-line TextView would
        // reflow it onto two or three lines on every frame. One line, clipped by the outline, and anchored to the start
        // so it slides out from behind the icon instead of re-centring as the width grows.
        pill.setSingleLine(true);
        pill.setMaxLines(1);
        pill.setHorizontallyScrolling(true);
        pill.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pill.setContentDescription(s.label);
        pill.setClipToOutline(true);
        View.OnFocusChangeListener previous = pill.getOnFocusChangeListener();
        pill.setOnFocusChangeListener((v, focused) -> {
            if (previous != null) previous.onFocusChange(v, focused);
            if (focused) expand(pill);
            else collapse(pill);
        });
        if (pill.isFocused()) expand(pill);
        else collapseNow(pill, s);
    }

    /** The pill's text was just set (the network name): keep it, and hide it again while collapsed. */
    void textChanged(TextView pill) {
        State s = pills.get(pill);
        CharSequence text = pill.getText();
        if (s == null || TextUtils.isEmpty(text)) return;
        s.label = text;
        pill.setContentDescription(text);
        if (!pill.isFocused() && s.running == null) pill.setText(null);
    }

    private void expand(TextView pill) {
        State s = pills.get(pill);
        if (s == null) return;
        cancel(s);
        int from = pill.getWidth() > 0 ? pill.getWidth() : collapsed;
        pill.setText(s.label);
        pill.setEllipsize(null);
        pill.setCompoundDrawablePadding(s.drawablePadding);
        pill.setPaddingRelative(pad, pill.getPaddingTop(), s.endPadding, pill.getPaddingBottom());
        pill.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(Math.max(1, pill.getHeight()), View.MeasureSpec.EXACTLY));
        int to = pill.getMeasuredWidth();
        run(pill, s, from, to, () -> {
            pill.setEllipsize(s.ellipsize);
            setWidth(pill, ViewGroup.LayoutParams.WRAP_CONTENT);
        });
    }

    private void collapse(TextView pill) {
        State s = pills.get(pill);
        if (s == null) return;
        cancel(s);
        pill.setEllipsize(null);
        run(pill, s, pill.getWidth(), collapsed, () -> collapseNow(pill, s));
    }

    private void collapseNow(TextView pill, State s) {
        pill.setText(null);
        pill.setEllipsize(s.ellipsize);
        pill.setCompoundDrawablePadding(0);
        pill.setPaddingRelative(pad, pill.getPaddingTop(), pad, pill.getPaddingBottom());
        setWidth(pill, collapsed);
    }

    private void run(TextView pill, State s, int from, int to, Runnable end) {
        if (!animate || from <= 0 || from == to) {
            end.run();
            return;
        }
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(DURATION_MS);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(x -> setWidth(pill, (Integer) x.getAnimatedValue()));
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (s.running == animation) s.running = null;
                if (!cancelled) end.run();
            }
        });
        s.running = a;
        a.start();
    }

    private static void cancel(State s) {
        if (s.running != null) {
            ValueAnimator a = s.running;
            s.running = null;
            a.cancel();
        }
    }

    private static void setWidth(View v, int width) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.width == width) return;
        lp.width = width;
        v.setLayoutParams(lp);
    }
}
