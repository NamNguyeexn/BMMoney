package com.example.bmmoney.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;

/**
 * Dau hoi tron nho dat canh mot nhan: bam vao thi hien chu thich ngan.
 *
 * <h3>Vi sao ve vong tron bang code chu khong bang drawable</h3>
 *
 * <p>Vien duoc ve bang {@link GradientDrawable} lay mau tu chinh mau chu cua o dau
 * hoi. Nho vay cung mot o dung duoc tren the kem sang lan the nen toi ma khong can
 * them file drawable nao, cung khong phai nho hai bo mau sang/toi.</p>
 *
 * <h3>Vung bam</h3>
 *
 * <p>O chi 18dp - nho hon nguong 48dp khuyen dung. Thay vi phong to o len cho xau
 * bo cuc, vung bam duoc noi rong bang {@link TouchDelegate} nen ngon tay van trung
 * de dang.</p>
 *
 * <p>Luu y: moi ViewGroup chi giu duoc MOT TouchDelegate. Neu sau nay can hai o dau
 * hoi trong cung mot khung cha thi phai boc moi o trong mot khung rieng.</p>
 */
public final class HelpTip {

    private static final int MAX_WIDTH_DP = 260;
    private static final float TEXT_SP = 14f;
    private static final int PADDING_DP = 14;
    private static final int TOUCH_EXTRA_DP = 12;

    private HelpTip() {
    }

    /**
     * Gan chu thich vao o dau hoi.
     *
     * @param root    khung chua o dau hoi, co the null
     * @param anchorId id cua o dau hoi trong bo cuc
     * @param message noi dung chu thich. De trong thi o dau hoi bi an di.
     */
    public static void attach(@Nullable View root, @IdRes int anchorId, @Nullable String message) {
        if (root == null) return;
        final View anchor = root.findViewById(anchorId);
        if (anchor == null) return;

        if (message == null || message.trim().isEmpty()) {
            anchor.setVisibility(View.GONE);
            return;
        }

        anchor.setVisibility(View.VISIBLE);
        style(anchor);
        expandTouchArea(anchor);

        final String text = message;
        anchor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(v, text);
            }
        });
    }

    /** Ve dau hoi va vong tron bao quanh. */
    private static void style(View anchor) {
        if (!(anchor instanceof TextView)) return;
        TextView badge = (TextView) anchor;

        // Dat chu o day chu KHONG dat trong XML: gia tri bat dau bang "?" bi trinh
        // bien dich tai nguyen hieu la tham chieu thuoc tinh theme va bao loi
        // "invalid resource reference: missing resource type".
        badge.setText("?");
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setPadding(0, 0, 0, 0);

        float density = badge.getResources().getDisplayMetrics().density;
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Math.max(1, (int) (1.2f * density)), badge.getCurrentTextColor());
        badge.setBackground(ring);
    }

    /** Noi rong vung bam cua o dau hoi ra moi phia. */
    private static void expandTouchArea(final View anchor) {
        final ViewGroup parent = anchor.getParent() instanceof ViewGroup
                ? (ViewGroup) anchor.getParent() : null;
        if (parent == null) return;

        parent.post(new Runnable() {
            @Override
            public void run() {
                Rect area = new Rect();
                anchor.getHitRect(area);
                int extra = (int) (TOUCH_EXTRA_DP
                        * anchor.getResources().getDisplayMetrics().density);
                area.inset(-extra, -extra);
                parent.setTouchDelegate(new TouchDelegate(area, anchor));
            }
        });
    }

    /** Bong bong chu thich hien ngay duoi o dau hoi. */
    private static void show(View anchor, String message) {
        Context ctx = anchor.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        TextView body = new TextView(ctx);
        body.setText(message);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        body.setTextColor(ContextCompat.getColor(ctx, R.color.dark_green));
        body.setBackgroundResource(R.drawable.bg_dialog);
        body.setMaxWidth((int) (MAX_WIDTH_DP * density));
        int pad = (int) (PADDING_DP * density);
        body.setPadding(pad, pad, pad, pad);

        final PopupWindow popup = new PopupWindow(body,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(12f * density);

        body.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popup.dismiss();
            }
        });

        popup.showAsDropDown(anchor, 0, (int) (4 * density), Gravity.START);
    }
}
