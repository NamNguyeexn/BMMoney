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
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Reminders;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Man Cai dat: ho so, tuy chon chu ky - nhac ghi chu - cac nguong phan tram,
 * danh muc tuy chinh va sao luu du lieu.
 */
public class SettingsFragment extends Fragment {

    private static final int REQ_NOTIFICATION = 7001;

    /** Bieu tuong goi y khi them danh muc moi. */
    private static final String[] EMOJI_SUGGEST = {
            "\ud83c\udf5c", "\ud83c\udf54", "\u2615", "\ud83d\ude97", "\u26fd",
            "\ud83e\uddfe", "\ud83d\udca1", "\ud83c\udfe0", "\ud83d\udecd", "\ud83d\udc55",
            "\ud83d\udc8a", "\ud83c\udfac", "\ud83c\udfae", "\ud83d\udcda", "\u2708",
            "\ud83c\udf81", "\ud83d\udc36", "\ud83c\udfcb", "\ud83d\udcb0", "\ud83c\udff7"
    };

    private View root;
    private SwipeRefreshLayout refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);
        refresh = Refresh.setup(root, R.id.refresh_settings, this::reload);

        bindProfile();
        allowInnerScroll();

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
     * Khung danh muc nam ben trong mot ScrollView khac nen man cha se doat thao tac cuon.
     * O day ta yeu cau man cha nhuong lai su kien cham khi ngon tay dang o tren khung nay.
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

    // ------------------------------------------------------------- ho so
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

    // ------------------------------------------------------------- nap lai toan man
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

    // ------------------------------------------------------------- nhac ghi chu
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

    // ------------------------------------------------------------- danh muc tuy chinh
    /** Nap danh sach danh muc kem so tien da chi trong ky hien tai. */
    private void buildCategories() {
        final LinearLayout container = root.findViewById(R.id.container_categories);
        if (container == null || getContext() == null) return;

        final List<Categories.Item> list = Categories.all(getContext());
        text(R.id.tv_cat_count, list.size() + " m\u1ee5c");
        show(R.id.tv_no_category, list.isEmpty());
        show(R.id.tv_cat_scroll_hint, list.size() > 5);

        final long[] bounds = Cycle.bounds(Prefs.cycleDay(getContext()), System.currentTimeMillis(), 0);
        final TransactionDao dao = AppDatabase.dao(getContext().getApplicationContext());

        Db.load(() -> {
            Map<String, Double> spent = new HashMap<>();
            List<CategoryTotal> totals = dao.getExpenseByCategoryInRange(bounds[0], bounds[1]);
            if (totals != null) {
                for (CategoryTotal item : totals) {
                    if (item != null && item.category != null) spent.put(item.category, item.total);
                }
            }
            return spent;
        }, spent -> renderCategories(container, list, spent == null ? new HashMap<>() : spent));
    }

    private void renderCategories(LinearLayout container, final List<Categories.Item> list,
                                  final Map<String, Double> spent) {
        if (root == null || getContext() == null) return;
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final Categories.Item item = list.get(i);

            View row = inflater.inflate(R.layout.item_category, container, false);
            ((TextView) row.findViewById(R.id.tv_cat_emoji)).setText(item.emoji);
            ((TextView) row.findViewById(R.id.tv_cat_name)).setText(item.name);

            Double total = spent.get(item.name);
            ((TextView) row.findViewById(R.id.tv_cat_hint)).setText(
                    total == null || total <= 0d
                            ? "Ch\u01b0a d\u00f9ng trong k\u1ef3 n\u00e0y"
                            : "\u0110\u00e3 chi " + Money.shortVnd(total) + " trong k\u1ef3 n\u00e0y");

            row.findViewById(R.id.box_cat_info).setOnClickListener(v -> editCategory(index));

            View up = row.findViewById(R.id.btn_cat_up);
            View down = row.findViewById(R.id.btn_cat_down);
            up.setEnabled(index > 0);
            up.setAlpha(index > 0 ? 1f : 0.3f);
            down.setEnabled(index < list.size() - 1);
            down.setAlpha(index < list.size() - 1 ? 1f : 0.3f);

            up.setOnClickListener(v -> move(index, -1));
            down.setOnClickListener(v -> move(index, 1));
            row.findViewById(R.id.btn_cat_remove).setOnClickListener(v ->
                    confirmDelete(index, item, spent.get(item.name)));

            container.addView(row);
        }
    }

    /** Doi thu tu hien thi cua danh muc (anh huong ca dai chon o man Them giao dich). */
    private void move(int index, int delta) {
        if (getContext() == null) return;
        List<Categories.Item> list = new ArrayList<>(Categories.all(getContext()));
        int target = index + delta;
        if (index < 0 || index >= list.size() || target < 0 || target >= list.size()) return;

        Collections.swap(list, index, target);
        Categories.save(getContext(), list);
        buildCategories();
    }

    private void confirmDelete(final int index, final Categories.Item item, final Double total) {
        if (getContext() == null) return;

        String message = total == null || total <= 0d
                ? "Danh m\u1ee5c n\u00e0y ch\u01b0a d\u00f9ng trong k\u1ef3 hi\u1ec7n t\u1ea1i. X\u00f3a kh\u1ecfi danh s\u00e1ch?"
                : "K\u1ef3 n\u00e0y \u0111\u00e3 chi " + Money.shortVnd(total) + " cho danh m\u1ee5c n\u00e0y. "
                + "C\u00e1c giao d\u1ecbch c\u0169 v\u1eabn \u0111\u01b0\u1ee3c gi\u1eef nguy\u00ean, ch\u1ec9 danh m\u1ee5c b\u1ecb x\u00f3a kh\u1ecfi danh s\u00e1ch ch\u1ecdn.";

        new AlertDialog.Builder(getContext())
                .setTitle("X\u00f3a " + item.name + "?")
                .setMessage(message)
                .setNegativeButton("H\u1ee7y", null)
                .setPositiveButton("X\u00f3a", (dialog, which) -> {
                    List<Categories.Item> list = new ArrayList<>(Categories.all(getContext()));
                    if (index >= 0 && index < list.size()) list.remove(index);
                    Categories.save(getContext(), list);
                    toast("\u0110\u00e3 x\u00f3a " + item.name);
                    buildCategories();
                })
                .show();
    }

    /** index = -1 la them moi. */
    private void editCategory(final int index) {
        if (getContext() == null) return;
        final Context context = getContext();
        final List<Categories.Item> list = new ArrayList<>(Categories.all(context));

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_category, null, false);
        final EditText emoji = view.findViewById(R.id.edt_cat_emoji);
        final EditText name = view.findViewById(R.id.edt_cat_name);
        View delete = view.findViewById(R.id.btn_cat_delete);

        final boolean editing = index >= 0 && index < list.size();
        if (editing) {
            emoji.setText(list.get(index).emoji);
            name.setText(list.get(index).name);
            name.setSelection(name.getText().length());
            ((TextView) view.findViewById(R.id.tv_dialog_title)).setText("S\u1eeda danh m\u1ee5c");
        } else {
            ((TextView) view.findViewById(R.id.tv_dialog_title)).setText("Th\u00eam danh m\u1ee5c");
            delete.setVisibility(View.GONE);
        }

        buildEmojiSuggest(context, view, emoji);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_cat_cancel).setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            if (editing) confirmDelete(index, list.get(index), null);
        });
        view.findViewById(R.id.btn_cat_save).setOnClickListener(v -> {
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
            if (newEmoji.isEmpty()) newEmoji = EMOJI_SUGGEST[EMOJI_SUGGEST.length - 1];

            if (editing) {
                list.get(index).emoji = newEmoji;
                list.get(index).name = newName;
            } else {
                list.add(new Categories.Item(newEmoji, newName));
            }
            Categories.save(context, list);
            dialog.dismiss();
            toast(editing ? "\u0110\u00e3 l\u01b0u thay \u0111\u1ed5i" : "\u0110\u00e3 th\u00eam " + newName);
            buildCategories();
        });

        dialog.show();
    }

    /** Dai bieu tuong goi y, cham mot cai la dien ngay vao o emoji. */
    private void buildEmojiSuggest(Context context, View view, final EditText target) {
        LinearLayout container = view.findViewById(R.id.container_emoji);
        if (container == null) return;
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(context);
        for (final String emoji : EMOJI_SUGGEST) {
            View chip = inflater.inflate(R.layout.item_chip, container, false);
            TextView label = chip.findViewById(R.id.tv_chip);
            label.setText(emoji);
            label.setTextSize(18f);
            chip.setOnClickListener(v -> {
                target.setText(emoji);
                target.setSelection(target.getText().length());
            });
            container.addView(chip);
        }
    }

    // ------------------------------------------------------------- sao luu
    private void backup() {
        if (getContext() == null) return;
        final Context app = getContext().getApplicationContext();
        toast("\u0110ang sao l\u01b0u...");
        Db.io(() -> {
            try {
                new FirebaseSyncManager(app).uploadAllLocal();
                Prefs.setLastBackup(app, System.currentTimeMillis());
            } catch (Throwable ignored) {
                // khong co mang thi bo qua
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

    // ------------------------------------------------------------- tien ich
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

    private void show(int id, boolean visible) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
