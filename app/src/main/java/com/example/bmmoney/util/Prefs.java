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
}
