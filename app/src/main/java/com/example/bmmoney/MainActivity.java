package com.example.bmmoney;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.ui.AddExpenseFragment;
import com.example.bmmoney.ui.AnalyticsFragment;
import com.example.bmmoney.ui.DashboardFragment;
import com.example.bmmoney.ui.SearchFragment;
import com.example.bmmoney.ui.SettingsFragment;
import com.example.bmmoney.remote.FirebaseSyncManager;

/**
 * Man hinh chinh duy nhat cua ung dung.
 * Chua 5 man moi (Trang chu / Them / Phan tich / Tim kiem / Cai dat)
 * va thanh dieu huong duoi giong ban thiet ke.
 */
public class MainActivity extends AppCompatActivity {

    public static final int TAB_HOME = 0;
    public static final int TAB_ADD = 1;
    public static final int TAB_ANALYTICS = 2;
    public static final int TAB_SEARCH = 3;
    public static final int TAB_SETTINGS = 4;

    private static final int[] NAV_IDS = {
            R.id.nav_home, R.id.nav_add, R.id.nav_analytics, R.id.nav_search, R.id.nav_settings};
    private static final int[] PILL_IDS = {
            R.id.nav_home_pill, R.id.nav_add_pill, R.id.nav_analytics_pill,
            R.id.nav_search_pill, R.id.nav_settings_pill};
    private static final int[] ICON_IDS = {
            R.id.nav_home_icon, R.id.nav_add_icon, R.id.nav_analytics_icon,
            R.id.nav_search_icon, R.id.nav_settings_icon};
    private static final int[] LABEL_IDS = {
            R.id.nav_home_label, R.id.nav_add_label, R.id.nav_analytics_label,
            R.id.nav_search_label, R.id.nav_settings_label};
    private static final int[] DOT_IDS = {
            R.id.nav_home_dot, R.id.nav_add_dot, R.id.nav_analytics_dot,
            R.id.nav_search_dot, R.id.nav_settings_dot};

    private int current = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < NAV_IDS.length; i++) {
            final int index = i;
            findViewById(NAV_IDS[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(index);
                }
            });
        }
        showTab(TAB_HOME);

        // Dong bo du lieu tu cloud (neu da cau hinh Firebase)
        try {
            new FirebaseSyncManager(this).downloadToLocal(new Runnable() {
                @Override
                public void run() {
                    Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (f instanceof DashboardFragment) {
                        ((DashboardFragment) f).reload();
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Chuyen man hinh + cap nhat trang thai thanh dieu huong. */
    public void showTab(int index) {
        if (current == index) return;
        current = index;

        Fragment fragment;
        switch (index) {
            case TAB_ADD: fragment = new AddExpenseFragment(); break;
            case TAB_ANALYTICS: fragment = new AnalyticsFragment(); break;
            case TAB_SEARCH: fragment = new SearchFragment(); break;
            case TAB_SETTINGS: fragment = new SettingsFragment(); break;
            default: fragment = new DashboardFragment(); break;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .commit();

        int activeColor = ContextCompat.getColor(this, R.color.dark_green);
        int inactiveColor = Color.parseColor("#80606C38");

        for (int i = 0; i < NAV_IDS.length; i++) {
            boolean active = i == index;
            View pill = findViewById(PILL_IDS[i]);
            pill.setBackgroundResource(active ? R.drawable.bg_nav_pill : 0);

            ImageView icon = findViewById(ICON_IDS[i]);
            icon.setColorFilter(active ? activeColor : inactiveColor);

            TextView label = findViewById(LABEL_IDS[i]);
            label.setTextColor(active ? activeColor : inactiveColor);

            findViewById(DOT_IDS[i]).setVisibility(active ? View.VISIBLE : View.GONE);

            if (active) {
                pill.setScaleX(0.9f);
                pill.setScaleY(0.9f);
                pill.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (current != TAB_HOME) {
            showTab(TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }
}
