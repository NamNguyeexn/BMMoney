package com.example.bmmoney.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * NUT + NOI, KEO THA KHAP MAN HINH.
 *
 * <h3>Vi sao khong dung FloatingActionButton cua Material</h3>
 *
 * <p>FAB cua Material duoc thiet ke de NAM YEN mot cho: no tu quan ly le, tu an hien
 * theo thanh cuon, va nuot su kien cham de lo ripple. Muon keo tha thi phai chong lai
 * gan het nhung hanh vi do. Mot {@code FrameLayout} tron voi lop dieu khien rieng o
 * day it dong hon va khong co gi phai vo hieu hoa.</p>
 *
 * <h3>Ba thu tao cam giac "co that"</h3>
 *
 * <ol>
 *   <li><b>Do bong theo do cao.</b> Luc nghi nut o {@link #REST_ELEVATION_DP}, luc nhac
 *       len {@link #DRAG_ELEVATION_DP}. Bong tu no dam va loe rong ra - mat doc do la
 *       vat the roi khoi mat phang chu khong phai truot tren do.</li>
 *   <li><b>Nghieng 3D theo huong keo.</b> Nut xoay quanh truc X/Y theo VAN TOC ngon tay,
 *       khong theo vi tri. Nghieng theo vi tri se cho ra mot khoi cung do dan sang mot
 *       ben; nghieng theo van toc moi giong quan tinh - keo nhanh thi nga nhieu, dung
 *       lai thi tu dung thang day du ngon tay con dat tren man hinh.</li>
 *   <li><b>Dan hoi khi tha.</b> Nut bat ve canh gan nhat bang {@link SpringAnimation}
 *       chu khong phai mot duong truot deu. Lo xo mang theo van toc cuoi cung cua ngon
 *       tay, nen hat manh thi no lao nhanh va nay qua diem dung mot chut roi moi ve -
 *       dung cach mot vat co khoi luong hanh xu.</li>
 * </ol>
 *
 * <h3>Vi tri duoc nho lai</h3>
 *
 * <p>Luu duoi dang TI LE (0..1) chu khong phai pixel. Doi may, xoay ngang, hay chia
 * doi man hinh thi ti le van dat nut o dung goc cu; luu pixel se nem no ra ngoai vung
 * nhin thay.</p>
 */
public final class FloatingAddButton {

    /** Do cao luc nghi va luc dang nhac len, tinh bang dp. */
    private static final float REST_ELEVATION_DP = 14f;
    private static final float DRAG_ELEVATION_DP = 28f;

    /**
     * Mau bong do (chi co tac dung tu Android 9).
     *
     * <p>Bong mac dinh cua he thong la den thuan. Tren nen kem {@code #FEFAE0} cua app,
     * mot vung xam den trong nhu vet ban chu khong nhu bong. Nhuom bong theo mau xanh
     * o liu dam cua bang mau thi no hoa vao nen, va nghich ly la nho vay lai co the day
     * do dam len cao hon nhieu ma van sach.</p>
     */
    private static final int SHADOW_COLOR = 0xFF283618;

    /** Chua le nao bam sat canh man hinh, luon chua mot khoang nay. */
    private static final float EDGE_MARGIN_DP = 16f;

    /** Goc nghieng toi da khi keo. Qua 16 do la bat dau trong nhu bi vo. */
    private static final float MAX_TILT = 16f;

    /** Van toc (px/ms) tuong ung voi goc nghieng toi da. */
    private static final float TILT_VELOCITY_FULL = 2.2f;

    private static final float PRESS_SCALE = 0.92f;
    private static final float DRAG_SCALE = 1.12f;

    private static final String PREF_FILE = "fab_position";
    private static final String KEY_X = "x_ratio";
    private static final String KEY_Y = "y_ratio";

    private FloatingAddButton() {
    }

    /**
     * Gan hanh vi keo tha vao mot nut da co trong bo cuc.
     *
     * @param fab     nut can dieu khien, phai nam trong mot {@link ViewGroup} co
     *                {@code clipChildren=false} de bong va phan phong to khong bi cat
     * @param onClick chay khi CHAM (khong phai keo)
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attach(final View fab, final Runnable onClick) {
        if (fab == null) return;

        final Context context = fab.getContext();
        final float density = context.getResources().getDisplayMetrics().density;
        final float edge = EDGE_MARGIN_DP * density;
        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Khoang cach "may anh" mac dinh cua Android qua gan, nen mot goc xoay nho da
        // lam mat truoc phinh ra meo mo. Day ra xa thi phep chieu gan nhu truc giao,
        // do nghieng doc ra tinh te dung nhu vat the that.
        fab.setCameraDistance(density * 8000f);
        fab.setElevation(REST_ELEVATION_DP * density);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Bong "diem" la vet do bong den tu nguon sang, bong "moi truong" la vung toi
            // deu quanh vat. Dat ca hai, neu khong nua nay se van la mau den mac dinh va
            // hai lop bong danh nhau ve mau sac.
            fab.setOutlineSpotShadowColor(SHADOW_COLOR);
            fab.setOutlineAmbientShadowColor(SHADOW_COLOR);
        }

        final SpringAnimation springX = spring(fab, DynamicAnimation.TRANSLATION_X,
                SpringForce.STIFFNESS_LOW, 0.62f);
        final SpringAnimation springY = spring(fab, DynamicAnimation.TRANSLATION_Y,
                SpringForce.STIFFNESS_LOW, 0.62f);
        final SpringAnimation springTiltX = spring(fab, DynamicAnimation.ROTATION_X,
                SpringForce.STIFFNESS_MEDIUM, 0.45f);
        final SpringAnimation springTiltY = spring(fab, DynamicAnimation.ROTATION_Y,
                SpringForce.STIFFNESS_MEDIUM, 0.45f);

        final State state = new State();

        fab.setOnTouchListener((v, event) -> {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent == null) return false;

            float maxX = parent.getWidth() - parent.getPaddingLeft()
                    - parent.getPaddingRight() - v.getWidth();
            float maxY = parent.getHeight() - parent.getPaddingTop()
                    - parent.getPaddingBottom() - v.getHeight();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    springX.cancel();
                    springY.cancel();

                    state.downRawX = event.getRawX();
                    state.downRawY = event.getRawY();
                    state.startX = v.getTranslationX();
                    state.startY = v.getTranslationY();
                    state.lastRawX = event.getRawX();
                    state.lastRawY = event.getRawY();
                    state.lastMoveAt = event.getEventTime();
                    state.velocityX = 0f;
                    state.velocityY = 0f;
                    state.dragging = false;

                    // Lun xuong mot chut: phan hoi cham tuc thi, truoc khi biet day la
                    // cu cham hay cu keo.
                    v.animate().cancel();
                    v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                            .setDuration(110).start();
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - state.downRawX;
                    float dy = event.getRawY() - state.downRawY;

                    if (!state.dragging && Math.hypot(dx, dy) > slop) {
                        state.dragging = true;
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        lift(v, density);
                        // Cha me co the dang cuon (Trang chu nam trong ScrollView).
                        // Gianh lay chuoi su kien, neu khong nut se tuot khoi ngon tay
                        // ngay khi noi dung ben duoi bat dau cuon theo.
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    if (!state.dragging) return true;

                    v.setTranslationX(clamp(state.startX + dx, 0f, Math.max(0f, maxX)));
                    v.setTranslationY(clamp(state.startY + dy, 0f, Math.max(0f, maxY)));

                    long dt = event.getEventTime() - state.lastMoveAt;
                    if (dt > 0) {
                        // Lam muot van toc thay vi lay thang hieu hai diem: ngon tay tren
                        // man hinh cam ung luon rung nhe, doc thang se cho ra goc nghieng
                        // giat cuc bo.
                        float vx = (event.getRawX() - state.lastRawX) / dt;
                        float vy = (event.getRawY() - state.lastRawY) / dt;
                        state.velocityX = state.velocityX * 0.7f + vx * 0.3f;
                        state.velocityY = state.velocityY * 0.7f + vy * 0.3f;

                        // Keo sang phai thi canh phai chim xuong: xoay quanh truc doc
                        // theo chieu duong. Keo xuong thi canh duoi chim: xoay quanh truc
                        // ngang theo chieu am.
                        v.setRotationY(tilt(state.velocityX));
                        v.setRotationX(-tilt(state.velocityY));

                        state.lastRawX = event.getRawX();
                        state.lastRawY = event.getRawY();
                        state.lastMoveAt = event.getEventTime();
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    drop(v, density);
                    springTiltX.animateToFinalPosition(0f);
                    springTiltY.animateToFinalPosition(0f);

                    boolean wasDrag = state.dragging;
                    state.dragging = false;

                    if (!wasDrag) {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && onClick != null) {
                            v.performClick();
                            onClick.run();
                        }
                        return true;
                    }

                    // Ve canh trai hay canh phai: xet TAM cua nut, khong xet diem cham.
                    float centerX = v.getTranslationX() + v.getWidth() / 2f;
                    float target = centerX < (maxX + v.getWidth()) / 2f
                            ? edge
                            : Math.max(edge, maxX - edge);

                    // Lo xo nhan lai van toc cuoi cung (px/ms -> px/s), nen cu hat manh
                    // se bay nhanh va vot qua diem dung mot chut roi moi lang lai.
                    springX.setStartVelocity(state.velocityX * 1000f);
                    springX.animateToFinalPosition(target);

                    springY.setStartVelocity(state.velocityY * 1000f);
                    springY.animateToFinalPosition(
                            clamp(v.getTranslationY(), edge, Math.max(edge, maxY - edge)));

                    save(context, target, v.getTranslationY(), maxX, maxY);
                    return true;
                }

                default:
                    return false;
            }
        });

        // Dat nut vao cho ngay khi bo cuc co kich thuoc that. Lam trong
        // OnLayoutChangeListener chu khong phai post(): cach nay con chay lai khi xoay
        // may hay chia doi man hinh, luc do gioi han cu da khong con dung nua.
        fab.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (state.dragging) return;
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent == null || v.getWidth() == 0) return;

            float maxX = parent.getWidth() - parent.getPaddingLeft()
                    - parent.getPaddingRight() - v.getWidth();
            float maxY = parent.getHeight() - parent.getPaddingTop()
                    - parent.getPaddingBottom() - v.getHeight();
            if (maxX <= 0 || maxY <= 0) return;

            SharedPreferences prefs =
                    context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            // Mac dinh: goc duoi ben phai, cho quen thuoc cua mot nut them moi.
            float rx = prefs.getFloat(KEY_X, 1f);
            float ry = prefs.getFloat(KEY_Y, 1f);

            v.setTranslationX(clamp(rx * maxX, edge, Math.max(edge, maxX - edge)));
            v.setTranslationY(clamp(ry * maxY, edge, Math.max(edge, maxY - edge)));
        });
    }

    // ------------------------------------------------------------------- an / hien

    /**
     * An hoac hien nut.
     *
     * <p>Dung khi man dang mo CHINH LA man Them: giu nut + o do la moi mot lan bam nhu
     * hua hen mo them mot thu gi nua, trong khi thuc te khong co gi xay ra.</p>
     *
     * <p>An bang {@code GONE} chu khong phai {@code alpha = 0}: mot nut trong suot van
     * chan cham o dung cho no dung, nen nguoi dung se gap mot vung chet vo hinh tren
     * man Them ma khong hieu tai sao.</p>
     */
    public static void setShown(final View fab, boolean shown) {
        if (fab == null) return;
        boolean visible = fab.getVisibility() == View.VISIBLE;
        if (shown == visible) return;

        fab.animate().cancel();

        if (shown) {
            fab.setVisibility(View.VISIBLE);
            fab.setScaleX(0.4f);
            fab.setScaleY(0.4f);
            fab.setAlpha(0f);
            fab.setRotation(-90f);
            fab.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f).rotation(0f)
                    .setDuration(260)
                    .start();
        } else {
            fab.animate()
                    .scaleX(0.4f).scaleY(0.4f).alpha(0f).rotation(-90f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        fab.setVisibility(View.GONE);
                        // Tra lai nguyen trang, neu khong lan hien ke tiep se bat dau tu
                        // mot nut xoay leo va teo nho.
                        fab.setScaleX(1f);
                        fab.setScaleY(1f);
                        fab.setAlpha(1f);
                        fab.setRotation(0f);
                    })
                    .start();
        }
    }

    // --------------------------------------------------------------- nhac len / ha xuong

    /** Nhac nut khoi mat phang: to hon, bong dam hon. */
    private static void lift(View v, float density) {
        v.animate().cancel();
        v.animate()
                .scaleX(DRAG_SCALE).scaleY(DRAG_SCALE)
                .translationZ((DRAG_ELEVATION_DP - REST_ELEVATION_DP) * density)
                .setDuration(160)
                .start();
    }

    /** Dat nut tro lai mat phang. */
    private static void drop(View v, float density) {
        v.animate().cancel();
        v.animate()
                .scaleX(1f).scaleY(1f)
                .translationZ(0f)
                .setDuration(220)
                .start();
    }

    // ------------------------------------------------------------------------ tien ich

    private static SpringAnimation spring(View view,
                                          DynamicAnimation.ViewProperty property,
                                          float stiffness, float damping) {
        SpringAnimation animation = new SpringAnimation(view, property);
        animation.setSpring(new SpringForce()
                .setStiffness(stiffness)
                .setDampingRatio(damping));
        return animation;
    }

    /** Doi van toc ngon tay thanh goc nghieng, chan tren o {@link #MAX_TILT}. */
    private static float tilt(float velocity) {
        float ratio = clamp(velocity / TILT_VELOCITY_FULL, -1f, 1f);
        return ratio * MAX_TILT;
    }

    private static void save(Context context, float x, float y, float maxX, float maxY) {
        if (maxX <= 0 || maxY <= 0) return;
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_X, clamp(x / maxX, 0f, 1f))
                .putFloat(KEY_Y, clamp(y / maxY, 0f, 1f))
                .apply();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Trang thai cua mot cu cham, gom lai de dung duoc trong lambda. */
    private static final class State {
        boolean dragging;
        float downRawX, downRawY;
        float startX, startY;
        float lastRawX, lastRawY;
        long lastMoveAt;
        float velocityX, velocityY;
    }
}
