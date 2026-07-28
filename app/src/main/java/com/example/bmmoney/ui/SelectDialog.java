package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bmmoney.R;

import java.util.List;

/** Select box cu\u1ed9n \u0111\u01b0\u1ee3c, d\u00f9ng chung cho ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n v\u00e0 c\u00e1c l\u1ef1a ch\u1ecdn kh\u00e1c. */
public final class SelectDialog {

    public interface OnPicked {
        void onPicked(int index, String value);
    }

    private SelectDialog() {
    }

    public static void show(Context context, String title, List<String> options,
                            String selected, final OnPicked callback) {
        if (context == null || options == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_select, null, false);
        ((TextView) view.findViewById(R.id.tv_select_title)).setText(title);

        LinearLayout container = view.findViewById(R.id.container_options);
        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            final String value = options.get(i);
            TextView row = (TextView) inflater.inflate(R.layout.item_select, container, false);
            row.setText(value);
            if (value.equals(selected)) {
                row.setBackgroundResource(R.drawable.bg_pill_olive);
                row.setTextColor(context.getResources().getColor(R.color.cream));
            }
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onPicked(index, value);
            });
            container.addView(row);
        }

        view.findViewById(R.id.btn_select_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
