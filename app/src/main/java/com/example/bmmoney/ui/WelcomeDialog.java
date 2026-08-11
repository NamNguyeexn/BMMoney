package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;

import com.example.bmmoney.R;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.ViewUtils;

import java.util.Calendar;

/**
 * Popup Xin chao, chi hien mot lan khi mo app lan dau.
 *
 * <p>Ban va 04/08: lam gon lai. Truoc day co nam o nhap, hai nut, va BAT BUOC
 * phai dien ten va ngan sach moi cho di tiep - dien sai thi bi Toast chan lai.
 * Nay khong o nao bat buoc: dien o nao thi luu o do, de trong thi lay mac dinh.
 * Chi con MOT nut, nen khong con canh nguoi dung phai chon giua "Bat dau" va
 * "De sau" ngay khi chua biet app lam gi.</p>
 *
 * <p>Nguong chi tieu va moc chi tieu lon da roi khoi man nay - chung co san mac
 * dinh 90% va 15%, va co hang rieng trong man Cai dat.</p>
 */
public final class WelcomeDialog {

    private WelcomeDialog() {
    }

    /** @param onDone chay sau khi luu xong de man hinh nap lai so lieu */
    public static void show(final Context context, final Runnable onDone) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_welcome, null, false);

        final EditText name = view.findViewById(R.id.edt_welcome_name);
        final EditText budget = view.findViewById(R.id.edt_welcome_budget);
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

        ViewUtils.onClick(view, R.id.btn_welcome_start, v -> {
            // Dien o nao thi luu o do. Khong o nao bat buoc.
            String userName = name.getText().toString().trim();
            Prefs.setUserName(context, userName.isEmpty() ? "b\u1ea1n" : userName);

            String digits = budget.getText().toString().replaceAll("[^0-9]", "");
            double value = Prefs.DEFAULT_BUDGET;
            if (!digits.isEmpty()) {
                try {
                    double typed = Double.parseDouble(digits);
                    if (typed > 0d) value = typed;
                } catch (NumberFormatException ignored) {
                    // so qua dai hoac khong doc duoc -> giu mac dinh
                }
            }
            Prefs.setBudget(context, value);
            Prefs.setCycle(context, day.getValue(), month.getValue());
            Prefs.setOnboarded(context, true);

            dialog.dismiss();
            if (onDone != null) onDone.run();
        });

        dialog.show();
    }
}
