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
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Reminders;

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

    /** Bi\u1ec3u t\u01b0\u1ee3ng g\u1ee3i \u00fd cho danh m\u1ee5c. */
    private static final String[] EMOJI_SUGGEST = {
            "\ud83c\udf5c", "\u2615", "\ud83d\uded2", "\ud83d\ude97", "\u26fd", "\ud83e\uddfe",
            "\ud83c\udfe0", "\ud83d\udca1", "\ud83d\udc8a", "\ud83c\udfac", "\ud83d\udcda",
            "\u2708\ufe0f", "\ud83c\udf81", "\ud83d\udc36", "\ud83c\udfcb", "\ud83d\udcb0"};

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
        root.findViewById(R.id.btn_google_auth).setOnClickListener(v -> toggleGoogleAccount());
        root.findViewById(R.id.btn_backup_now).setOnClickListener(v -> backup());
        root.findViewById(R.id.btn_sync_now).setOnClickListener(v -> sync());
        // Ban va 03/08 (sua tiep): nhan giu nut Dong bo de xem chi tiet trang thai,
        // biet ngay dang vuong o dang nhap, mang hay moc thoi gian.
        root.findViewById(R.id.btn_sync_now).setOnLongClickListener(v -> {
            showSyncStatus();
            return true;
        });

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
        final LinearLayout container = root.findViewById(R.id.container_categories);
        if (container == null || getContext() == null) return;

        final List<Categories.Item> list = Categories.all(getContext());
        text(R.id.tv_cat_count, list.size() + " m\u1ee5c");
        root.findViewById(R.id.tv_no_category).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        View scrollHint = root.findViewById(R.id.tv_cat_scroll_hint);
        if (scrollHint != null) scrollHint.setVisibility(list.size() > 5 ? View.VISIBLE : View.GONE);

        // N\u1ea1p t\u1ed5ng chi c\u1ee7a k\u1ef3 hi\u1ec7n t\u1ea1i \u0111\u1ec3 m\u1ed7i danh m\u1ee5c c\u00f3 th\u00eam d\u00f2ng ph\u1ee5 h\u1eefu \u00edch
        final TransactionDao dao = AppDatabase.dao(getContext());
        final long[] bounds = Cycle.bounds(Prefs.cycleDay(getContext()), System.currentTimeMillis(), 0);
        Db.load(() -> {
            Map<String, Double> map = new HashMap<>();
            List<CategoryTotal> totals = dao.getExpenseByCategoryInRange(bounds[0], bounds[1]);
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

            row.findViewById(R.id.box_cat_info).setOnClickListener(v -> editCategory(index));

            View up = row.findViewById(R.id.btn_cat_up);
            View down = row.findViewById(R.id.btn_cat_down);
            setEnabledLook(up, index > 0);
            setEnabledLook(down, index < list.size() - 1);
            up.setOnClickListener(v -> moveCategory(index, -1));
            down.setOnClickListener(v -> moveCategory(index, 1));

            row.findViewById(R.id.btn_cat_remove).setOnClickListener(v -> confirmDeleteCategory(index, spent));
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
        Categories.save(getContext(), list);
        buildCategories();
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
                    Categories.save(requireContext(), list);
                    buildCategories();
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

        buildEmojiSuggestions(view, emoji);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_cat_cancel).setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteCategory(index, 0d);
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
            if (newEmoji.isEmpty()) newEmoji = "\ud83c\udff7";

            if (index >= 0 && index < list.size()) {
                list.get(index).emoji = newEmoji;
                list.get(index).name = newName;
            } else {
                list.add(new Categories.Item(newEmoji, newName));
            }
            Categories.save(context, list);
            dialog.dismiss();
            buildCategories();
        });

        dialog.show();
    }

    /** D\u1ea3i bi\u1ec3u t\u01b0\u1ee3ng b\u1ea5m m\u1ed9t c\u00e1i l\u00e0 \u0111i\u1ec1n, \u0111\u1ee1 ph\u1ea3i m\u1edf b\u00e0n ph\u00edm emoji. */
    private void buildEmojiSuggestions(View dialogView, final EditText target) {
        LinearLayout box = dialogView.findViewById(R.id.container_emoji);
        if (box == null || getContext() == null) return;
        box.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (String value : EMOJI_SUGGEST) {
            View chip = inflater.inflate(R.layout.item_chip, box, false);
            TextView label = chip.findViewById(R.id.tv_chip);
            label.setText(value);
            chip.setOnClickListener(v -> {
                target.setText(value);
                target.setSelection(target.getText().length());
            });
            box.addView(chip);
        }
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

//    /**
//     * Sau khi \u0111\u0103ng nh\u1eadp: h\u1ecfi xem d\u00f9ng b\u1ea3n sao l\u01b0u tr\u00ean cloud hay \u0111\u1ea9y d\u1eef li\u1ec7u m\u00e1y l\u00ean.
//     * Hai h\u01b0\u1edbng \u0111\u1ec1u ghi \u0111\u00e8 to\u00e0n b\u1ed9 n\u00ean lu\u00f4n \u0111\u1ec3 ng\u01b0\u1eddi d\u00f9ng ch\u1ecdn.
//     */
//    private void syncAfterSignIn() {
//        if (getContext() == null) return;
//        final Context app = getContext().getApplicationContext();
//        final FirebaseSyncManager manager = new FirebaseSyncManager(app);
//        manager.saveAccountProfile();
//
//        manager.loadInfo(info -> {
//            if (!isAdded() || getContext() == null) return;
//
//            if (!info.exists || info.count <= 0) {
//                // Cloud ch\u01b0a c\u00f3 g\u00ec: sao l\u01b0u d\u1eef li\u1ec7u m\u00e1y l\u00ean lu\u00f4n
//                runBackup(manager);
//                return;
//            }
//
//            String when = android.text.format.DateFormat
//                    .format("dd/MM/yyyy HH:mm", info.updatedAt).toString();
//            new androidx.appcompat.app.AlertDialog.Builder(getContext())
//                    .setTitle("T\u00e0i kho\u1ea3n n\u00e0y \u0111\u00e3 c\u00f3 b\u1ea3n sao l\u01b0u")
//                    .setMessage("B\u1ea3n sao l\u01b0u l\u00fac " + when + " g\u1ed3m " + info.count
//                            + " giao d\u1ecbch.\n\nD\u00f9ng b\u1ea3n n\u00e0y s\u1ebd xo\u00e1 d\u1eef li\u1ec7u \u0111ang c\u00f3 tr\u00ean m\u00e1y.")
//                    .setPositiveButton("D\u00f9ng b\u1ea3n tr\u00ean cloud", (d, w) -> runRestore(manager))
//                    .setNegativeButton("Gi\u1eef d\u1eef li\u1ec7u m\u00e1y", (d, w) -> runBackup(manager))
//                    .setCancelable(false)
//                    .show();
//        });
//    }
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
