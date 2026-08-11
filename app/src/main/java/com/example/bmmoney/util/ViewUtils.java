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

    // ------------------------------------------------------------- tim view an toan

    /**
     * Ban va 11/08: gan su kien / doi hien thi ma KHONG so view bi thieu.
     *
     * <p><b>Vi sao can:</b> code cu noi chuoi thang tu findViewById sang setOnClickListener. Chi can mot lan ai do comment mot widget trong file layout
     * la {@code findViewById} tra ve null va app sap ngay khi mo man (NullPointerException).
     * Dung dieu nay da tung xay ra voi {@code container_cats} hom 03/08.</p>
     *
     * <p>Cac ham duoi day tra ve am tham khi khong tim thay view: mat mot nut bam
     * van hon la sap ca app.</p>
     */
    public static View find(View root, int id) {
        return root == null ? null : root.findViewById(id);
    }

    /** Gan su kien bam, bo qua neu view khong ton tai. */
    public static void onClick(View root, int id, View.OnClickListener listener) {
        View view = find(root, id);
        if (view != null) view.setOnClickListener(listener);
    }

    /** Gan su kien bam giu, bo qua neu view khong ton tai. */
    public static void onLongClick(View root, int id, View.OnLongClickListener listener) {
        View view = find(root, id);
        if (view != null) view.setOnLongClickListener(listener);
    }

    /** Doi trang thai hien thi, bo qua neu view khong ton tai. */
    public static void setVisibility(View root, int id, int visibility) {
        View view = find(root, id);
        if (view != null) view.setVisibility(visibility);
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

    /** Dat ngay do rong thanh tien trinh, khong chay animation (dung khi reload lai). */
    public static void setBar(final View bar, final float percent) {
        if (bar == null) return;
        final View track = (View) bar.getParent();
        track.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams lp = bar.getLayoutParams();
                lp.width = (int) (track.getWidth() * Math.max(0f, Math.min(100f, percent)) / 100f);
                bar.setLayoutParams(lp);
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
