package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.example.bmmoney.R;
import com.example.bmmoney.util.Reminders;

import java.util.Calendar;

/** Th\u00eam, s\u1eeda ho\u1eb7c xo\u00e1 m\u1ed9t m\u1ed1c gi\u1edd nh\u1eafc ghi chi ti\u00eau. */
public final class ReminderDialog {

    public interface OnResult {
        void onSave(Reminders.Item item);

        void onDelete();
    }

    private ReminderDialog() {
    }

    public static void show(Context context, Reminders.Item existing, final OnResult callback) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_reminder, null, false);
        final NumberPicker hour = view.findViewById(R.id.rm_hour);
        final NumberPicker minute = view.findViewById(R.id.rm_minute);
        final EditText label = view.findViewById(R.id.edt_reminder_label);
        View delete = view.findViewById(R.id.btn_reminder_delete);

        NumberPicker.Formatter twoDigits = new NumberPicker.Formatter() {
            @Override
            public String format(int value) {
                return value < 10 ? "0" + value : String.valueOf(value);
            }
        };
        hour.setMinValue(0);
        hour.setMaxValue(23);
        hour.setFormatter(twoDigits);
        minute.setMinValue(0);
        minute.setMaxValue(59);
        minute.setFormatter(twoDigits);

        if (existing == null) {
            Calendar c = Calendar.getInstance();
            hour.setValue(c.get(Calendar.HOUR_OF_DAY));
            minute.setValue(0);
            delete.setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.tv_reminder_title)).setText("Th\u00eam l\u1eddi nh\u1eafc");
        } else {
            hour.setValue(existing.hour);
            minute.setValue(existing.minute);
            label.setText(existing.label);
            ((TextView) view.findViewById(R.id.tv_reminder_title)).setText("S\u1eeda l\u1eddi nh\u1eafc");
        }

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_reminder_cancel).setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onDelete();
        });
        view.findViewById(R.id.btn_reminder_save).setOnClickListener(v -> {
            hour.clearFocus();
            minute.clearFocus();
            dialog.dismiss();
            if (callback != null) {
                callback.onSave(new Reminders.Item(hour.getValue(), minute.getValue(),
                        label.getText().toString().trim()));
            }
        });

        dialog.show();
    }
}
