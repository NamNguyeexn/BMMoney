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

    /**
     * Ban va 04/08: mot so ROM (Honor, Huawei, Xiaomi) khong gui BOOT_COMPLETED
     * chuan sau khi khoi dong lai may ma gui QUICKBOOT_POWERON. Neu chi lang nghe
     * BOOT_COMPLETED thi sau moi lan tat may, toan bo loi nhac im lang han cho den
     * khi nguoi dung mo app.
     */
    private static boolean isBoot(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.huawei.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final String action = intent == null ? null : intent.getAction();

        if (isBoot(action)) {
            Reminders.rescheduleAll(app);
            return;
        }
        if (intent == null) return;

        final int hour = intent.getIntExtra("hour", 20);
        final int minute = intent.getIntExtra("minute", 0);
        final String label = intent.getStringExtra("label");
        final Reminders.Item item = new Reminders.Item(hour, minute, label);

        // Ban va 04/08: dat lai bao thuc NGAY LAP TUC, truoc khi doc co so du lieu.
        // Truoc day viec dat lai nam sau mot loat truy van Room; chi can mot loi doc
        // du lieu (hoac he thong giet tien trinh giua duong) la chuoi bao thuc dut
        // han, va se khong bao gio nhac lai cho den khi nguoi dung mo app.
        Reminders.schedule(app, item);

        final PendingResult result = goAsync();
        Db.io(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!hasTransactionToday(app)) {
                        push(app, item.requestCode(), label);
                    }
                } catch (Throwable ignored) {
                    // khong de loi doc du lieu lam sap tien trinh nhan bao thuc
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

    /**
     * Day mot thong bao nhac ghi chi tieu.
     *
     * <p>Ban va 04/08: mo ra public static de nut "Thu thong bao" trong man Cai dat
     * dung chung dung mot duong day thong bao voi bao thuc that. Nho vay khi thu
     * thanh cong thi bao thuc that cung se hien duoc.</p>
     *
     * <p>Moi moc gio dung mot ma thong bao rieng, truoc day dung chung so 4321 nen
     * hai moc gio gan nhau se ghi de len nhau.</p>
     */
    public static void push(Context context, int id, String label) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(context, id, open, flags);

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

        manager.notify(id, notification);
    }
}
