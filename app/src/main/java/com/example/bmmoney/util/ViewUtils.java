package com.example.bmmoney.util;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Tien ich hieu ung, mo phong animation cua ban thiet ke web.
 * Cac animation vo han tra ve ValueAnimator de fragment co the cancel() khi bi huy.
 */
public final class ViewUtils {

    private ViewUtils() {
    }

    /** Cho thanh tien trinh chay tu 0 den percent (0..100) cua chieu rong track. */
    public static void animateBar(final View bar, final float percent, final long duration, final long delay) {
        if (bar == null) return;
        final View track = (View) bar.getParent();
        track.post(new Runnable() {
            @Override
            public void run() {
                final int target = (int) (track.getWidth() * Math.max(0f, Math.min(100f, percent)) / 100f);
                ValueAnimator animator = ValueAnimator.ofInt(0, target);
                animator.setDuration(duration);
                animator.setStartDelay(delay);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        ViewGroup.LayoutParams lp = bar.getLayoutParams();
                        lp.width = (int) animation.getAnimatedValue();
                        bar.setLayoutParams(lp);
                    }
                });
                animator.start();
            }
        });
    }

    public static void fadeSlideIn(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(16f);
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420).start();
    }

    /** Animation noi len xuong vo han. NHO cancel() trong onDestroyView(). */
    public static ValueAnimator floatForever(final View view, final float distanceDp) {
        if (view == null) return null;
        final float d = distanceDp * view.getResources().getDisplayMetrics().density;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, -d, 0f);
        animator.setDuration(2500);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                view.setTranslationY((Float) animation.getAnimatedValue());
            }
        });
        animator.start();
        return animator;
    }

    /** Animation lac nhe vo han. NHO cancel() trong onDestroyView(). */
    public static ValueAnimator wiggleForever(final View view) {
        if (view == null) return null;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 10f, -10f, 0f);
        animator.setDuration(3000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                view.setRotation((Float) animation.getAnimatedValue());
            }
        });
        animator.start();
        return animator;
    }
}
