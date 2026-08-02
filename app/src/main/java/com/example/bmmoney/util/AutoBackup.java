package com.example.bmmoney.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;

import com.example.bmmoney.remote.BackupReceiver;
import com.example.bmmoney.remote.FirebaseSyncManager;

import java.util.Calendar;

/**
 * Lich sao luu tu dong.
 *
 * <ul>
 *   <li>Moi buoi sang (mac dinh 7:00) he thong danh thuc app mot lan de sao luu.</li>
 *   <li>Sau khi ghi hoac xoa giao dich, mot lan sao luu duoc hen sau vai phut,
 *       nhieu thay doi lien tiep chi ton dung MOT lan ghi len cloud.</li>
 *   <li>Neu hom nay chua sao luu duoc lan nao (may tat, mat mang) thi lan mo app
 *       ke tiep se sao luu bu.</li>
 * </ul>
 *
 * <p><b>Ban va 01/08:</b> dieu kien cu co them {@code Prefs.lastBackup(context) <= 0}
 * nen may nao CHUA tung sao luu thanh cong se khong bao gio duoc sao luu tu dong -
 * dung nhung may dang can nhat. Dieu kien do da duoc bo.</p>
 */
public final class AutoBackup {

    /** Gio sao luu hang ngay. */
    public static final int HOUR = 7;

    private static final int CODE_DAILY = 8100;
    private static final int CODE_SOON = 8101;

    /** Cho phep gom nhieu thay doi lien tiep vao mot lan ghi cloud. */
    private static final long SOON_DELAY = 10 * 60 * 1000L;

    private AutoBackup() {
    }

    // ------------------------------------------------------------- dat lich
    /** Dat bao thuc sao luu cho 7 gio sang ke tiep. */
    public static void scheduleDaily(Context context) {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, HOUR);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        set(context, CODE_DAILY, target.getTimeInMillis(), BackupReceiver.ACTION_DAILY);
    }

    /** Hen sao luu sau vai phut, goi moi khi du lieu duoi may thay doi. */
    public static void scheduleSoon(Context context) {
        // Ban va 03/08: ghi nhan moc thay doi TRUOC khi kiem tra dang nhap.
        // Truoc day may chua dang nhap se khong bao gio co moc nay, nen sau khi
        // dang nhap nut Dong bo khong biet du lieu may la moi hay cu.
        Prefs.touchLocal(context.getApplicationContext());
        if (!FirebaseSyncManager.isSignedIn()) return;
        set(context, CODE_SOON, System.currentTimeMillis() + SOON_DELAY, BackupReceiver.ACTION_SOON);
    }

    private static void set(Context context, int code, long at, String action) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(context.getApplicationContext(), BackupReceiver.class);
        intent.setAction(action);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getBroadcast(
                context.getApplicationContext(), code, intent, flags);

        try {
            // Sao luu khong can chinh xac tung phut -> dung bao thuc thuong, do ton pin
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (Throwable ignored) {
            manager.set(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    // ------------------------------------------------------------- chay sao luu
    /** Sao luu bu neu hom nay chua co lan nao thanh cong. */
    public static void runIfDue(Context context, @Nullable Runnable done) {
        if (!FirebaseSyncManager.isSignedIn()
                || todayKey() == Prefs.autoBackupDay(context)) {
            if (done != null) done.run();
            return;
        }
        run(context, done);
    }

    /** Chay sao luu ngay, danh dau ngay da sao luu khi thanh cong. */
    public static void run(Context context, @Nullable Runnable done) {
        final Context app = context.getApplicationContext();
        if (!FirebaseSyncManager.isSignedIn()) {
            if (done != null) done.run();
            return;
        }
        try {
            new FirebaseSyncManager(app).backupNow((ok, count, error) -> {
                if (ok) Prefs.setAutoBackupDay(app, todayKey());
                if (done != null) done.run();
            });
        } catch (Throwable ignored) {
            if (done != null) done.run();
        }
    }

    /** Ngay hom nay dang yyyyMMdd, dung de biet da sao luu trong ngay chua. */
    public static int todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000
                + (c.get(Calendar.MONTH) + 1) * 100
                + c.get(Calendar.DAY_OF_MONTH);
    }
}
