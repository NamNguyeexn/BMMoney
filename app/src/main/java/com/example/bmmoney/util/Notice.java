package com.example.bmmoney.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;

import java.lang.ref.WeakReference;

/**
 * Thong bao noi theo dung bang mau cua app (kem – o liu – nau dat), thay cho Toast
 * xam den cua he thong.
 *
 * <p>Cach dung:</p>
 * <pre>
 * Notice.info(root, "Đang sao lưu…");                       // hien roi tu tat
 * Notice.Handle h = Notice.loading(root, "Đang sao lưu…");   // hien va giu lai
 * h.success("Đã sao lưu 128 giao dịch");                     // doi thanh trang thai xong
 * h.error("Sao lưu thất bại", "không có mạng");             // hoac bao loi
 * </pre>
 *
 * <p>Moi luc chi co MOT thong bao tren man hinh: thong bao moi se thay cho cai cu,
 * nen bam sao luu lien tuc cung khong bi chong toast nhu truoc.</p>
 */
public final class Notice {

    public static final long SHORT = 2400L;
    public static final long LONG = 4200L;
    /** Giu lai cho den khi goi dismiss()/success()/error(). */
    public static final long KEEP = 0L;

    public enum Kind {INFO, SUCCESS, ERROR, LOADING}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    @Nullable
    private static WeakReference<Handle> current;

    private Notice() {
    }

    // ------------------------------------------------------------- loi goi ngan
    public static Handle info(@Nullable View anchor, String message) {
        return show(anchor, Kind.INFO, message, null, SHORT);
    }

    public static Handle success(@Nullable View anchor, String message) {
        return show(anchor, Kind.SUCCESS, message, null, SHORT);
    }

    public static Handle error(@Nullable View anchor, String message, @Nullable String detail) {
        return show(anchor, Kind.ERROR, message, detail, LONG);
    }

    public static Handle loading(@Nullable View anchor, String message) {
        return show(anchor, Kind.LOADING, message, null, KEEP);
    }

    // ------------------------------------------------------------- hien thi
    public static Handle show(@Nullable View anchor, Kind kind, String message,
                              @Nullable String detail, long duration) {
        Handle handle = new Handle(anchor);
        handle.apply(kind, message, detail, duration);
        return handle;
    }

    private static void replaceCurrent(Handle handle) {
        Handle old = current == null ? null : current.get();
        if (old != null && old != handle) old.dismiss();
        current = new WeakReference<>(handle);
    }

    @Nullable
    private static ViewGroup host(@Nullable View anchor) {
        if (anchor == null) return null;
        Activity activity = activityOf(anchor.getContext());
        if (activity == null || activity.isFinishing()) return null;
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }

    @Nullable
    private static Activity activityOf(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Mot the thong bao dang song, co the doi noi dung hoac tat di. */
    public static final class Handle {
        @Nullable
        private final ViewGroup host;
        @Nullable
        private View card;
        @Nullable
        private final Context context;
        private final Runnable autoDismiss = new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        };

        private Handle(@Nullable View anchor) {
            this.host = host(anchor);
            this.context = anchor == null ? null : anchor.getContext();
        }

        void apply(Kind kind, String message, @Nullable String detail, long duration) {
            if (host == null || context == null) {
                // Khong bam duoc vao man hinh nao -> quay ve Toast cho chac
                if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                return;
            }
            replaceCurrent(this);

            if (card == null) {
                card = LayoutInflater.from(context).inflate(R.layout.view_notice, host, false);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.BOTTOM;
                lp.leftMargin = dp(context, 16);
                lp.rightMargin = dp(context, 16);
                lp.bottomMargin = dp(context, 96); // nam tren thanh dieu huong duoi
                card.setLayoutParams(lp);
                card.setAlpha(0f);
                card.setTranslationY(dp(context, 24));
                host.addView(card);
                card.animate().alpha(1f).translationY(0f).setDuration(220).start();
            }

            bind(kind, message, detail);

            MAIN.removeCallbacks(autoDismiss);
            if (duration > 0) MAIN.postDelayed(autoDismiss, duration);
        }

        private void bind(Kind kind, String message, @Nullable String detail) {
            if (card == null || context == null) return;

            final int accent;
            final String emoji;
            switch (kind) {
                case SUCCESS:
                    accent = ContextCompat.getColor(context, R.color.olive);
                    emoji = "\u2713";
                    break;
                case ERROR:
                    accent = ContextCompat.getColor(context, R.color.burnt);
                    emoji = "!";
                    break;
                case LOADING:
                    accent = ContextCompat.getColor(context, R.color.sandy);
                    emoji = "";
                    break;
                case INFO:
                default:
                    accent = ContextCompat.getColor(context, R.color.dark_green);
                    emoji = "\u2022";
                    break;
            }

            TextView title = card.findViewById(R.id.tv_notice_title);
            TextView sub = card.findViewById(R.id.tv_notice_detail);
            TextView icon = card.findViewById(R.id.tv_notice_icon);
            ProgressBar spinner = card.findViewById(R.id.pb_notice);

            title.setText(message);
            if (detail == null || detail.trim().isEmpty()) {
                sub.setVisibility(View.GONE);
            } else {
                sub.setVisibility(View.VISIBLE);
                sub.setText(detail);
            }

            boolean busy = kind == Kind.LOADING;
            spinner.setVisibility(busy ? View.VISIBLE : View.GONE);
            icon.setVisibility(busy ? View.GONE : View.VISIBLE);
            icon.setText(emoji);
            spinner.getIndeterminateDrawable().setColorFilter(accent, PorterDuff.Mode.SRC_IN);

            tint(card.findViewById(R.id.box_notice_icon), accent, true);
            stroke(card, accent);
        }

        /** To mau vong tron sau bieu tuong theo trang thai. */
        private void tint(@Nullable View view, int color, boolean soft) {
            if (view == null) return;
            Drawable background = view.getBackground();
            if (background == null) return;
            background = background.mutate();
            background.setColorFilter(soft ? softer(color) : color, PorterDuff.Mode.SRC_IN);
            view.setBackground(background);
        }

        /** Vien the doi mau theo trang thai, phan con lai giu nen kem cua app. */
        private void stroke(View view, int color) {
            Drawable background = view.getBackground();
            if (background instanceof GradientDrawable && context != null) {
                GradientDrawable shape = (GradientDrawable) background.mutate();
                shape.setStroke(dp(context, 1.5f), color);
                view.setBackground(shape);
            }
        }

        private static int softer(int color) {
            return (color & 0x00FFFFFF) | 0x33000000; // giu mau, giam do dam
        }

        public void success(String message) {
            apply(Kind.SUCCESS, message, null, SHORT);
        }

        public void error(String message, @Nullable String detail) {
            apply(Kind.ERROR, message, detail, LONG);
        }

        public void info(String message) {
            apply(Kind.INFO, message, null, SHORT);
        }

        public void dismiss() {
            MAIN.removeCallbacks(autoDismiss);
            final View view = card;
            card = null;
            if (view == null || host == null || context == null) return;
            view.animate().alpha(0f).translationY(dp(context, 16)).setDuration(180)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (view.getParent() instanceof ViewGroup) {
                                ((ViewGroup) view.getParent()).removeView(view);
                            }
                        }
                    }).start();
        }
    }
}
