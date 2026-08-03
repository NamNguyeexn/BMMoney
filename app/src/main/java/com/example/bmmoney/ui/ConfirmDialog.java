package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.bmmoney.R;

/**
 * Ban va 03/08. Hop thoai xac nhan dung chung, theo dung chu de cua app.
 *
 * <p><b>Van de cu:</b> cac man goi
 * {@code new AlertDialog.Builder(ctx, R.style.Theme_Bmm_Dialog)}. Theme do co dat
 * {@code android:background} nhung thuoc tinh nay KHONG doi duoc nen cua so cua
 * AlertDialog (phai la {@code android:windowBackground}), nen thuc te van hien ra
 * hop thoai trang, goc vuong, nut chu IN HOA mau xanh duong cua he thong - lac hoan
 * toan voi tong kem / o liu / nau dat.</p>
 *
 * <p><b>Nay:</b> dung {@code dialog_confirm.xml}, cung bo drawable voi
 * {@code dialog_tx.xml}, nen moi hop thoai trong app deu cung mot ngon ngu thiet ke.</p>
 *
 * <pre>
 * // Hai lua chon
 * ConfirmDialog.show(ctx, "✕", "Xóa giao dịch?", "Cà phê · 45.000 ₫",
 *         "Xóa", () -> doDelete());
 *
 * // Ba lua chon
 * ConfirmDialog.choose(ctx, "☁", "Đã có bản sao lưu", msg,
 *         "Lấy bản cloud về", this::restore,
 *         "Sao lưu đè", this::backup);
 * </pre>
 */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    /** Hai lua chon: dong y (nut nau dat) va bo qua. */
    public static void show(Context context, String icon, String title, String message,
                            String confirmText, Runnable onConfirm) {
        show(context, icon, title, message, confirmText, "Gi\u1eef l\u1ea1i", onConfirm);
    }

    /** Hai lua chon, tu dat luon chu cho nut bo qua. */
    public static void show(Context context, String icon, String title, String message,
                            String confirmText, String cancelText, Runnable onConfirm) {
        build(context, icon, title, message,
                confirmText, onConfirm,
                null, null,
                cancelText);
    }

    /**
     * Ba lua chon, dung cho khoi Dong bo &amp; Sao luu: mot huong chinh (nau dat),
     * mot huong phu (o liu) va "De sau".
     */
    public static void choose(Context context, String icon, String title, String message,
                              String primaryText, Runnable onPrimary,
                              String secondaryText, Runnable onSecondary) {
        build(context, icon, title, message,
                primaryText, onPrimary,
                secondaryText, onSecondary,
                "\u0110\u1ec3 sau");
    }

    private static void build(Context context, String icon, String title, String message,
                              String primaryText, @Nullable final Runnable onPrimary,
                              @Nullable String secondaryText, @Nullable final Runnable onSecondary,
                              String cancelText) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null, false);
        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            // Bo nen trang goc vuong mac dinh, de lo bo goc 20dp cua bg_dialog
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        text(view, R.id.tv_confirm_icon, icon == null || icon.isEmpty() ? "!" : icon);
        text(view, R.id.tv_confirm_title, title);

        TextView body = view.findViewById(R.id.tv_confirm_message);
        if (message == null || message.trim().isEmpty()) {
            body.setVisibility(View.GONE);
        } else {
            body.setVisibility(View.VISIBLE);
            body.setText(message);
        }

        TextView yes = view.findViewById(R.id.btn_confirm_yes);
        yes.setText(primaryText);
        yes.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null) onPrimary.run();
        });

        TextView alt = view.findViewById(R.id.btn_confirm_alt);
        if (secondaryText == null || secondaryText.isEmpty()) {
            alt.setVisibility(View.GONE);
        } else {
            alt.setVisibility(View.VISIBLE);
            alt.setText(secondaryText);
            alt.setOnClickListener(v -> {
                dialog.dismiss();
                if (onSecondary != null) onSecondary.run();
            });
        }

        TextView no = view.findViewById(R.id.btn_confirm_no);
        no.setText(cancelText);
        no.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static void text(View root, int id, String value) {
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
