package com.example.bmmoney.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.bmmoney.remote.ReminderReceiver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Cac moc gio nhac ghi chi tieu.
 * Moi moc gio duoc dat bang mot bao thuc cua he thong (AlarmManager),
 * nen app KHONG can chay nen kiem tra lien tuc.
 */
public final class Reminders {

    /** Mot moc nhac: gio, phut va ghi chu. */
    public static class Item {
        public final int hour;
        public final int minute;
        public final String label;

        public Item(int hour, int minute, String label) {
            this.hour = hour;
            this.minute = minute;
            this.label = label == null ? "" : label;
        }

        public String time() {
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        }

        /** Ma yeu cau rieng cho tung moc gio. */
        public int requestCode() {
            return 9000 + hour * 60 + minute;
        }
    }

    private static final String SEP = "\u241f";
    private static final String ROW = "\n";

    private Reminders() {
    }

    public static List<Item> all(Context context) {
        List<Item> list = new ArrayList<>();
        String raw = Prefs.remindersRaw(context);
        if (raw == null || raw.trim().isEmpty()) return list;
        for (String row : raw.split(ROW)) {
            String[] parts = row.split(SEP, -1);
            if (parts.length < 3) continue;
            try {
                list.add(new Item(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]));
            } catch (NumberFormatException ignored) {
                // bo qua dong hong
            }
        }
        return list;
    }

    public static void save(Context context, List<Item> list) {
        StringBuilder sb = new StringBuilder();
        for (Item item : list) {
            if (sb.length() > 0) sb.append(ROW);
            sb.append(item.hour).append(SEP).append(item.minute).append(SEP).append(item.label);
        }
        Prefs.setRemindersRaw(context, sb.toString());
        rescheduleAll(context);
    }

    /** Dat lai toan bo bao thuc (goi sau khi sua danh sach hoac sau khi khoi dong may). */
    public static void rescheduleAll(Context context) {
        for (Item item : all(context)) {
            schedule(context, item);
        }
    }

    public static void schedule(Context context, Item item) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, item.hour);
        target.set(Calendar.MINUTE, item.minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }

        PendingIntent pending = pendingIntent(context, item);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pending);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pending);
            }
        } catch (SecurityException e) {
            // May chan bao thuc chinh xac -> dung bao thuc thuong
            manager.set(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pending);
        }
    }

    public static void cancel(Context context, Item item) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pendingIntent(context, item));
    }

    private static PendingIntent pendingIntent(Context context, Item item) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.bmmoney.REMIND");
        intent.putExtra("hour", item.hour);
        intent.putExtra("minute", item.minute);
        intent.putExtra("label", item.label);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context.getApplicationContext(),
                item.requestCode(), intent, flags);
    }
}
