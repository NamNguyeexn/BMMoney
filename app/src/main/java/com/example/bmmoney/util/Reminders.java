package com.example.bmmoney.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationManagerCompat;

import com.example.bmmoney.MainActivity;
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
        long at = target.getTimeInMillis();
        try {
            if (usingAlarmClock(context, manager)) {
                // Ban va 04/08: day la muc uu tien cao nhat Android cho phep, ngang bao
                // thuc cua dong ho he thong. Doze, App Standby va cac trinh tiet kiem pin
                // cua hang deu KHONG duoc phep hoan hay dong bang no. Va van khong ton pin:
                // trong luc cho, app khong chay - bao thuc nam trong bang bao thuc cua he
                // thong, chinh tien trinh system_server danh thuc may dung giay da hen.
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(context)), pending);
            } else if (!canScheduleExact(manager)) {
                // Android 12+ chua cho dat bao thuc chinh xac -> van nhac, chi lech vai phut
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, at, pending);
            }
        } catch (Throwable e) {
            manager.set(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    /** True khi dang dat bao thuc o muc uu tien dong ho he thong. */
    public static boolean usingAlarmClock(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && usingAlarmClock(context, manager);
    }

    private static boolean usingAlarmClock(Context context, AlarmManager manager) {
        return Prefs.strongAlarm(context) && canScheduleExact(manager);
    }

    /**
     * Man se mo khi nguoi dung bam vao bieu tuong dong ho tren thanh trang thai.
     * setAlarmClock bat buoc phai co, va chinh no lam bao thuc tro thanh "bao thuc
     * nguoi dung nhin thay duoc" - ly do he thong khong dam hoan.
     */
    private static PendingIntent showIntent(Context context) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context.getApplicationContext(), 9100, open, flags);
    }

    /** True khi may cho phep dat bao thuc chinh xac tung phut. */
    public static boolean canScheduleExact(AlarmManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        try {
            return manager.canScheduleExactAlarms();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Mo trang cai dat de nguoi dung bat quyen bao thuc chinh xac. */
    public static void openExactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
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

    // ------------------------------------------------- Ban va 04/08: chan doan
    /**
     * Thoi diem l\u1eddi nh\u1eafc g\u1ea7n nh\u1ea5t s\u1eafp n\u1ed5, 0 khi ch\u01b0a \u0111\u1eb7t m\u1ed1c n\u00e0o.
     * Ch\u1ec9 t\u00ednh l\u1ea1i t\u1eeb danh s\u00e1ch n\u00ean lu\u00f4n kh\u1edbp v\u1edbi nh\u1eefng g\u00ec ng\u01b0\u1eddi d\u00f9ng th\u1ea5y.
     */
    public static long nextTrigger(Context context) {
        long best = 0L;
        long now = System.currentTimeMillis();
        for (Item item : all(context)) {
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, item.hour);
            target.set(Calendar.MINUTE, item.minute);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);
            if (target.getTimeInMillis() <= now) {
                target.add(Calendar.DAY_OF_MONTH, 1);
            }
            long at = target.getTimeInMillis();
            if (best == 0L || at < best) best = at;
        }
        return best;
    }

    /** True khi m\u00e1y cho \u0111\u1eb7t b\u00e1o th\u1ee9c \u0111\u00fang ph\u00fat. */
    public static boolean canScheduleExact(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager == null || canScheduleExact(manager);
    }

    /** True khi ng\u01b0\u1eddi d\u00f9ng ch\u01b0a t\u1eaft th\u00f4ng b\u00e1o c\u1ee7a app. */
    public static boolean notificationsEnabled(Context context) {
        try {
            return NotificationManagerCompat.from(context).areNotificationsEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * True khi app \u0111\u01b0\u1ee3c mi\u1ec5n t\u1ed1i \u01b0u pin.
     *
     * <p>\u0110\u00e2y l\u00e0 nguy\u00ean nh\u00e2n th\u01b0\u1eddng g\u1eb7p nh\u1ea5t khi l\u1eddi nh\u1eafc kh\u00f4ng hi\u1ec7n: h\u1ec7 th\u1ed1ng \u0111\u00f4ng
     * l\u1ea1nh app d\u01b0\u1edbi n\u1ec1n, b\u00e1o th\u1ee9c n\u1ed5 nh\u01b0ng l\u1ec7nh b\u1ecb gi\u1eef l\u1ea1i cho t\u1edbi khi ng\u01b0\u1eddi d\u00f9ng
     * m\u1edf app, n\u00ean th\u00f4ng b\u00e1o m\u1edbi nh\u1ea3y ra \u0111\u00fang l\u00fac v\u00e0o app.</p>
     */
    public static boolean ignoringBattery(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** M\u1edf h\u1ed9p tho\u1ea1i xin mi\u1ec5n t\u1ed1i \u01b0u pin cho app. */
    public static void openBatterySettings(Context context) {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
            openAppSettings(context);
        }
    }

    /** M\u1edf trang th\u00f4ng tin \u1ee9ng d\u1ee5ng, \u0111\u1ec3 b\u1eadt l\u1ea1i quy\u1ec1n th\u00f4ng b\u00e1o. */
    public static void openAppSettings(Context context) {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }
}
