package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.R;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Man Cai dat: ho so, ngan sach, ngay chot chu ky (dd/mm), danh muc tuy chinh (them/sua/xoa)
 * va sao luu. Da bo phan Xuat du lieu va Bao mat / Quyen rieng tu.
 */
public class SettingsFragment extends Fragment {

    private final SimpleDateFormat backupFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);

        final EditText nameInput = root.findViewById(R.id.edt_name);
        final EditText budgetInput = root.findViewById(R.id.edt_budget);

        nameInput.setText(Prefs.userName(getContext()));
        budgetInput.setText(String.valueOf((long) Prefs.budget(getContext())));
        avatar(Prefs.userName(getContext()));

        nameInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String value = s.toString().trim();
                if (getContext() == null) return;
                Prefs.setUserName(getContext(), value.isEmpty() ? "b\u1ea1n" : value);
                avatar(value);
            }
        });

        budgetInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (getContext() == null) return;
                String digits = s.toString().replaceAll("[^0-9]", "");
                if (digits.isEmpty()) return;
                Prefs.setBudget(getContext(), Double.parseDouble(digits));
            }
        });

        // Ngay chot chu ky (dd/mm)
        View cycleRow = root.findViewById(R.id.tv_cycle_day);
        if (cycleRow != null) {
            cycleRow.setOnClickListener(v ->
                    CycleDialog.show(getContext(), this::refreshCycle));
        }

        // Danh muc tuy chinh: nut "+" mo popup them moi
        View addCategory = root.findViewById(R.id.btn_add_category);
        if (addCategory != null) {
            addCategory.setOnClickListener(v -> editCategory(-1));
        }

        root.findViewById(R.id.btn_backup_now).setOnClickListener(v -> backup());
        root.findViewById(R.id.btn_sync_now).setOnClickListener(v -> sync());

        text(R.id.tv_app_version, "Phi\u00ean b\u1ea3n 2.1");
        refreshCycle();
        refreshBackup();
        refreshCategories();
        return root;
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }

    private void refreshCycle() {
        if (getContext() == null) return;
        int day = Prefs.cycleDay(getContext());
        long now = System.currentTimeMillis();
        long[] bounds = Cycle.bounds(day, now, 0);
        text(R.id.tv_cycle_day, Cycle.cycleDayLabel(getContext())
                + "  \u00b7  k\u1ef3 hi\u1ec7n t\u1ea1i " + Cycle.rangeLabel(bounds[0], bounds[1])
                + "  \u00b7  c\u00f2n " + Cycle.daysLeft(day, now) + " ng\u00e0y");
    }

    /** Ve lai danh sach danh muc tuy chinh. */
    private void refreshCategories() {
        if (root == null || getContext() == null) return;
        LinearLayout container = root.findViewById(R.id.container_categories);
        if (container == null) return;

        container.removeAllViews();
        List<Categories.Item> items = Categories.all(getContext());

        View empty = root.findViewById(R.id.tv_no_category);
        if (empty != null) empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            Categories.Item item = items.get(i);
            View row = inflater.inflate(R.layout.item_category, container, false);
            ((TextView) row.findViewById(R.id.tv_cat_emoji)).setText(item.emoji);
            ((TextView) row.findViewById(R.id.tv_cat_name)).setText(item.name);
            row.setOnClickListener(v -> editCategory(index));
            container.addView(row);
        }
    }

    /**
     * Popup nho de them moi (index = -1) hoac sua / xoa danh muc dang chon.
     */
    private void editCategory(final int index) {
        if (getContext() == null) return;

        final List<Categories.Item> items = Categories.all(getContext());
        final boolean isNew = index < 0 || index >= items.size();

        View content = LayoutInflater.from(getContext()).inflate(R.layout.dialog_category, null, false);
        final EditText emojiInput = content.findViewById(R.id.edt_cat_emoji);
        final EditText nameInput = content.findViewById(R.id.edt_cat_name);
        TextView title = content.findViewById(R.id.tv_dialog_title);
        View delete = content.findViewById(R.id.btn_cat_delete);
        View cancel = content.findViewById(R.id.btn_cat_cancel);
        View save = content.findViewById(R.id.btn_cat_save);

        if (isNew) {
            title.setText("Th\u00eam danh m\u1ee5c");
            delete.setVisibility(View.GONE);
        } else {
            title.setText("S\u1eeda danh m\u1ee5c");
            emojiInput.setText(items.get(index).emoji);
            nameInput.setText(items.get(index).name);
        }

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(content)
                .create();

        cancel.setOnClickListener(v -> dialog.dismiss());

        delete.setOnClickListener(v -> {
            items.remove(index);
            Categories.save(getContext(), items);
            refreshCategories();
            dialog.dismiss();
            toast("\u0110\u00e3 x\u00f3a danh m\u1ee5c");
        });

        save.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String emoji = emojiInput.getText().toString().trim();
            if (name.isEmpty()) {
                toast("Nh\u1eadp t\u00ean danh m\u1ee5c");
                return;
            }
            if (emoji.isEmpty()) emoji = "\ud83c\udff7";
            if (isNew) {
                items.add(new Categories.Item(emoji, name));
            } else {
                items.get(index).emoji = emoji;
                items.get(index).name = name;
            }
            Categories.save(getContext(), items);
            refreshCategories();
            dialog.dismiss();
            toast("\u0110\u00e3 l\u01b0u danh m\u1ee5c");
        });

        dialog.show();
    }

    private void backup() {
        if (getContext() == null) return;
        toast("\u0110ang sao l\u01b0u...");
        Db.io(() -> {
            new FirebaseSyncManager(requireContext().getApplicationContext()).uploadAllLocal();
            Db.ui(() -> {
                if (!isAdded() || getContext() == null) return;
                Prefs.setLastBackup(getContext(), System.currentTimeMillis());
                refreshBackup();
                toast("Sao l\u01b0u xong");
            });
        });
    }

    private void sync() {
        if (getContext() == null) return;
        toast("\u0110ang t\u1ea3i d\u1eef li\u1ec7u...");
        new FirebaseSyncManager(requireContext().getApplicationContext()).downloadToLocal(() -> {
            if (!isAdded()) return;
            toast("\u0110\u00e3 \u0111\u1ed3ng b\u1ed9 t\u1eeb m\u00e1y ch\u1ee7");
        });
    }

    private void refreshBackup() {
        if (getContext() == null) return;
        long last = Prefs.lastBackup(getContext());
        text(R.id.tv_last_backup, last == 0
                ? "Ch\u01b0a sao l\u01b0u l\u1ea7n n\u00e0o"
                : "L\u1ea7n cu\u1ed1i: " + backupFormat.format(new Date(last)));
    }

    private void avatar(String name) {
        String value = name == null ? "" : name.trim();
        text(R.id.tv_avatar, value.isEmpty()
                ? "B" : value.substring(0, 1).toUpperCase(Locale.getDefault()));
    }

    private void toast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void text(int id, String value) {
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }

    /** TextWatcher rut gon de bot code lap. */
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
