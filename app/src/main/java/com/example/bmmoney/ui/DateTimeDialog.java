package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;
import android.widget.NumberPicker;

import com.example.bmmoney.R;

import java.util.Calendar;

/** M\u1ed9t popup duy nh\u1ea5t cho ph\u00e9p ch\u1ecdn \u0111\u1ed3ng th\u1eddi ng\u00e0y, gi\u1edd v\u00e0 ph\u00fat. */
public final class DateTimeDialog {

    public interface OnPicked {
        void onPicked(long timeMillis);
    }

    private DateTimeDialog() {
    }

    public static void show(Context context, long initial, final OnPicked callback) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_datetime, null, false);
        final DatePicker date = view.findViewById(R.id.dt_date);
        final NumberPicker hour = view.findViewById(R.id.dt_hour);
        final NumberPicker minute = view.findViewById(R.id.dt_minute);

        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(initial > 0 ? initial : System.currentTimeMillis());
        date.updateDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        hour.setMinValue(0);
        hour.setMaxValue(23);
        hour.setValue(c.get(Calendar.HOUR_OF_DAY));
        hour.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int value) {
                return value < 10 ? "0" + value : String.valueOf(value);
            }
        });

        minute.setMinValue(0);
        minute.setMaxValue(59);
        minute.setValue(c.get(Calendar.MINUTE));
        minute.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int value) {
                return value < 10 ? "0" + value : String.valueOf(value);
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.dt_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.dt_ok).setOnClickListener(v -> {
            Calendar picked = Calendar.getInstance();
            picked.set(date.getYear(), date.getMonth(), date.getDayOfMonth(),
                    hour.getValue(), minute.getValue(), 0);
            picked.set(Calendar.MILLISECOND, 0);
            dialog.dismiss();
            if (callback != null) callback.onPicked(picked.getTimeInMillis());
        });

        dialog.show();
    }
}
