package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.example.bmmoney.R;
import com.example.bmmoney.util.ViewUtils;

import java.util.Locale;

/**
 * Popup chinh mot gia tri phan tram (nguong chi tieu, moc chi tieu lon).
 *
 * <h3>Ban va 11/08 - GO THANG BANG BAN PHIM SO</h3>
 *
 * <p>Truoc day o day la {@code NumberPicker}. Doi tu 10% len 180% nghia la vuot banh
 * xe hang chuc lan, va khong co cach nao nhay thang toi mot con so. Nay o nhap la mot
 * {@code EditText} chi nhan chu so, ban phim so bat san khi mo hop thoai; hai nut
 * +/- chi con de chinh nhe mot vai bac.</p>
 *
 * <p>Gia tri nhap luon duoc KEP lai trong khoang {@code min..max} ngay truoc khi tra
 * ve, nen ben goi khong bao gio nhan duoc mot con so vo ly - go 999 vao o "Moc chi
 * tieu lon" (toi da 99) thi luu ra dung 99.</p>
 */
public final class PercentDialog {

    public interface OnPicked {
        void onPicked(int percent);
    }

    private PercentDialog() {
    }

    public static void show(Context context, String title, String hint,
                            final int min, final int max, int current, final OnPicked callback) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_percent, null, false);
        ((TextView) view.findViewById(R.id.tv_percent_title)).setText(title);
        ((TextView) view.findViewById(R.id.tv_percent_hint)).setText(hint);

        TextView range = view.findViewById(R.id.tv_percent_range);
        if (range != null) {
            range.setText(String.format(Locale.US,
                    "Nh\u1eadp t\u1eeb %d%% \u0111\u1ebfn %d%%", min, max));
        }

        final EditText input = view.findViewById(R.id.edt_percent);
        input.setText(String.valueOf(clamp(current, min, max)));
        input.setSelection(input.getText().length());

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Ban phim so hien len cung luc voi hop thoai: nguoi dung go duoc ngay,
            // khong phai bam them mot lan vao o nhap.
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        // Hai nut chinh nhe. Doc lai gia tri dang go chu khong giu mot bien rieng, nen
        // go tay roi bam + van cong tiep tu dung con so vua go.
        ViewUtils.onClick(view, R.id.btn_percent_minus,
                v -> input.setText(String.valueOf(clamp(read(input, current) - 1, min, max))));
        ViewUtils.onClick(view, R.id.btn_percent_plus,
                v -> input.setText(String.valueOf(clamp(read(input, current) + 1, min, max))));

        ViewUtils.onClick(view, R.id.btn_percent_cancel, v -> dialog.dismiss());
        ViewUtils.onClick(view, R.id.btn_percent_save, v -> {
            int value = clamp(read(input, current), min, max);
            dialog.dismiss();
            if (callback != null) callback.onPicked(value);
        });

        // Phim Xong tren ban phim so = bam Luu.
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int value = clamp(read(input, current), min, max);
                dialog.dismiss();
                if (callback != null) callback.onPicked(value);
                return true;
            }
            return false;
        });

        dialog.show();
        input.requestFocus();
    }

    /** So dang go trong o. O rong hoac go dang do thi giu lai gia tri cu. */
    private static int read(EditText input, int fallback) {
        String raw = input.getText() == null ? "" : input.getText().toString();
        raw = raw.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(raw)) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
