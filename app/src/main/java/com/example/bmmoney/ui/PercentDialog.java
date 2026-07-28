package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.example.bmmoney.R;

/** Popup ch\u1ec9nh m\u1ed9t gi\u00e1 tr\u1ecb ph\u1ea7n tr\u0103m (ng\u01b0\u1ee1ng chi ti\u00eau, m\u1ed1c chi ti\u00eau l\u1edbn). */
public final class PercentDialog {

    public interface OnPicked {
        void onPicked(int percent);
    }

    private PercentDialog() {
    }

    public static void show(Context context, String title, String hint,
                            int min, int max, int current, final OnPicked callback) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_percent, null, false);
        ((TextView) view.findViewById(R.id.tv_percent_title)).setText(title);
        ((TextView) view.findViewById(R.id.tv_percent_hint)).setText(hint);

        final NumberPicker picker = view.findViewById(R.id.np_percent);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, current)));
        picker.setWrapSelectorWheel(false);
        picker.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int value) {
                return value + "%";
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_percent_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_percent_save).setOnClickListener(v -> {
            picker.clearFocus();
            dialog.dismiss();
            if (callback != null) callback.onPicked(picker.getValue());
        });

        dialog.show();
    }
}
