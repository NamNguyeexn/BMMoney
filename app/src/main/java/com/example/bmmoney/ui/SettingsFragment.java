package com.example.bmmoney.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
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

import com.example.bmmoney.R;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Reminders;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * M\u00e0n C\u00e0i \u0111\u1eb7t: h\u1ed3 s\u01a1, t\u00f9y ch\u1ecdn chu k\u1ef3 \u2013 nh\u1eafc ghi ch\u00fa \u2013 c\u00e1c ng\u01b0\u1ee1ng ph\u1ea7n tr\u0103m,
 * danh m\u1ee5c t\u00f9y ch\u1ec9nh v\u00e0 sao l\u01b0u d\u1eef li\u1ec7u.
 */
public class SettingsFragment extends Fragment {

    private static final int REQ_NOTIFICATION = 7001;

    private View root;
    private SwipeRefreshLayout refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);
        refresh = Refresh.setup(root, R.id.refresh_settings, this::reload);

        bindProfile();

        root.findViewById(R.id.tv_cycle_day).setOnClickListener(v ->
                CycleDialog.show(getContext(), this::reload));

        root.findViewById(R.id.tv_warn_percent).setOnClickListener(v ->
                PercentDialog.show(getContext(), "Ng\u01b0\u1ee1ng chi ti\u00eau",
                        "C\u1ea3nh b\u00e1o \u1edf Trang ch\u1ee7 khi chi ti\u00eau v\u01b0\u1ee3t m\u1ee9c n\u00e0y c\u1ee7a ng\u00e2n s\u00e1ch",
                        10, 200, Prefs.warnPercent(getContext()), percent -> {
                            Prefs.setWarnPercent(getContext(), percent);
                            reload();
                        }));

        root.findViewById(R.id.tv_big_percent).setOnClickListener(v ->
                PercentDialog.show(getContext(), "M\u1ed1c chi ti\u00eau l\u1edbn",
                        "Giao d\u1ecbch chi\u1ebfm t\u1eeb m\u1ee9c n\u00e0y c\u1ee7a t\u1ed5ng chi trong k\u1ef3 s\u1ebd \u0111\u01b0\u1ee3c \u0111\u00e1nh d\u1ea5u",
                        1, 99, Prefs.bigPercent(getContext()), percent -> {
                            Prefs.setBigPercent(getContext(), percent);
                            reload();
                        }));

        root.findViewById(R.id.btn_add_reminder).setOnClickListener(v -> {
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

        allowInnerScroll();

        root.findViewById(R.id.btn_add_category).setOnClickListener(v -> editCategory(-1));
        root.findViewById(R.id.btn_backup_now).setOnClickListener(v -> backup());
        root.findViewById(R.id.btn_sync_now).setOnClickListener(v -> sync());

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
    public void reload() {
        if (root == null || getContext() == null) return;

        text(R.id.tv_cycle_day, Cycle.cycleDayLabel(getContext()));
        text(R.id.tv_warn_percent, Prefs.warnPercent(getContext()) + "%");
        text(R.id.tv_big_percent, Prefs.bigPercent(getContext()) + "%");
        text(R.id.tv_app_version, "Phi\u00ean b\u1ea3n 2.2");

        long backup = Prefs.lastBackup(getContext());
        text(R.id.tv_last_backup, backup <= 0 ? "Ch\u01b0a sao l\u01b0u l\u1ea7n n\u00e0o"
                : "L\u1ea7n cu\u1ed1i: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(backup)));

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
        root.findViewById(R.id.tv_no_reminder).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

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

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || getContext() == null) return;
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    // ------------------------------------------------------------- danh m\u1ee5c
    private void buildCategories() {
        LinearLayout container = root.findViewById(R.id.container_categories);
        if (container == null) return;
        container.removeAllViews();

        final List<Categories.Item> list = Categories.all(getContext());
        root.findViewById(R.id.tv_no_category).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            Categories.Item item = list.get(i);
            View row = inflater.inflate(R.layout.item_category, container, false);
            ((TextView) row.findViewById(R.id.tv_cat_emoji)).setText(item.emoji);
            ((TextView) row.findViewById(R.id.tv_cat_name)).setText(item.name);
            ((TextView) row.findViewById(R.id.tv_cat_hint)).setText("Ch\u1ea1m \u0111\u1ec3 s\u1eeda ho\u1eb7c xo\u00e1");
            row.setOnClickListener(v -> editCategory(index));
            container.addView(row);
        }
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
            ((TextView) view.findViewById(R.id.tv_dialog_title)).setText("S\u1eeda danh m\u1ee5c");
        } else {
            ((TextView) view.findViewById(R.id.tv_dialog_title)).setText("Th\u00eam danh m\u1ee5c");
            delete.setVisibility(View.GONE);
        }

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_cat_cancel).setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            if (index >= 0 && index < list.size()) list.remove(index);
            Categories.save(context, list);
            dialog.dismiss();
            reload();
        });
        view.findViewById(R.id.btn_cat_save).setOnClickListener(v -> {
            String newName = name.getText().toString().trim();
            if (newName.isEmpty()) {
                toast("Nh\u1eadp t\u00ean danh m\u1ee5c nh\u00e9");
                return;
            }
            String newEmoji = emoji.getText().toString().trim();
            if (newEmoji.isEmpty()) newEmoji = "\ud83c\udff7";

            if (index >= 0 && index < list.size()) {
                list.get(index).emoji = newEmoji;
                list.get(index).name = newName;
            } else {
                list.add(new Categories.Item(newEmoji, newName));
            }
            Categories.save(context, list);
            dialog.dismiss();
            reload();
        });

        dialog.show();
    }

    // ------------------------------------------------------------- sao l\u01b0u
    private void backup() {
        if (getContext() == null) return;
        final Context app = getContext().getApplicationContext();
        toast("\u0110ang sao l\u01b0u...");
        Db.io(() -> {
            try {
                new FirebaseSyncManager(app).uploadAllLocal();
                Prefs.setLastBackup(app, System.currentTimeMillis());
            } catch (Throwable ignored) {
                // kh\u00f4ng c\u00f3 m\u1ea1ng th\u00ec b\u1ecf qua
            }
            Db.ui(() -> {
                toast("\u0110\u00e3 sao l\u01b0u xong");
                reload();
            });
        });
    }

    private void sync() {
        if (getContext() == null) return;
        toast("\u0110ang \u0111\u1ed3ng b\u1ed9...");
        try {
            new FirebaseSyncManager(getContext().getApplicationContext())
                    .downloadToLocal(() -> {
                        toast("\u0110\u00e3 \u0111\u1ed3ng b\u1ed9 xong");
                        reload();
                    });
        } catch (Throwable ignored) {
            toast("Kh\u00f4ng k\u1ebft n\u1ed1i \u0111\u01b0\u1ee3c m\u00e1y ch\u1ee7");
        }
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

    private void toast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
