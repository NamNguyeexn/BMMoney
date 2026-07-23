package com.example.bmmoney;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public class ThemeManager {
    public static final String PREF = "app_theme";
    public static final String KEY_PRIMARY = "primary";
    public static final String KEY_SECONDARY = "secondary";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_BG1 = "bg1";
    public static final String KEY_BG2 = "bg2";

    public static final String[][] PALETTES = new String[][]{
            {"Purple Mint", "#101828", "#344054", "#7F56D9", "#12B76A", "#F04438"},
            {"Coolors Sunset", "#0F172A", "#334155", "#FF6B6B", "#FFD166", "#06D6A0"},
            {"Ocean Blue", "#001219", "#005F73", "#0A9396", "#94D2BD", "#EE9B00"},
            {"Candy Pop", "#231942", "#5E548E", "#9F86C0", "#E0B1CB", "#BE95C4"},
            {"Forest Money", "#081C15", "#1B4332", "#2D6A4F", "#52B788", "#D8F3DC"},
            {"Warm Finance", "#2B2D42", "#8D99AE", "#EF233C", "#FCA311", "#06D6A0"}
    };

    public static void savePalette(Context context, String[] p) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_BG1, p[1])
                .putString(KEY_BG2, p[2])
                .putString(KEY_PRIMARY, p[3])
                .putString(KEY_SECONDARY, p[4])
                .putString(KEY_ACCENT, p[5])
                .apply();
    }

    public static int color(Context context, String key, String fallback) {
        String value = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, fallback);
        try { return Color.parseColor(value); } catch (Exception e) { return Color.parseColor(fallback); }
    }

    public static void apply(Activity activity) {
        int bg1 = color(activity, KEY_BG1, "#101828");
        int bg2 = color(activity, KEY_BG2, "#344054");
        int primary = color(activity, KEY_PRIMARY, "#7F56D9");
        int secondary = color(activity, KEY_SECONDARY, "#12B76A");
        int accent = color(activity, KEY_ACCENT, "#F04438");

        View root = activity.findViewById(android.R.id.content);
        if (root != null) {
            GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bg1, bg2});
            root.setBackground(gradient);
        }
        activity.getWindow().setStatusBarColor(bg1);
        activity.getWindow().setNavigationBarColor(bg1);

        tintIfExists(activity, "fabAdd", primary);
        tintIfExists(activity, "fabCenterAdd", secondary);
        textColorIfExists(activity, "navHome", primary);
        textColorIfExists(activity, "navCalendar", primary);
        textColorIfExists(activity, "navReport", primary);
        textColorIfExists(activity, "navSettings", primary);
        textColorIfExists(activity, "tvIncome", secondary);
        textColorIfExists(activity, "tvExpense", accent);
    }

    private static void tintIfExists(Activity a, String idName, int color) {
        int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
        View v = a.findViewById(id);
        if (v instanceof com.google.android.material.floatingactionbutton.FloatingActionButton) {
            ((com.google.android.material.floatingactionbutton.FloatingActionButton) v).setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private static void textColorIfExists(Activity a, String idName, int color) {
        int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
        View v = a.findViewById(id);
        if (v instanceof TextView) ((TextView) v).setTextColor(color);
    }
}
