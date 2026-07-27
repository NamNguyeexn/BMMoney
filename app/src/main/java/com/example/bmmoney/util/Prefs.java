package com.example.bmmoney.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Luu ten nguoi dung, ngan sach thang va thoi diem sao luu. */
public final class Prefs {

    private static final String FILE = "bmm_settings";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_BUDGET = "monthly_budget";
    private static final String KEY_BACKUP = "last_backup";

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
}
