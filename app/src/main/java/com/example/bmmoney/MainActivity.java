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
import com.example.bmmoney.ui.CalendarFragment;
import com.example.bmmoney.ui.DashboardFragment;
import com.example.bmmoney.ui.SearchFragment;
import com.example.bmmoney.ui.SettingsFragment;
import com.example.bmmoney.ui.WelcomeDialog;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Reminders;

/**
 * Man hinh chinh duy nhat cua ung dung.
 * Chua 6 man (Trang chu / Them / Lich / Phan tich / Tim kiem / Cai dat)
 * va thanh dieu huong duoi giong ban thiet ke.
 */
public class MainActivity extends AppCompatActivity {

    public static final int TAB_HOME = 0;
    public static final int TAB_ADD = 1;
    /** Ban va 02/08: man Lich, dat giua Them va Phan tich. */
    public static final int TAB_CALENDAR = 2;
    public static final int TAB_ANALYTICS = 3;
    public static final int TAB_SEARCH = 4;
    public static final int TAB_SETTINGS = 5;

    // Nam mang duoi day phai cung thu tu voi cac hang so TAB_* o tren
    private static final int[] NAV_IDS = {
            R.id.nav_home, R.id.nav_add, R.id.nav_calendar,
            R.id.nav_analytics, R.id.nav_search, R.id.nav_settings};
    private static final int[] PILL_IDS = {
            R.id.nav_home_pill, R.id.nav_add_pill, R.id.nav_calendar_pill,
            R.id.nav_analytics_pill, R.id.nav_search_pill, R.id.nav_settings_pill};
    private static final int[] ICON_IDS = {
            R.id.nav_home_icon, R.id.nav_add_icon, R.id.nav_calendar_icon,
            R.id.nav_analytics_icon, R.id.nav_search_icon, R.id.nav_settings_icon};
    private static final int[] LABEL_IDS = {
            R.id.nav_home_label, R.id.nav_add_label, R.id.nav_calendar_label,
            R.id.nav_analytics_label, R.id.nav_search_label, R.id.nav_settings_label};
    private static final int[] DOT_IDS = {
            R.id.nav_home_dot, R.id.nav_add_dot, R.id.nav_calendar_dot,
            R.id.nav_analytics_dot, R.id.nav_search_dot, R.id.nav_settings_dot};

    private int current = -1;
    /** Chỉ sao lưu tối đa một lần cho mỗi phiên mở app. */
    private boolean backupDone = false;

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

        // Lan dau mo app: hien popup Xin chao de thiet lap thong tin co ban
        if (savedInstanceState == null && !Prefs.onboarded(this)) {
            WelcomeDialog.show(this, new Runnable() {
                @Override
                public void run() {
                    refreshCurrentTab();
                }
            });
        }

        // Dat lai bao thuc nhac nho + lich sao luu buoi sang
        try {
            Reminders.rescheduleAll(getApplicationContext());
            AutoBackup.scheduleDaily(getApplicationContext());
        } catch (Throwable ignored) {
        }

        // Neu hom nay chua sao luu duoc lan nao thi sao luu bu ngay bay gio.
        // Khong tu dong keo du lieu cloud ve: viec do se xoa du lieu duoi may
        // nen chi lam khi nguoi dung bam Dong bo hoac vua dang nhap.
        if (savedInstanceState == null && !backupDone && FirebaseSyncManager.isSignedIn()) {
            backupDone = true;
            try {
                AutoBackup.runIfDue(getApplicationContext(), null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Nap lai man dang mo (dung sau khi doi thiet lap o popup Xin chao). */
    private void refreshCurrentTab() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof DashboardFragment) {
            ((DashboardFragment) f).reload();
        } else if (f instanceof SettingsFragment) {
            ((SettingsFragment) f).reload();
        } else if (f instanceof CalendarFragment) {
            ((CalendarFragment) f).reload();
        }
    }

    /** Chuyen man hinh + cap nhat trang thai thanh dieu huong. */
    public void showTab(int index) {
        if (current == index) return;
        current = index;

        Fragment fragment;
        switch (index) {
            case TAB_ADD: fragment = new AddExpenseFragment(); break;
            case TAB_CALENDAR: fragment = new CalendarFragment(); break;
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
