package com.example.bmmoney.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.remote.ReminderReceiver;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Reminders;
import com.example.bmmoney.util.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * M\u00e0n C\u00e0i \u0111\u1eb7t: h\u1ed3 s\u01a1, t\u00f9y ch\u1ecdn chu k\u1ef3 \u2013 nh\u1eafc ghi ch\u00fa \u2013 c\u00e1c ng\u01b0\u1ee1ng ph\u1ea7n tr\u0103m,
 * danh m\u1ee5c t\u00f9y ch\u1ec9nh v\u00e0 sao l\u01b0u d\u1eef li\u1ec7u.
 */
public class SettingsFragment extends Fragment {

    private static final int REQ_NOTIFICATION = 7001;
    private static final int REQ_GOOGLE = 7002;

    private GoogleSignInClient googleClient;

    private View root;
    private SwipeRefreshLayout refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);
        refresh = Refresh.setup(root, R.id.refresh_settings, this::reload);

        bindProfile();

        ViewUtils.onClick(root, R.id.tv_cycle_day, v ->
                CycleDialog.show(getContext(), this::reload));

        ViewUtils.onClick(root, R.id.tv_warn_percent, v ->
                PercentDialog.show(getContext(), "Ng\u01b0\u1ee1ng chi ti\u00eau",
                        "C\u1ea3nh b\u00e1o \u1edf Trang ch\u1ee7 khi chi ti\u00eau v\u01b0\u1ee3t m\u1ee9c n\u00e0y c\u1ee7a ng\u00e2n s\u00e1ch",
                        10, 200, Prefs.warnPercent(getContext()), percent -> {
                            Prefs.setWarnPercent(getContext(), percent);
                            reload();
                        }));

        ViewUtils.onClick(root, R.id.tv_big_percent, v ->
                PercentDialog.show(getContext(), "M\u1ed1c chi ti\u00eau l\u1edbn",
                        "Giao d\u1ecbch chi\u1ebfm t\u1eeb m\u1ee9c n\u00e0y c\u1ee7a t\u1ed5ng chi trong k\u1ef3 s\u1ebd \u0111\u01b0\u1ee3c \u0111\u00e1nh d\u1ea5u",
                        1, 99, Prefs.bigPercent(getContext()), percent -> {
                            Prefs.setBigPercent(getContext(), percent);
                            reload();
                        }));

        ViewUtils.onClick(root, R.id.btn_add_reminder, v -> {
            askNotificationPermission();
            ReminderDialog.show(getContext(), null, new ReminderDialog.OnResult() {
                @Override
                public void onSave(Reminders.Item item) {
                    List<Reminders.Item> list = Reminders.all(getContext());
                    list.add(item);
                    sortAndSave(list);
                }

                @Override
                public void onDelete() {
                }
            });
        });

        // Ban va 04/08: nhan giu nut + de xem vi sao loi nhac khong hien
        ViewUtils.onClick(root, R.id.tv_alarm_mode, v -> pickAlarmMode());

        ViewUtils.onLongClick(root, R.id.btn_add_reminder, v -> {
            showReminderCheck();
            return true;
        });

        allowInnerScroll();

        ViewUtils.onClick(root, R.id.btn_add_category, v -> editCategory(-1));
        ViewUtils.onClick(root, R.id.btn_google_auth, v -> toggleGoogleAccount());
        ViewUtils.onClick(root, R.id.btn_backup_now, v -> backup());
        ViewUtils.onClick(root, R.id.btn_sync_now, v -> sync());
        // Ban va 03/08 (sua tiep): nhan giu nut Dong bo de xem chi tiet trang thai,
        // biet ngay dang vuong o dang nhap, mang hay moc thoi gian.
        ViewUtils.onLongClick(root, R.id.btn_sync_now, v -> {
            showSyncStatus();
            return true;
        });
        // Ban va 03/08 (bo sung): duong xoa han ban sao luu tren cloud
        ViewUtils.onClick(root, R.id.btn_delete_cloud, v -> deleteCloudBackup());

        reload();
        return root;
    }

    @Override
    public void onDestroyView() {
        refresh = null;
        root = null;
        super.onDestroyView();
    }

    /**
     * Khung danh m\u1ee5c n\u1eb1m b\u00ean trong m\u1ed9t ScrollView kh\u00e1c n\u00ean m\u00e0n cha s\u1ebd \u0111o\u1ea1t thao t\u00e1c cu\u1ed9n.
     * \u1ede \u0111\u00e2y ta y\u00eau c\u1ea7u m\u00e0n cha nh\u01b0\u1eddng l\u1ea1i s\u1ef1 ki\u1ec7n ch\u1ea1m khi ng\u00f3n tay \u0111ang \u1edf tr\u00ean khung n\u00e0y.
     */
    private void allowInnerScroll() {
        final View scroll = root.findViewById(R.id.scroll_categories);
        if (scroll == null) return;

        scroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                boolean finished = action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL;

                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(!finished);
                }
                if (refresh != null) {
                    refresh.setEnabled(finished);
                }
                return false;
            }
        });
    }

    // ------------------------------------------------------------- h\u1ed3 s\u01a1
    private void bindProfile() {
        final EditText name = root.findViewById(R.id.edt_name);
        final EditText budget = root.findViewById(R.id.edt_budget);

        name.setText(Prefs.userName(getContext()));
        budget.setText(String.valueOf((long) Prefs.budget(getContext())));

        name.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void changed(String value) {
                if (getContext() == null) return;
                Prefs.setUserName(getContext(), value.trim().isEmpty() ? "b\u1ea1n" : value.trim());
                avatar(value);
            }
        });

        budget.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void changed(String value) {
                if (getContext() == null) return;
                String digits = value.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    Prefs.setBudget(getContext(), Double.parseDouble(digits));
                }
            }
        });

        avatar(Prefs.userName(getContext()));
    }

    private void avatar(String name) {
        String value = name == null || name.trim().isEmpty() ? "B" : name.trim();
        text(R.id.tv_avatar, value.substring(0, 1).toUpperCase(Locale.getDefault()));
    }

    // ------------------------------------------------------------- n\u1ea1p l\u1ea1i to\u00e0n m\u00e0n
    /**
     * Ban va 04/08: roi man Cai dat thi hen mot lan sao luu.
     *
     * <p>Dat o day thay vi rai vao muời cho luu rieng le: nguoi dung thuong sua
     * lien tiep nhieu o (ten, ngan sach, nguong, danh muc, gio nhac) roi moi thoat,
     * nen mot lan hen khi thoat la du va khong ghi cloud lien tuc.</p>
     *
     * <p>scheduleSoon tu bo qua neu chua dang nhap Google, va no goi touchLocal
     * truoc khi kiem tra dieu do.</p>
     */
    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            AutoBackup.scheduleSoon(getContext().getApplicationContext());
        }
    }

    public void reload() {
        if (root == null || getContext() == null) return;

        text(R.id.tv_cycle_day, Cycle.cycleDayLabel(getContext()));
        text(R.id.tv_warn_percent, Prefs.warnPercent(getContext()) + "%");
        text(R.id.tv_alarm_mode, Prefs.strongAlarm(getContext())
                ? "M\u1ee9c \u0111\u1ed3ng h\u1ed3 h\u1ec7 th\u1ed1ng \u2014 ch\u1eafc ch\u1eafn nh\u1ea5t"
                : "Ch\u1ebf \u0111\u1ed9 nh\u1eb9 \u2014 kh\u00f4ng c\u00f3 bi\u1ec3u t\u01b0\u1ee3ng \u0111\u1ed3ng h\u1ed3");
        text(R.id.tv_big_percent, Prefs.bigPercent(getContext()) + "%");
        text(R.id.tv_app_version, "Phi\u00ean b\u1ea3n 2.2");

        long backup = Prefs.lastBackup(getContext());
        text(R.id.tv_last_backup, backup <= 0 ? "Ch\u01b0a sao l\u01b0u l\u1ea7n n\u00e0o"
                : "L\u1ea7n cu\u1ed1i: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(backup)));

        bindAccount();
        buildReminders();
        buildCategories();

        if (refresh != null) refresh.setRefreshing(false);
    }

    // ------------------------------------------------------------- nh\u1eafc ghi ch\u00fa
    private void buildReminders() {
        LinearLayout container = root.findViewById(R.id.container_reminders);
        if (container == null) return;
        container.removeAllViews();

        final List<Reminders.Item> list = Reminders.all(getContext());
        ViewUtils.setVisibility(root, R.id.tv_no_reminder, list.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final Reminders.Item item = list.get(i);
            View row = inflater.inflate(R.layout.item_reminder, container, false);
            ((TextView) row.findViewById(R.id.tv_reminder_time)).setText(item.time());
            ((TextView) row.findViewById(R.id.tv_reminder_label)).setText(
                    item.label.isEmpty() ? "Nh\u1eafc ghi chi ti\u00eau" : item.label);
            row.setOnClickListener(v -> ReminderDialog.show(getContext(), item,
                    new ReminderDialog.OnResult() {
                        @Override
                        public void onSave(Reminders.Item updated) {
                            List<Reminders.Item> current = Reminders.all(getContext());
                            Reminders.cancel(getContext(), item);
                            if (index < current.size()) current.set(index, updated);
                            sortAndSave(current);
                        }

                        @Override
                        public void onDelete() {
                            List<Reminders.Item> current = Reminders.all(getContext());
                            Reminders.cancel(getContext(), item);
                            if (index < current.size()) current.remove(index);
                            sortAndSave(current);
                        }
                    }));
            container.addView(row);
        }
    }

    private void sortAndSave(List<Reminders.Item> list) {
        if (getContext() == null) return;
        Collections.sort(list, new Comparator<Reminders.Item>() {
            @Override
            public int compare(Reminders.Item a, Reminders.Item b) {
                return (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute);
            }
        });
        Reminders.save(getContext(), list);
        reload();
    }

    /**
     * Ban va 04/08: bang chan doan loi nhac.
     *
     * <p>Bao thuc dat bang AlarmManager thi khong the kiem tra bang cach doc code:
     * he thong co the ha cap bao thuc, khoa quyen thong bao, hoac dong lanh app
     * duoi nen. Ba dieu do deu cho ra cung mot trieu chung "khong thay thong bao",
     * nen can mot cho de xem thang tung dieu kien mot.</p>
     */
    /**
     * Ban va 04/08: chon do uu tien bao thuc.
     *
     * <p>Ca hai che do deu khong ton pin trong luc cho - app khong chay. Khac nhau o
     * cho he thong co duoc phep hoan bao thuc lai hay khong.</p>
     */
    private void pickAlarmMode() {
        final Context ctx = getContext();
        if (ctx == null) return;

        ConfirmDialog.choose(ctx, "\u23f0", "\u0110\u1ed9 \u01b0u ti\u00ean b\u00e1o th\u1ee9c", "M\u1ee9c \u0111\u1ed3ng h\u1ed3 h\u1ec7 th\u1ed1ng: b\u00e1o th\u1ee9c \u0111\u01b0\u1ee3c \u0111\u1ed1i x\u1eed nh\u01b0 b\u00e1o th\u1ee9c b\u00e1o gi\u1edd. H\u1ec7 th\u1ed1ng kh\u00f4ng \u0111\u01b0\u1ee3c ho\u00e3n hay \u0111\u00f3ng b\u0103ng n\u00f3, k\u1ec3 c\u1ea3 khi m\u00e1y \u0111\u1ee7 s\u00e2u trong ch\u1ebf \u0111\u1ed9 ng\u1ee7. \u0110\u1ed5i l\u1ea1i, thanh tr\u1ea1ng th\u00e1i hi\u1ec7n m\u1ed9t bi\u1ec3u t\u01b0\u1ee3ng \u0111\u1ed3ng h\u1ed3 nh\u1ecf.\n\nCh\u1ebf \u0111\u1ed9 nh\u1eb9: kh\u00f4ng c\u00f3 bi\u1ec3u t\u01b0\u1ee3ng, nh\u01b0ng h\u1ec7 th\u1ed1ng \u0111\u01b0\u1ee3c ph\u00e9p d\u1ed3n b\u00e1o th\u1ee9c sang c\u1eeda s\u1ed5 b\u1ea3o tr\u00ec, c\u00f3 th\u1ec3 tr\u1ec5 h\u00e0ng gi\u1edd.\n\nC\u1ea3 hai \u0111\u1ec1u KH\u00d4NG t\u1ed1n pin trong l\u00fac ch\u1edd: app kh\u00f4ng h\u1ec1 ch\u1ea1y.",
                "M\u1ee9c \u0111\u1ed3ng h\u1ed3 h\u1ec7 th\u1ed1ng",
                () -> {
                    Prefs.setStrongAlarm(ctx, true);
                    Reminders.rescheduleAll(ctx);
                    reload();
                    Notice.success(getView(), "\u0110\u00e3 chuy\u1ec3n sang m\u1ee9c \u0111\u1ed3ng h\u1ed3 h\u1ec7 th\u1ed1ng");
                },
                "Ch\u1ebf \u0111\u1ed9 nh\u1eb9",
                () -> {
                    Prefs.setStrongAlarm(ctx, false);
                    Reminders.rescheduleAll(ctx);
                    reload();
                    Notice.info(getView(), "\u0110\u00e3 chuy\u1ec3n sang ch\u1ebf \u0111\u1ed9 nh\u1eb9");
                });
    }

    private void showReminderCheck() {
        final Context ctx = getContext();
        if (ctx == null) return;

        boolean notif = Reminders.notificationsEnabled(ctx);
        boolean exact = Reminders.canScheduleExact(ctx);
        boolean battery = Reminders.ignoringBattery(ctx);
        boolean clock = Reminders.usingAlarmClock(ctx);
        int count = Reminders.all(ctx).size();
        long next = Reminders.nextTrigger(ctx);

        StringBuilder sb = new StringBuilder();
        if (count == 0) {
            sb.append("Ch\u01b0a \u0111\u1eb7t m\u1ed1c gi\u1edd nh\u1eafc n\u00e0o.");
        } else {
            sb.append(count).append(" m\u1ed1c gi\u1edd \u0111ang b\u1eadt.");
            if (next > 0) {
                sb.append(" L\u1ea7n nh\u1eafc k\u1ebf ti\u1ebfp: ")
                        .append(new SimpleDateFormat("HH:mm dd/MM", new Locale("vi"))
                                .format(new Date(next)));
            }
        }
        sb.append("\n\n");
        sb.append(notif ? "\u2713 Quy\u1ec1n th\u00f4ng b\u00e1o: \u0111ang b\u1eadt" : "\u2717 Quy\u1ec1n th\u00f4ng b\u00e1o: \u0110ANG T\u1eaeT").append("\n");
        sb.append(exact ? "\u2713 B\u00e1o th\u1ee9c \u0111\u00fang gi\u1edd: \u0111\u01b0\u1ee3c ph\u00e9p" : "\u2717 B\u00e1o th\u1ee9c \u0111\u00fang gi\u1edd: B\u1eca CH\u1eb6N (l\u1eddi nh\u1eafc c\u00f3 th\u1ec3 tr\u1ec5 h\u00e0ng gi\u1edd)").append("\n");
        sb.append(clock ? "\u2713 \u0110\u1ed9 \u01b0u ti\u00ean: m\u1ee9c \u0111\u1ed3ng h\u1ed3 h\u1ec7 th\u1ed1ng (h\u1ec7 th\u1ed1ng kh\u00f4ng \u0111\u01b0\u1ee3c ho\u00e3n)" : "\u25cb \u0110\u1ed9 \u01b0u ti\u00ean: ch\u1ebf \u0111\u1ed9 nh\u1eb9 (c\u00f3 th\u1ec3 b\u1ecb ho\u00e3n trong Doze)").append("\n");
        sb.append(battery ? "\u2713 T\u1ed1i \u01b0u pin: \u0111\u00e3 mi\u1ec5n cho app" : "\u25cb T\u1ed1i \u01b0u pin: ch\u01b0a mi\u1ec5n (kh\u00f4ng c\u1ea7n thi\u1ebft \u1edf m\u1ee9c \u0111\u1ed3ng h\u1ed3)");
        if (!battery && !clock) sb.append("\n\n" + "\u0110\u00e2y th\u01b0\u1eddng l\u00e0 nguy\u00ean nh\u00e2n khi \u1edf ch\u1ebf \u0111\u1ed9 nh\u1eb9.");

        ConfirmDialog.choose(ctx, "\u23f0", "Ki\u1ec3m tra l\u1eddi nh\u1eafc", sb.toString(),
                battery ? "M\u1edf c\u00e0i \u0111\u1eb7t app" : "M\u1edf c\u00e0i \u0111\u1eb7t pin",
                () -> {
                    if (battery) {
                        Reminders.openAppSettings(ctx);
                    } else {
                        Reminders.openBatterySettings(ctx);
                    }
                },
                "Th\u1eed th\u00f4ng b\u00e1o ngay",
                () -> ReminderReceiver.push(ctx, 4321, "Th\u1eed th\u00f4ng b\u00e1o \u2014 l\u1eddi nh\u1eafc \u0111ang ho\u1ea1t \u0111\u1ed9ng b\u00ecnh th\u01b0\u1eddng."));
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || getContext() == null) return;
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    // ------------------------------------------------------------- danh m\u1ee5c
    private void buildCategories() {
        final LinearLayout container = root.findViewById(R.id.container_categories);
        if (container == null || getContext() == null) return;

        if (!Categories.isReady()) {
            // Ban sao danh muc chua nap xong: cho roi ve lai, thay vi hien "0 muc"
            Categories.whenReady(getContext(), this::buildCategories);
            return;
        }

        final List<Categories.Item> list = Categories.all(getContext());
        text(R.id.tv_cat_count, list.size() + " m\u1ee5c");
        ViewUtils.setVisibility(root, R.id.tv_no_category, list.isEmpty() ? View.VISIBLE : View.GONE);
        View scrollHint = root.findViewById(R.id.tv_cat_scroll_hint);
        if (scrollHint != null) scrollHint.setVisibility(list.size() > 5 ? View.VISIBLE : View.GONE);

        // N\u1ea1p t\u1ed5ng chi c\u1ee7a k\u1ef3 hi\u1ec7n t\u1ea1i \u0111\u1ec3 m\u1ed7i danh m\u1ee5c c\u00f3 th\u00eam d\u00f2ng ph\u1ee5 h\u1eefu \u00edch
        final TransactionDao dao = AppDatabase.dao(getContext());
        final long[] bounds = Cycle.bounds(Prefs.cycleDay(getContext()), System.currentTimeMillis(), 0);
        Db.load(() -> {
            Map<String, Double> map = new HashMap<>();
            List<CategoryTotal> totals = dao.getExpenseByCategoryInRangeSkip(bounds[0], bounds[1], Stats.CATEGORY_BALANCE);
            if (totals != null) {
                for (CategoryTotal c : totals) {
                    if (c.category != null) map.put(c.category, c.total);
                }
            }
            return map;
        }, map -> {
            if (root == null) return;
            renderCategories(container, list, map == null ? new HashMap<>() : map);
        });
    }

    private void renderCategories(LinearLayout container, final List<Categories.Item> list,
                                  Map<String, Double> spentByCategory) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            Categories.Item item = list.get(i);
            View row = inflater.inflate(R.layout.item_category, container, false);

            ((TextView) row.findViewById(R.id.tv_cat_emoji)).setText(item.emoji);
            ((TextView) row.findViewById(R.id.tv_cat_name)).setText(item.name);

            Double spentValue = spentByCategory.get(item.name);
            final double spent = spentValue == null ? 0d : spentValue;
            ((TextView) row.findViewById(R.id.tv_cat_hint)).setText(spent > 0
                    ? "\u0110\u00e3 chi " + Money.vnd(spent) + " trong k\u1ef3 n\u00e0y"
                    : "Ch\u01b0a d\u00f9ng trong k\u1ef3 n\u00e0y");

            ViewUtils.onClick(row, R.id.box_cat_info, v -> editCategory(index));

            View up = row.findViewById(R.id.btn_cat_up);
            View down = row.findViewById(R.id.btn_cat_down);
            setEnabledLook(up, index > 0);
            setEnabledLook(down, index < list.size() - 1);
            up.setOnClickListener(v -> moveCategory(index, -1));
            down.setOnClickListener(v -> moveCategory(index, 1));

            ViewUtils.onClick(row, R.id.btn_cat_remove, v -> confirmDeleteCategory(index, spent));
            container.addView(row);
        }
    }

    private void setEnabledLook(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.25f);
    }

    /** \u0110\u1ed5i th\u1ee9 t\u1ef1 danh m\u1ee5c, th\u1ee9 t\u1ef1 n\u00e0y d\u00f9ng lu\u00f4n cho m\u00e0n Th\u00eam giao d\u1ecbch. */
    private void moveCategory(int index, int direction) {
        if (getContext() == null) return;
        List<Categories.Item> list = Categories.all(getContext());
        int target = index + direction;
        if (index < 0 || index >= list.size() || target < 0 || target >= list.size()) return;

        Categories.Item moving = list.remove(index);
        list.add(target, moving);
        // Ve lai SAU khi ban sao duoc lam moi, neu khong van la danh sach cu
        Categories.save(getContext(), list, this::buildCategories);
    }

    private void confirmDeleteCategory(final int index, double spent) {
        if (getContext() == null) return;
        final List<Categories.Item> list = Categories.all(getContext());
        if (index < 0 || index >= list.size()) return;

        String message = "Danh m\u1ee5c \u201c" + list.get(index).name + "\u201d s\u1ebd kh\u00f4ng c\u00f2n hi\u1ec7n khi ghi chi ti\u00eau.";
        if (spent > 0) {
            message += "\n\nK\u1ef3 n\u00e0y \u0111\u00e3 ghi " + Money.vnd(spent) + " cho danh m\u1ee5c n\u00e0y. "
                    + "C\u00e1c giao d\u1ecbch c\u0169 v\u1eabn \u0111\u01b0\u1ee3c gi\u1eef nguy\u00ean.";
        }

        ConfirmDialog.show(requireContext(),
                "\u2715",
                "X\u00f3a danh m\u1ee5c?",
                message,
                "X\u00f3a",
                () -> {
                    list.remove(index);
                    Categories.save(requireContext(), list, this::buildCategories);
                });
    }

    /** index = -1 l\u00e0 th\u00eam m\u1edbi. */
    private void editCategory(final int index) {
        if (getContext() == null) return;
        final Context context = getContext();
        final List<Categories.Item> list = Categories.all(context);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_category, null, false);
        final EditText emoji = view.findViewById(R.id.edt_cat_emoji);
        final EditText name = view.findViewById(R.id.edt_cat_name);
        View delete = view.findViewById(R.id.btn_cat_delete);

        if (index >= 0 && index < list.size()) {
            emoji.setText(list.get(index).emoji);
            name.setText(list.get(index).name);
            name.setSelection(name.getText().length());
            ((TextView) view.findViewById(R.id.tv_dialog_title)).setText("S\u1eeda danh m\u1ee5c");
        } else {
            ((TextView) view.findViewById(R.id.tv_dialog_title)).setText("Th\u00eam danh m\u1ee5c");
            delete.setVisibility(View.GONE);
        }

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ViewUtils.onClick(view, R.id.btn_cat_cancel, v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteCategory(index, 0d);
        });
        ViewUtils.onClick(view, R.id.btn_cat_save, v -> {
            String newName = name.getText().toString().trim();
            if (newName.isEmpty()) {
                toast("Nh\u1eadp t\u00ean danh m\u1ee5c nh\u00e9");
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                if (i != index && list.get(i).name.equalsIgnoreCase(newName)) {
                    toast("\u0110\u00e3 c\u00f3 danh m\u1ee5c c\u00f9ng t\u00ean");
                    return;
                }
            }

            String newEmoji = emoji.getText().toString().trim();
            if (newEmoji.isEmpty()) newEmoji = "\ud83c\udff7";

            if (index >= 0 && index < list.size()) {
                list.get(index).emoji = newEmoji;
                list.get(index).name = newName;
            } else {
                list.add(new Categories.Item(newEmoji, newName));
            }
            Categories.save(context, list, this::buildCategories);
            dialog.dismiss();
        });

        dialog.show();
    }

    // ------------------------------------------------------------- t\u00e0i kho\u1ea3n Google
    /** Hi\u1ec3n th\u1ecb tr\u1ea1ng th\u00e1i \u0111\u0103ng nh\u1eadp \u1edf kh\u1ed1i \u0110\u1ed3ng b\u1ed9 & Sao l\u01b0u. */
    private void bindAccount() {
        if (root == null) return;
        boolean signedIn = FirebaseSyncManager.isSignedIn();

        if (signedIn) {
            String name = FirebaseSyncManager.displayName();
            String mail = FirebaseSyncManager.email();
            text(R.id.tv_account_name, name == null || name.isEmpty()
                    ? "T\u00e0i kho\u1ea3n Google" : name);
            text(R.id.tv_account_email, mail == null ? "" : mail);
            text(R.id.tv_account_badge, "\u0110\u00e3 k\u1ebft n\u1ed1i");
            text(R.id.btn_google_auth, "\u0110\u0103ng xu\u1ea5t kh\u1ecfi t\u00e0i kho\u1ea3n n\u00e0y");
        } else {
            text(R.id.tv_account_name, "Ch\u01b0a \u0111\u0103ng nh\u1eadp");
            text(R.id.tv_account_email,
                    "\u0110\u0103ng nh\u1eadp Google \u0111\u1ec3 sao l\u01b0u chi ti\u00eau v\u00e0 thi\u1ebft l\u1eadp");
            text(R.id.tv_account_badge, "Ch\u01b0a k\u1ebft n\u1ed1i");
            text(R.id.btn_google_auth, "\u0110\u0103ng nh\u1eadp b\u1eb1ng Google");
        }
    }

    private GoogleSignInClient googleClient() {
        if (googleClient == null && getContext() != null) {
            GoogleSignInOptions.Builder builder =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail();
            int id = getResources().getIdentifier(
                    "default_web_client_id", "string", requireContext().getPackageName());
            if (id != 0) builder.requestIdToken(getString(id));
            googleClient = GoogleSignIn.getClient(requireContext(), builder.build());
        }
        return googleClient;
    }

    private void toggleGoogleAccount() {
        if (getContext() == null) return;
        if (FirebaseSyncManager.isSignedIn()) {
            signOutGoogle();
        } else {
            int id = getResources().getIdentifier(
                    "default_web_client_id", "string", requireContext().getPackageName());
            if (id == 0) {
                toast("C\u1ea7n b\u1eadt Google Sign-In trong Firebase r\u1ed3i t\u1ea3i l\u1ea1i google-services.json");
                return;
            }
            GoogleSignInClient client = googleClient();
            if (client == null) return;
            // Ban va 03/08: dang xuat phien Google cu truoc khi mo bang chon tai khoan.
            // Truoc day Google tu chon lai dung tai khoan cu nen nguoi dung bam nut ma
            // "khong thay gi xay ra", tuong la nut hong.
            client.signOut().addOnCompleteListener(task -> googleLauncher.launch(client.getSignInIntent()));
        }
    }

    private void signOutGoogle() {
        ConfirmDialog.show(requireContext(),
                "\u21aa",
                "\u0110\u0103ng xu\u1ea5t?",
                "B\u1ea3n sao l\u01b0u v\u1eabn n\u1eb1m tr\u00ean cloud, \u0111\u0103ng nh\u1eadp l\u1ea1i l\u00e0 l\u1ea5y v\u1ec1 \u0111\u01b0\u1ee3c.",
                "\u0110\u0103ng xu\u1ea5t",
                "Hu\u1ef7",
                () -> {
                    try {
                        FirebaseAuth.getInstance().signOut();
                        GoogleSignInClient client = googleClient();
                        if (client != null) client.signOut();
                    } catch (Throwable ignored) {
                    }
                    toast("\u0110\u00e3 \u0111\u0103ng xu\u1ea5t");
                    bindAccount();
                });
    }

    /**
     * Ban va 03/08. Ket qua dang nhap Google.
     *
     * <p>{@code startActivityForResult} da bi danh dau lac hau va tren mot so may
     * Android moi khong con tra ket qua ve Fragment, day la ly do bam "Dang nhap
     * bang Google" xong thi khong co gi xay ra. Nay dung Activity Result API.</p>
     */
    private final androidx.activity.result.ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> handleGoogleResult(result.getData()));

    private void handleGoogleResult(@Nullable Intent data) {
        if (data == null) {
            // Nguoi dung tu dong bang chon tai khoan
            return;
        }
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(com.google.android.gms.common.api.ApiException.class);
            if (account == null || account.getIdToken() == null) {
                toast("Kh\u00f4ng l\u1ea5y \u0111\u01b0\u1ee3c th\u00f4ng tin t\u00e0i kho\u1ea3n");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener(result -> {
                        toast("\u0110\u00e3 \u0111\u0103ng nh\u1eadp Google");
                        bindAccount();
                        syncAfterSignIn();
                    })
                    .addOnFailureListener(e -> toast("\u0110\u0103ng nh\u1eadp th\u1ea5t b\u1ea1i"));
        } catch (com.google.android.gms.common.api.ApiException e) {
            // Bao ro ma loi: 10 la SHA-1 chua khai bao, 12501 la nguoi dung tu huy
            if (e.getStatusCode() == 12501) return;
            toast("\u0110\u0103ng nh\u1eadp th\u1ea5t b\u1ea1i (m\u00e3 " + e.getStatusCode() + ")");
        } catch (Throwable e) {
            toast("\u0110\u0103ng nh\u1eadp th\u1ea5t b\u1ea1i");
        }
    }

    /** Sau khi đăng nhập chỉ HỎI, không tự động đụng vào dữ liệu. */
    private void syncAfterSignIn() {
        if (getContext() == null) return;
        final Context app = getContext().getApplicationContext();
        final FirebaseSyncManager manager = new FirebaseSyncManager(app);
        manager.saveAccountProfile();

        manager.loadInfo(info -> {
            if (!isAdded() || getContext() == null) return;
            reload();

            if (!info.exists || info.count <= 0) {
                ConfirmDialog.show(getContext(),
                        "\u2601",
                        "Sao l\u01b0u d\u1eef li\u1ec7u?",
                        "T\u00e0i kho\u1ea3n n\u00e0y ch\u01b0a c\u00f3 b\u1ea3n sao l\u01b0u n\u00e0o.",
                        "Sao l\u01b0u ngay",
                        "\u0110\u1ec3 sau",
                        () -> runBackup(manager));
                return;
            }

            String when = android.text.format.DateFormat
                    .format("dd/MM/yyyy HH:mm", info.updatedAt).toString();
            ConfirmDialog.choose(getContext(),
                    "\u2601",
                    "\u0110\u00e3 c\u00f3 b\u1ea3n sao l\u01b0u tr\u00ean cloud",
                    when + " \u00b7 " + info.count + " giao d\u1ecbch"
                            + "\n\nL\u1ea5y v\u1ec1: d\u00f9ng b\u1ea3n cloud, thay d\u1eef li\u1ec7u \u0111ang c\u00f3 tr\u00ean m\u00e1y."
                            + "\nGhi \u0111\u00e8: \u0111\u01b0a d\u1eef li\u1ec7u tr\u00ean m\u00e1y l\u00ean cloud.",
                    "L\u1ea5y b\u1ea3n cloud v\u1ec1", () -> runRestore(manager),
                    "Ghi \u0111\u00e8 l\u00ean cloud", () -> runBackup(manager));
        });
    }

    // ------------------------------------------------------------- sao l\u01b0u / \u0111\u1ed3ng b\u1ed9
    /** Ghi \u0111\u00e8 b\u1ea3n sao l\u01b0u tr\u00ean cloud b\u1eb1ng d\u1eef li\u1ec7u hi\u1ec7n t\u1ea1i c\u1ee7a m\u00e1y. */
    private void backup() {
        if (getContext() == null) return;
        if (!FirebaseSyncManager.isSignedIn()) {
            toast("\u0110\u0103ng nh\u1eadp Google tr\u01b0\u1edbc \u0111\u1ec3 sao l\u01b0u nh\u00e9");
            return;
        }
        runBackup(new FirebaseSyncManager(getContext().getApplicationContext()));
    }

    private void runBackup(FirebaseSyncManager manager) {
        // The thong bao giu nguyen tren man hinh cho den khi co ket qua that su,
        // nho vay khong con canh bam Sao luu roi khong biet no xong hay chua.
        final Notice.Handle notice = Notice.loading(root, "\u0110ang sao l\u01b0u l\u00ean Google\u2026");

        // Dong ho canh o tang giao dien: thong bao khong bao gio quay mai
        final boolean[] done = new boolean[1];
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable giveUp = () -> {
            if (done[0] || !isAdded() || root == null) return;
            done[0] = true;
            notice.error("Sao l\u01b0u qu\u00e1 l\u00e2u",
                    "Nh\u1ea5n gi\u1eef n\u00fat \u0110\u1ed3ng b\u1ed9 \u0111\u1ec3 xem chi ti\u1ebft tr\u1ea1ng th\u00e1i");
        };
        ui.postDelayed(giveUp, 25000L);

        manager.backupNow((ok, count, error) -> {
            ui.removeCallbacks(giveUp);
            if (done[0]) return;
            done[0] = true;
            if (!isAdded() || root == null) {
                notice.dismiss();
                return;
            }
            if (ok) {
                Prefs.setAutoBackupDay(getContext(), AutoBackup.todayKey());
                notice.success("\u0110\u00e3 sao l\u01b0u " + count + " giao d\u1ecbch");
                reload();
            } else {
                notice.error("Sao l\u01b0u th\u1ea5t b\u1ea1i", error);
            }
        });
    }

    /**
     * \u0110\u1ed3ng b\u1ed9 = l\u1ea5y b\u1ea3n sao l\u01b0u cu\u1ed1i c\u00f9ng l\u00e0m b\u1ea3n \u0111\u00fang, k\u1ec3 c\u1ea3 khi n\u00f3 r\u1ed7ng.
     * D\u1eef li\u1ec7u \u0111ang c\u00f3 tr\u00ean m\u00e1y s\u1ebd b\u1ecb xo\u00e1 n\u00ean ph\u1ea3i x\u00e1c nh\u1eadn tr\u01b0\u1edbc.
     */
    private void sync() {
        if (getContext() == null) return;
        if (!FirebaseSyncManager.isSignedIn()) {
            toast("\u0110\u0103ng nh\u1eadp Google tr\u01b0\u1edbc \u0111\u1ec3 \u0111\u1ed3ng b\u1ed9 nh\u00e9");
            return;
        }
        final FirebaseSyncManager manager =
                new FirebaseSyncManager(getContext().getApplicationContext());
        final Notice.Handle notice = Notice.loading(root, "\u0110ang \u0111\u1ed3ng b\u1ed9\u2026");

        // Ban va 03/08 (sua tiep): dong ho canh o TANG GIAO DIEN.
        // Du tang duoi co treo vi bat ky ly do gi, thong bao cung khong quay mai:
        // sau 25 giay no tu doi sang bao loi kem huong dan xem chi tiet.
        final boolean[] done = new boolean[1];
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable giveUp = () -> {
            if (done[0] || !isAdded() || root == null) return;
            done[0] = true;
            notice.error("\u0110\u1ed3ng b\u1ed9 qu\u00e1 l\u00e2u",
                    "Nh\u1ea5n gi\u1eef n\u00fat \u0110\u1ed3ng b\u1ed9 \u0111\u1ec3 xem chi ti\u1ebft tr\u1ea1ng th\u00e1i");
        };
        ui.postDelayed(giveUp, 25000L);

        manager.syncNow((ok, count, pushed, error) -> {
            ui.removeCallbacks(giveUp);
            if (done[0]) return;
            done[0] = true;
            if (!isAdded() || root == null) {
                notice.dismiss();
                return;
            }
            if (ok) {
                Prefs.setAutoBackupDay(getContext(), AutoBackup.todayKey());
                // Bao ro huong da chay. Truoc day chi bao "Da dong bo N giao dich"
                // nen khi app day du lieu LEN, man hinh khong doi so va nhin nhu that bai.
                notice.success(pushed
                        ? "\u0110\u00e3 \u0111\u1ea9y " + count + " giao d\u1ecbch l\u00ean Google"
                        : "\u0110\u00e3 t\u1ea3i " + count + " giao d\u1ecbch t\u1eeb Google v\u1ec1");
                reload();
                refreshOtherScreens();
            } else {
                notice.error("\u0110\u1ed3ng b\u1ed9 th\u1ea5t b\u1ea1i", error);
            }
        });
    }

    /**
     * Ban va 03/08 (bo sung). Xoa han ban sao luu tren cloud.
     *
     * <p>Doc truoc bang loadInfo de hop thoai noi ro sap mat cai gi - moc thoi gian
     * va so giao dich - thay vi hoi chung chung. Day la viec khong hoan tac duoc nen
     * phai xac nhan, va cau chu noi thang rang du lieu tren may van con nguyen.</p>
     */
    private void deleteCloudBackup() {
        if (getContext() == null) return;
        if (!FirebaseSyncManager.isSignedIn()) {
            toast("\u0110\u0103ng nh\u1eadp Google tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9");
            return;
        }
        final Context app = getContext().getApplicationContext();
        final FirebaseSyncManager manager = new FirebaseSyncManager(app);

        final Notice.Handle checking = Notice.loading(root, "\u0110ang ki\u1ec3m tra cloud\u2026");
        manager.loadInfo(info -> {
            if (!isAdded() || getContext() == null) {
                checking.dismiss();
                return;
            }
            checking.dismiss();

            if (!info.exists || info.count <= 0) {
                Notice.info(root, "Cloud ch\u01b0a c\u00f3 b\u1ea3n sao l\u01b0u n\u00e0o");
                return;
            }

            String when = android.text.format.DateFormat
                    .format("dd/MM/yyyy HH:mm", info.updatedAt).toString();
            ConfirmDialog.show(getContext(),
                    "\u2715",
                    "X\u00f3a b\u1ea3n sao l\u01b0u tr\u00ean cloud?",
                    when + " \u00b7 " + info.count + " giao d\u1ecbch"
                            + "\n\nD\u1eef li\u1ec7u tr\u00ean m\u00e1y v\u1eabn \u0111\u01b0\u1ee3c gi\u1eef nguy\u00ean."
                            + "\nX\u00f3a r\u1ed3i th\u00ec kh\u00f4ng l\u1ea5y l\u1ea1i \u0111\u01b0\u1ee3c.",
                    "X\u00f3a tr\u00ean cloud",
                    () -> runDeleteBackup(manager));
        });
    }

    private void runDeleteBackup(FirebaseSyncManager manager) {
        final Notice.Handle notice = Notice.loading(root, "\u0110ang x\u00f3a tr\u00ean cloud\u2026");

        // Cung kieu dong ho canh nhu Sao luu / Dong bo: khong de thong bao quay mai
        final boolean[] done = new boolean[1];
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable giveUp = () -> {
            if (done[0] || !isAdded() || root == null) return;
            done[0] = true;
            notice.error("X\u00f3a qu\u00e1 l\u00e2u",
                    "Nh\u1ea5n gi\u1eef n\u00fat \u0110\u1ed3ng b\u1ed9 \u0111\u1ec3 xem chi ti\u1ebft tr\u1ea1ng th\u00e1i");
        };
        ui.postDelayed(giveUp, 25000L);

        manager.deleteBackup((ok, count, error) -> {
            ui.removeCallbacks(giveUp);
            if (done[0]) return;
            done[0] = true;
            if (!isAdded() || root == null) {
                notice.dismiss();
                return;
            }
            if (ok) {
                notice.success("\u0110\u00e3 x\u00f3a b\u1ea3n sao l\u01b0u tr\u00ean cloud");
                reload();
            } else {
                notice.error("X\u00f3a th\u1ea5t b\u1ea1i", error);
            }
        });
    }

    /**
     * Ban va 03/08. Bat cac man hinh khac nap lai sau khi du lieu doi.
     *
     * <p>Truoc day khoi phuc xong chi co man Cai dat cap nhat, Trang chu va Phan tich
     * van hien so cu cho den khi mo lai app - nhin nhu nut dong bo khong an thua gi.</p>
     */
    /** Hop thoai chan doan: dang nhap ai, co mang khong, moc thoi gian hai ben. */
    private void showSyncStatus() {
        if (getContext() == null) return;
        final Context app = getContext().getApplicationContext();
        final FirebaseSyncManager manager = new FirebaseSyncManager(app);
        final String local = manager.describeStatus();

        final Notice.Handle notice = Notice.loading(root, "\u0110ang ki\u1ec3m tra cloud\u2026");
        manager.loadInfo(info -> {
            if (!isAdded() || getContext() == null) {
                notice.dismiss();
                return;
            }
            notice.dismiss();
            String cloud = info.exists
                    ? android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", info.updatedAt)
                    + " \u00b7 " + info.count + " giao d\u1ecbch"
                    : "ch\u01b0a c\u00f3 b\u1ea3n sao l\u01b0u";
            // Chi la bang thong tin nen khong can nut hanh dong nao
            ConfirmDialog.show(getContext(),
                    "\u26ac",
                    "Chi ti\u1ebft \u0111\u1ed3ng b\u1ed9",
                    local + "\nCloud: " + cloud,
                    "\u0110\u00f3ng",
                    "",
                    null);
        });
    }

    private void refreshOtherScreens() {
        if (getActivity() instanceof com.example.bmmoney.MainActivity) {
            ((com.example.bmmoney.MainActivity) getActivity()).refreshCurrentTab();
        }
    }

    private void runRestore(FirebaseSyncManager manager) {
        final Notice.Handle notice = Notice.loading(root, "\u0110ang l\u1ea5y d\u1eef li\u1ec7u v\u1ec1\u2026");
        manager.restoreLatest((ok, count, error) -> {
            if (!isAdded() || root == null) {
                notice.dismiss();
                return;
            }
            if (ok) {
                notice.success(count > 0
                        ? "\u0110\u00e3 kh\u00f4i ph\u1ee5c " + count + " giao d\u1ecbch"
                        : "B\u1ea3n sao l\u01b0u cu\u1ed1i c\u00f9ng kh\u00f4ng c\u00f3 giao d\u1ecbch n\u00e0o");
                reload();
                refreshOtherScreens();
            } else {
                notice.error("Kh\u00f4ng l\u1ea5y \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u", error);
            }
        });
    }

    // ------------------------------------------------------------- ti\u1ec7n \u00edch
    private abstract static class SimpleWatcher implements TextWatcher {
        public abstract void changed(String value);

        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            changed(s.toString());
        }
    }

    /**
     * Moi thong bao ngan cua man Tuy chon deu di qua day. Truoc dung Toast xam den
     * cua he thong, nay dung the noi mau kem cua app cho dong bo giao dien.
     */
    private void toast(String message) {
        if (root != null) {
            Notice.info(root, message);
        } else if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
