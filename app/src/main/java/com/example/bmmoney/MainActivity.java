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
import com.example.bmmoney.ui.FloatingAddButton;
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

    /**
     * THANH DIEU HUONG CHI CO NAM MUC.
     *
     * <p>Muc "Them" da bi go khoi thanh duoi (nguoi dung them khoan bang nut + noi keo
     * tha, xem {@link FloatingAddButton}). Cac mang duoi day PHAI khop chinh xac voi
     * {@code view_bottom_nav.xml}: truoc day chung van con giu {@code nav_add}, ma id
     * do khong con ton tai trong bo cuc nen thu tu bi lech mot bac - bam "Lich" lai mo
     * man Them, va man Trang chu khong duoc nap lai dung luc.</p>
     *
     * <p>{@link #NAV_TABS} noi tung o tren thanh voi hang so TAB_* tuong ung, nen sau
     * nay them hay bot mot muc chi phai sua mang nay chu khong dung toi cac hang so.</p>
     */
    private static final int[] NAV_IDS = {
            R.id.nav_home, R.id.nav_calendar,
            R.id.nav_analytics, R.id.nav_search, R.id.nav_settings};
    private static final int[] PILL_IDS = {
            R.id.nav_home_pill, R.id.nav_calendar_pill,
            R.id.nav_analytics_pill, R.id.nav_search_pill, R.id.nav_settings_pill};
    private static final int[] ICON_IDS = {
            R.id.nav_home_icon, R.id.nav_calendar_icon,
            R.id.nav_analytics_icon, R.id.nav_search_icon, R.id.nav_settings_icon};
    private static final int[] LABEL_IDS = {
            R.id.nav_home_label, R.id.nav_calendar_label,
            R.id.nav_analytics_label, R.id.nav_search_label, R.id.nav_settings_label};
    private static final int[] DOT_IDS = {
            R.id.nav_home_dot, R.id.nav_calendar_dot,
            R.id.nav_analytics_dot, R.id.nav_search_dot, R.id.nav_settings_dot};

    /** Man hinh ma tung o tren thanh dieu huong se mo. */
    private static final int[] NAV_TABS = {
            TAB_HOME, TAB_CALENDAR, TAB_ANALYTICS, TAB_SEARCH, TAB_SETTINGS};

    /** Nut + noi. Giu lai de an di dung luc man Them dang mo. */
    private View fabAdd;

    private int current = -1;
    /** Chỉ sao lưu tối đa một lần cho mỗi phiên mở app. */
    private boolean backupDone = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < NAV_IDS.length; i++) {
            final int tab = NAV_TABS[i];
            View item = findViewById(NAV_IDS[i]);
            if (item == null) continue;
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(tab);
                }
            });
        }
        // Nut + noi thuoc ve Activity chu khong thuoc man hinh nao, nen no song sot
        // qua moi lan doi tab va giu nguyen vi tri nguoi dung da keo toi.
        fabAdd = findViewById(R.id.fab_add);
        FloatingAddButton.attach(fabAdd, () -> showTab(TAB_ADD));

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

    /**
     * Ban va 04/08: dat lai bao thuc moi lan quay lai app, khong chi luc onCreate.
     *
     * <p>onCreate chi chay mot lan cho moi lan mo nguoi (cold start). Neu app nam lai
     * trong bo nho nhieu ngay, hoac bi cai de len khi cap nhat (moi lan cai lai la he
     * thong xoa sach bao thuc cua app), thi bao thuc khong bao gio duoc dat lai.</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        try {
            Reminders.rescheduleAll(getApplicationContext());
            AutoBackup.scheduleDaily(getApplicationContext());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Nap lai man dang mo.
     *
     * <p>Ban va 03/08: mo public va bo sung Phan tich / Tim kiem de sau khi dong bo
     * hay khoi phuc du lieu thi man hinh dang xem cap nhat ngay, khong phai mo lai app.</p>
     */
    public void refreshCurrentTab() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof DashboardFragment) {
            ((DashboardFragment) f).reload();
        } else if (f instanceof SettingsFragment) {
            ((SettingsFragment) f).reload();
        } else if (f instanceof CalendarFragment) {
            ((CalendarFragment) f).reload();
        } else if (f instanceof AnalyticsFragment) {
            ((AnalyticsFragment) f).reload();
        } else if (f instanceof SearchFragment) {
            ((SearchFragment) f).reload();
        }
    }

    /**
     * Chuyen man hinh + cap nhat trang thai thanh dieu huong.
     *
     * <p>Bam lai dung o dang mo: khong dung lai nua ma NAP LAI man do. Truoc day ham
     * tra ve ngay, nen sau khi luu mot khoan chi va quay ve Trang chu (cung mot tab)
     * thi man hinh giu nguyen so lieu cu - giao dich vua them khong hien ra.</p>
     */
    public void showTab(int index) {
        if (current == index) {
            refreshCurrentTab();
            return;
        }
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

        // An nut + khi man Them dang mo. De no lai thi moi lan bam nhu hua hen mo them
        // mot thu gi nua, trong khi nguoi dung dang o dung cho no dan tram.
        FloatingAddButton.setShown(fabAdd, index != TAB_ADD);

        int activeColor = ContextCompat.getColor(this, R.color.dark_green);
        int inactiveColor = Color.parseColor("#80606C38");

        for (int i = 0; i < NAV_IDS.length; i++) {
            // Man Them khong co o rieng tren thanh: luc do sang o Trang chu, vi day la
            // noi nguoi dung se quay ve ngay sau khi luu.
            boolean active = NAV_TABS[i] == index
                    || (index == TAB_ADD && NAV_TABS[i] == TAB_HOME);

            View pill = findViewById(PILL_IDS[i]);
            if (pill != null) {
                pill.setBackgroundResource(active ? R.drawable.bg_nav_pill : 0);
                if (active) {
                    pill.setScaleX(0.9f);
                    pill.setScaleY(0.9f);
                    pill.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                }
            }

            ImageView icon = findViewById(ICON_IDS[i]);
            if (icon != null) icon.setColorFilter(active ? activeColor : inactiveColor);

            TextView label = findViewById(LABEL_IDS[i]);
            if (label != null) label.setTextColor(active ? activeColor : inactiveColor);

            View dot = findViewById(DOT_IDS[i]);
            if (dot != null) dot.setVisibility(active ? View.VISIBLE : View.GONE);
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
