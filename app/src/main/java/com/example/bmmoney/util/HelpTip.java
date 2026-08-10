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

import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;

/**
 * Dau hoi tron nho, bam vao thi bung ra mot chu thich ngan.
 *
 * <p>Truoc day moi con so tren man hinh deu keo theo mot dong giai thich nam san ben
 * duoi. Cong lai chung chiem nhieu cho hon chinh con so, trong khi nguoi dung chi can
 * doc chung dung mot lan. Nay phan giai thich duoc cat vao day: giao dien chi con so,
 * ai muon hieu them thi bam dau hoi.</p>
 *
 * <p>Ca vong tron lan hop chu thich deu duoc ve bang ma, khong dung tep hinh rieng,
 * nen them mot dau hoi moi o bat ky man nao cung khong phai tao them tai nguyen.</p>
 */
public final class HelpTip {

    private HelpTip() {
    }

    /** Be ngang toi da cua hop chu thich, tinh theo dp. */
    private static final int MAX_WIDTH_DP = 260;

    private static final float TEXT_SP = 14f;

    private static final int PADDING_DP = 14;

    /** Noi them vung cham ra moi phia cua vong tron, tinh theo dp. */
    private static final int TOUCH_EXTRA_DP = 12;

    /**
     * Gan chu thich vao mot dau hoi trong layout.
     *
     * <p>Khong tim thay o dau hoi thi bo qua chu khong nem loi, de man hinh nao chua
     * kip them dau hoi van chay binh thuong. Loi nhan rong thi dau hoi tu an di - khong
     * co gi de noi thi khong nen bay ra mot nut bam cut.</p>
     */
    public static void attach(View root, int anchorId, String message) {
        if (root == null) return;
        final View anchor = root.findViewById(anchorId);
        if (anchor == null) return;
        if (message == null || message.isEmpty()) {
            anchor.setVisibility(View.GONE);
            return;
        }
        anchor.setVisibility(View.VISIBLE);
        style(anchor);
        expandTouchArea(anchor);
        anchor.setOnClickListener(v -> show(v, message));
    }

    /**
     * Ve vong tron vien quanh dau hoi.
     *
     * <p>Chu hoi duoc dat o day chu khong dat trong layout: trong XML cua Android, mot
     * gia tri mo dau bang dau hoi bi hieu la tham chieu thuoc tinh cua theme, nen
     * android:text="?" lam trinh bien dich tai nguyen bao loi thieu kieu tai nguyen.</p>
     *
     * <p>Mau vien lay tu chinh mau chu cua o do, nen dat tren the nen kem hay the nen
     * toi deu doc duoc ma khong can hai bo tai nguyen khac nhau.</p>
     */
    private static void style(View anchor) {
        if (!(anchor instanceof TextView)) return;
        final TextView badge = (TextView) anchor;
        badge.setText("?");
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setPadding(0, 0, 0, 0);

        final float density = badge.getResources().getDisplayMetrics().density;
        final GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Math.max(1, (int) (1.2f * density)), badge.getCurrentTextColor());
        badge.setBackground(ring);
    }

    /**
     * Vong tron chi rong 18dp nen ngon tay de bam truot. Noi rong vung cham ra moi phia
     * de bam thoai mai ma vong tron van nho.
     */
    private static void expandTouchArea(final View anchor) {
        if (!(anchor.getParent() instanceof ViewGroup)) return;
        final ViewGroup parent = (ViewGroup) anchor.getParent();
        // Doi bo cuc do xong moi biet o dau hoi nam o toa do nao.
        parent.post(() -> {
            final Rect area = new Rect();
            anchor.getHitRect(area);
            final int extra = (int) (TOUCH_EXTRA_DP
                    * anchor.getResources().getDisplayMetrics().density);
            area.inset(-extra, -extra);
            parent.setTouchDelegate(new TouchDelegate(area, anchor));
        });
    }

    /** Bung hop chu thich ngay duoi o vua bam. */
    public static void show(View anchor, String message) {
        if (anchor == null || message == null || message.isEmpty()) return;
        final Context context = anchor.getContext();
        if (context == null) return;
        final float density = context.getResources().getDisplayMetrics().density;

        final TextView body = new TextView(context);
        body.setText(message);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        body.setTextColor(ContextCompat.getColor(context, R.color.dark_green));
        body.setBackgroundResource(R.drawable.bg_dialog);
        body.setMaxWidth((int) (MAX_WIDTH_DP * density));
        final int pad = (int) (PADDING_DP * density);
        body.setPadding(pad, pad, pad, pad);

        final PopupWindow popup = new PopupWindow(body,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        // Nen trong suot: khung bo tron nam trong bg_dialog. De nen mac dinh cua
        // PopupWindow thi bon goc vuong mau xam se lo ra quanh khung bo tron do.
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(12f * density);

        body.setOnClickListener(v -> popup.dismiss());
        popup.showAsDropDown(anchor, 0, (int) (4 * density), Gravity.START);
    }
}
