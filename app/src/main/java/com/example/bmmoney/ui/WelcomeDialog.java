package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.example.bmmoney.R;
import com.example.bmmoney.util.Prefs;

import java.util.Calendar;

/**
 * Popup Xin chao, chi hien mot lan khi mo app lan dau.
 * Cho nguoi dung dien ho ten, ngan sach thang, ngay chot chu ky,
 * nguong chi tieu va moc chi tieu lon.
 */
public final class WelcomeDialog {

    private WelcomeDialog() {
    }

    /** @param onDone chay sau khi luu xong (hoac bo qua) de man hinh nap lai so lieu */
    public static void show(final Context context, final Runnable onDone) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_welcome, null, false);

        final EditText name = view.findViewById(R.id.edt_welcome_name);
        final EditText budget = view.findViewById(R.id.edt_welcome_budget);
        final EditText warn = view.findViewById(R.id.edt_welcome_warn);
        final EditText big = view.findViewById(R.id.edt_welcome_big);
        final NumberPicker day = view.findViewById(R.id.np_welcome_day);
        final NumberPicker month = view.findViewById(R.id.np_welcome_month);

        Calendar today = Calendar.getInstance();
        day.setMinValue(1);
        day.setMaxValue(31);
        day.setWrapSelectorWheel(true);
        day.setValue(today.get(Calendar.DAY_OF_MONTH));

        month.setMinValue(1);
        month.setMaxValue(12);
        month.setWrapSelectorWheel(true);
        month.setValue(today.get(Calendar.MONTH) + 1);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(false)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_welcome_skip).setOnClickListener(v -> {
            Prefs.setOnboarded(context, true);
            dialog.dismiss();
            if (onDone != null) onDone.run();
        });

        view.findViewById(R.id.btn_welcome_start).setOnClickListener(v -> {
            String userName = name.getText().toString().trim();
            if (userName.isEmpty()) {
                Toast.makeText(context, "Nh\u1eadp t\u00ean \u0111\u1ec3 app ch\u00e0o b\u1ea1n nh\u00e9", Toast.LENGTH_SHORT).show();
                name.requestFocus();
                return;
            }

            String rawBudget = budget.getText().toString().replaceAll("[^0-9]", "");
            if (rawBudget.isEmpty() || Double.parseDouble(rawBudget) <= 0d) {
                Toast.makeText(context, "Nh\u1eadp ng\u00e2n s\u00e1ch h\u00e0ng th\u00e1ng nh\u00e9", Toast.LENGTH_SHORT).show();
                budget.requestFocus();
                return;
            }

            Prefs.setUserName(context, userName);
            Prefs.setBudget(context, Double.parseDouble(rawBudget));
            Prefs.setCycle(context, day.getValue(), month.getValue());
            Prefs.setWarnPercent(context, number(warn, 90));
            Prefs.setBigPercent(context, number(big, 15));
            Prefs.setOnboarded(context, true);

            dialog.dismiss();
            Toast.makeText(context, "Xong r\u1ed3i, ch\u00fac b\u1ea1n gi\u1eef \u0111\u01b0\u1ee3c th\u00f3i quen n\u00e0y!",
                    Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
        });

        dialog.show();
    }

    /** Doc so tu o nhap, rong hoac sai thi dung gia tri mac dinh. */
    private static int number(EditText input, int fallback) {
        String raw = input.getText().toString().replaceAll("[^0-9]", "");
        if (raw.isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
