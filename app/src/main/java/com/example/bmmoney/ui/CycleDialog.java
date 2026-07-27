package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.example.bmmoney.util.Prefs;

/** Popup chon ngay chot chu ky theo dd/mm. */
public final class CycleDialog {

    private CycleDialog() {
    }

    public static void show(final Context context, final Runnable onSaved) {
        int density = (int) context.getResources().getDisplayMetrics().density;

        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(20 * density, 12 * density, 20 * density, 0);

        TextView hint = new TextView(context);
        hint.setText("Ch\u1ecdn ng\u00e0y ch\u1ed1t chu k\u1ef3 (dd/mm). "
                + "M\u1ecdi s\u1ed1 li\u1ec7u s\u1ebd \u0111\u01b0\u1ee3c t\u00ednh theo kho\u1ea3ng gi\u1eefa hai l\u1ea7n ch\u1ed1t.");
        hint.setTextSize(13f);
        wrap.addView(hint);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 12 * density, 0, 0);

        final NumberPicker dayPicker = new NumberPicker(context);
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(31);
        dayPicker.setValue(Prefs.cycleDay(context));
        dayPicker.setWrapSelectorWheel(true);

        TextView slash = new TextView(context);
        slash.setText("  /  ");
        slash.setTextSize(18f);

        final NumberPicker monthPicker = new NumberPicker(context);
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(Prefs.cycleMonth(context));
        monthPicker.setWrapSelectorWheel(true);

        row.addView(dayPicker);
        row.addView(slash);
        row.addView(monthPicker);
        wrap.addView(row);

        new AlertDialog.Builder(context)
                .setTitle("Ng\u00e0y t\u00ednh chu k\u1ef3")
                .setView(wrap)
                .setNegativeButton("Hu\u1ef7", null)
                .setPositiveButton("L\u01b0u", (dialog, which) -> {
                    Prefs.setCycle(context, dayPicker.getValue(), monthPicker.getValue());
                    if (onSaved != null) onSaved.run();
                })
                .show();
    }

    static void hide(View view) {
        if (view != null) view.setVisibility(View.GONE);
    }
}
