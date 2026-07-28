package com.example.bmmoney.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.bmmoney.BmmApp;
import com.example.bmmoney.MainActivity;
import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Reminders;

import java.util.Calendar;
import java.util.List;

/**
 * Nhan bao thuc cua he thong dung gio nguoi dung dat.
 * Chi day thong bao khi hom nay chua ghi giao dich nao,
 * sau do dat lai bao thuc cho ngay hom sau (khong co tien trinh nao chay nen).
 */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final int hour = intent.getIntExtra("hour", 20);
        final int minute = intent.getIntExtra("minute", 0);
        final String label = intent.getStringExtra("label");

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Reminders.rescheduleAll(app);
            return;
        }

        final PendingResult result = goAsync();
        Db.io(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!hasTransactionToday(app)) {
                        notifyUser(app, label);
                    }
                    // dat lai cho ngay mai
                    Reminders.schedule(app, new Reminders.Item(hour, minute, label));
                } finally {
                    result.finish();
                }
            }
        });
    }

    private boolean hasTransactionToday(Context context) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long from = c.getTimeInMillis();
        long to = from + 24L * 60 * 60 * 1000 - 1;

        List<TransactionEntity> list = AppDatabase.dao(context).getTransactionsByDateRange(from, to);
        return list != null && !list.isEmpty();
    }

    private void notifyUser(Context context, String label) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(context, 4321, open, flags);

        String text = label == null || label.trim().isEmpty()
                ? "H\u00f4m nay b\u1ea1n ch\u01b0a ghi kho\u1ea3n chi n\u00e0o. Ghi ngay cho \u0111\u1ee7 nh\u00e9!"
                : label;

        Notification notification = new NotificationCompat.Builder(context, BmmApp.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_add)
                .setContentTitle("Nh\u1eafc ghi chi ti\u00eau")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();

        manager.notify(4321, notification);
    }
}
