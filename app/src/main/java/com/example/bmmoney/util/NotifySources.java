package com.example.bmmoney.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cai dat cho tinh nang doc thong bao: bat tat, danh sach app duoc lang nghe, quyen he thong.
 *
 * <p>Mac dinh la tat va danh sach rong. App khong doc gi cho tot khi nguoi dung
 * tu tay bat va tu tay tick app.
 */
public final class NotifySources {

    private static final String PREF_FILE = "notify_sources";
    private static final String KEY_PACKAGES = "packages";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AI = "ai_enabled";
    private static final String SEPARATOR = "|";

    private NotifySources() {
    }

    /** Mot app trong may, dung cho hop thoai chon nguon. */
    public static final class AppItem {
        public final String packageName;
        public final String label;
        @Nullable public final Drawable icon;

        AppItem(String packageName, String label, @Nullable Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply();
    }

    /** Bat tat viec goi Gemini. Tat di thi van con duong doc bang tu khoa. */
    public static boolean aiEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AI, true);
    }

    public static void setAiEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AI, value).apply();
    }

    /** Danh sach package dang duoc lang nghe. */
    public static Set<String> watched(Context context) {
        String raw = prefs(context).getString(KEY_PACKAGES, "");
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split("\\|")) {
            String value = part.trim();
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    public static boolean isWatched(Context context, @Nullable String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        return watched(context).contains(packageName);
    }

    public static void setWatched(Context context, String packageName, boolean value) {
        Set<String> current = watched(context);
        if (value) {
            current.add(packageName);
        } else {
            current.remove(packageName);
        }
        save(context, current);
    }

    /** Ghi de toan bo danh sach, dung cho hop thoai chon nhieu app mot luot. */
    public static void replaceWatched(Context context, Set<String> packages) {
        save(context, packages);
    }

    private static void save(Context context, Set<String> packages) {
        StringBuilder out = new StringBuilder();
        for (String value : packages) {
            if (value == null || value.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(SEPARATOR);
            out.append(value.trim());
        }
        prefs(context).edit().putString(KEY_PACKAGES, out.toString()).apply();
    }

    /** App da duoc cap quyen doc thong bao trong cai dat he thong hay chua. */
    public static boolean hasAccess(Context context) {
        try {
            Set<String> allowed =
                    NotificationManagerCompat.getEnabledListenerPackages(context);
            return allowed.contains(context.getPackageName());
        } catch (Throwable error) {
            return false;
        }
    }

    /**
     * Mo trang cap quyen doc thong bao. Quyen nay khong the xin bang hop thoai,
     * nguoi dung buoc phai tu bat trong cai dat he thong.
     */
    public static void openAccessSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
                // May khong co trang cai dat nao mo duoc, khong con gi de lam.
            }
        }
    }

    /** Danh sach app co the mo tu man hinh chinh, da bo chinh app nay, sap theo ten. */
    public static List<AppItem> installed(Context context) {
        List<AppItem> out = new ArrayList<>();
        PackageManager manager = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found;
        try {
            found = manager.queryIntentActivities(launcher, 0);
        } catch (Throwable error) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        String self = context.getPackageName();
        for (ResolveInfo info : found) {
            if (info == null || info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (packageName == null || packageName.equals(self)) continue;
            if (!seen.add(packageName)) continue;
            String label;
            Drawable icon = null;
            try {
                label = String.valueOf(info.loadLabel(manager));
                icon = info.loadIcon(manager);
            } catch (Throwable error) {
                label = packageName;
            }
            if (label.trim().isEmpty()) label = packageName;
            out.add(new AppItem(packageName, label, icon));
        }
        Collections.sort(out, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem left, AppItem right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });
        return out;
    }

    /** Ten app de hien thi, tra ve chinh package khi khong tra cuu duoc. */
    public static String labelOf(Context context, String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            return String.valueOf(manager.getApplicationLabel(
                    manager.getApplicationInfo(packageName, 0)));
        } catch (Throwable error) {
            return packageName;
        }
    }

    /**
     * Tat roi bat lai thanh phan dich vu de he thong noi lai ket noi.
     *
     * <p>Can thiet vi sau khi vua duoc cap quyen, dich vu doi khi khong duoc goi
     * cho den lan khoi dong may tiep theo.
     */
    public static void rebind(Context context, Class<?> service) {
        try {
            PackageManager manager = context.getPackageManager();
            ComponentName component = new ComponentName(context, service);
            manager.setComponentEnabledSetting(component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            manager.setComponentEnabledSetting(component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable error) {
            // Khong noi lai duoc thi cung khong lam hong gi, cho lan sau.
        }
    }
}
