package com.example.bmmoney.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Luu ten nguoi dung, ngan sach, ngay chot chu ky, danh muc va thoi diem sao luu. */
public final class Prefs {

    private static final String FILE = "bmm_settings";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_BUDGET = "monthly_budget";
    private static final String KEY_BACKUP = "last_backup";
    private static final String KEY_CYCLE_DAY = "cycle_day";
    private static final String KEY_CYCLE_MONTH = "cycle_month";
    private static final String KEY_CATEGORIES = "categories";
    private static final String KEY_WARN_PERCENT = "warn_percent";
    private static final String KEY_BIG_PERCENT = "big_percent";
    private static final String KEY_REMINDERS = "reminders";
    private static final String KEY_ONBOARDED = "onboarded";
    private static final String KEY_AUTO_BACKUP_DAY = "auto_backup_day";
    private static final String KEY_LEGACY_CLEANED = "legacy_cleaned";
    /** Ban va 03/08: moc thoi gian du lieu duoi may doi lan cuoi (de so voi cloud). */
    private static final String KEY_LOCAL_CHANGED = "local_changed";

    public static final double DEFAULT_BUDGET = 80500000d;

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String userName(Context context) {
        return prefs(context).getString(KEY_NAME, "b\u1ea1n");
    }

    public static void setUserName(Context context, String name) {
        prefs(context).edit().putString(KEY_NAME, name).apply();
    }

    public static double budget(Context context) {
        return prefs(context).getFloat(KEY_BUDGET, (float) DEFAULT_BUDGET);
    }

    public static void setBudget(Context context, double budget) {
        prefs(context).edit().putFloat(KEY_BUDGET, (float) budget).apply();
    }

    /**
     * Ban va 03/08. Lan cuoi du lieu duoi may thay doi.
     *
     * <p>Dung de nut Dong bo biet nen day len hay keo ve: neu may moi hon cloud
     * thi sao luu de, con cloud moi hon thi lay ve.</p>
     */
    public static long localChangedAt(Context context) {
        return prefs(context).getLong(KEY_LOCAL_CHANGED, 0L);
    }

    public static void setLocalChangedAt(Context context, long time) {
        prefs(context).edit().putLong(KEY_LOCAL_CHANGED, time).apply();
    }

    /** Danh dau du lieu duoi may vua doi ngay bay gio. */
    public static void touchLocal(Context context) {
        setLocalChangedAt(context, System.currentTimeMillis());
    }

    public static long lastBackup(Context context) {
        return prefs(context).getLong(KEY_BACKUP, 0L);
    }

    public static void setLastBackup(Context context, long time) {
        prefs(context).edit().putLong(KEY_BACKUP, time).apply();
    }

    /** Ngay chot chu ky (1..31). */
    public static int cycleDay(Context context) {
        return prefs(context).getInt(KEY_CYCLE_DAY, 1);
    }

    /** Thang moc cua chu ky (1..12), chi dung de hien thi dd/mm. */
    public static int cycleMonth(Context context) {
        int fallback = Calendar.getInstance().get(Calendar.MONTH) + 1;
        return prefs(context).getInt(KEY_CYCLE_MONTH, fallback);
    }

    public static void setCycle(Context context, int day, int month) {
        prefs(context).edit()
                .putInt(KEY_CYCLE_DAY, Math.max(1, Math.min(31, day)))
                .putInt(KEY_CYCLE_MONTH, Math.max(1, Math.min(12, month)))
                .apply();
    }

    public static String categoriesRaw(Context context) {
        return prefs(context).getString(KEY_CATEGORIES, null);
    }

    public static void setCategoriesRaw(Context context, String raw) {
        prefs(context).edit().putString(KEY_CATEGORIES, raw).apply();
    }

    /** Nguong canh bao chi tieu (% ngan sach), mac dinh 90%. */
    public static int warnPercent(Context context) {
        return prefs(context).getInt(KEY_WARN_PERCENT, 90);
    }

    public static void setWarnPercent(Context context, int percent) {
        prefs(context).edit().putInt(KEY_WARN_PERCENT, clamp(percent, 10, 200)).apply();
    }

    /** Moc "khoan chi dang chu y": giao dich chiem tu x% tong chi cua ky, mac dinh 15%. */
    public static int bigPercent(Context context) {
        return prefs(context).getInt(KEY_BIG_PERCENT, 15);
    }

    public static void setBigPercent(Context context, int percent) {
        prefs(context).edit().putInt(KEY_BIG_PERCENT, clamp(percent, 1, 99)).apply();
    }

    public static String remindersRaw(Context context) {
        return prefs(context).getString(KEY_REMINDERS, null);
    }

    public static void setRemindersRaw(Context context, String raw) {
        prefs(context).edit().putString(KEY_REMINDERS, raw).apply();
    }

    /** Da qua popup Xin chao lan dau chua. */
    public static boolean onboarded(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDED, false);
    }

    public static void setOnboarded(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, value).apply();
    }

    /** Ngay da tu dong sao luu gan nhat, dang yyyyMMdd. */
    public static int autoBackupDay(Context context) {
        return prefs(context).getInt(KEY_AUTO_BACKUP_DAY, 0);
    }

    public static void setAutoBackupDay(Context context, int day) {
        prefs(context).edit().putInt(KEY_AUTO_BACKUP_DAY, day).apply();
    }

    /** Da don xong cac ban ghi cloud kieu cu (moi giao dich mot document) chua. */
    public static boolean legacyCleaned(Context context) {
        return prefs(context).getBoolean(KEY_LEGACY_CLEANED, false);
    }

    public static void setLegacyCleaned(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_LEGACY_CLEANED, value).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
