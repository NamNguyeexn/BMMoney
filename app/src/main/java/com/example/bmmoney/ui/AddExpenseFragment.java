package com.example.bmmoney.ui;

import android.os.Bundle;
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

import com.example.bmmoney.MainActivity;
import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Refresh;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * M\u00e0n Th\u00eam giao d\u1ecbch: nh\u1eadp tay, danh m\u1ee5c cu\u1ed9n ngang l\u1ea5y t\u1eeb danh m\u1ee5c t\u00f9y ch\u1ec9nh,
 * m\u1ed9t popup duy nh\u1ea5t cho ng\u00e0y + gi\u1edd + ph\u00fat, v\u00e0 select box ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n.
 */
public class AddExpenseFragment extends Fragment {

    private static final List<String> PAYMENTS = Arrays.asList(
            "\ud83c\udfe6 Chuy\u1ec3n kho\u1ea3n",
            "\ud83d\udcb5 Ti\u1ec1n m\u1eb7t",
            "\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng",
            "\ud83d\udcf1 V\u00ed \u0111i\u1ec7n t\u1eed",
            "\ud83e\uddfe Kh\u00e1c");

    private View root;
    private long pickedTime = System.currentTimeMillis();
    private String payment = PAYMENTS.get(0);
    private String category = "";
    private final List<View> cells = new ArrayList<>();
    private final List<Categories.Item> items = new ArrayList<>();

    private final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_add_expense, container, false);

        Refresh.setup(root, R.id.refresh_add_expense, this::resetForm);

        View back = root.findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> open(MainActivity.TAB_HOME));

        text(R.id.tv_date, format.format(new Date(pickedTime)));
        text(R.id.tv_payment, payment);

        root.findViewById(R.id.tv_date).setOnClickListener(v ->
                DateTimeDialog.show(getContext(), pickedTime, time -> {
                    pickedTime = time;
                    text(R.id.tv_date, format.format(new Date(pickedTime)));
                }));

        root.findViewById(R.id.tv_payment).setOnClickListener(v ->
                SelectDialog.show(getContext(), "Ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n", PAYMENTS, payment,
                        (index, value) -> {
                            payment = value;
                            text(R.id.tv_payment, payment);
                        }));

        root.findViewById(R.id.btn_submit).setOnClickListener(v -> save());

        buildCategories();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        buildCategories();
    }

    @Override
    public void onDestroyView() {
        cells.clear();
        items.clear();
        root = null;
        super.onDestroyView();
    }

    /** N\u1ea1p danh m\u1ee5c t\u1eeb C\u00e0i \u0111\u1eb7t v\u00e0o d\u1ea3i cu\u1ed9n ngang. */
    private void buildCategories() {
        if (root == null || getContext() == null) return;

        LinearLayout container = root.findViewById(R.id.container_cats);
        if (container == null) return;

        container.removeAllViews();
        cells.clear();
        items.clear();
        items.addAll(Categories.all(getContext()));

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < items.size(); i++) {
            final Categories.Item item = items.get(i);
            View cell = inflater.inflate(R.layout.item_cat_cell, container, false);
            ((TextView) cell.findViewById(R.id.tv_cell_emoji)).setText(item.emoji);
            ((TextView) cell.findViewById(R.id.tv_cell_name)).setText(item.name);
            cell.setOnClickListener(v -> {
                category = item.name;
                highlight();
            });
            container.addView(cell);
            cells.add(cell);
        }

        if (category.isEmpty() && !items.isEmpty()) {
            category = items.get(0).name;
        }
        highlight();
    }

    private void highlight() {
        for (int i = 0; i < cells.size() && i < items.size(); i++) {
            boolean active = items.get(i).name.equals(category);
            cells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    private void save() {
        if (root == null || getContext() == null) return;

        EditText amountField = root.findViewById(R.id.edt_amount);
        EditText descField = root.findViewById(R.id.edt_description);
        EditText noteField = root.findViewById(R.id.edt_note);

        String rawAmount = amountField.getText().toString().replaceAll("[^0-9]", "");
        if (rawAmount.isEmpty()) {
            toast("Nh\u1eadp s\u1ed1 ti\u1ec1n tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9");
            return;
        }
        if (category.isEmpty()) {
            toast("Ch\u1ecdn m\u1ed9t danh m\u1ee5c nh\u00e9");
            return;
        }

        double amount = Double.parseDouble(rawAmount);
        String title = descField.getText().toString().trim();
        if (title.isEmpty()) title = category;

        String note = noteField.getText().toString().trim();
        String fullNote = note.isEmpty() ? payment : note + " \u00b7 " + payment;

        final TransactionEntity entity =
                new TransactionEntity(title, amount, "EXPENSE", category, fullNote, pickedTime);
        final android.content.Context app = getContext().getApplicationContext();

        Db.io(() -> {
            AppDatabase.dao(app).insert(entity);
            try {
                new FirebaseSyncManager(app).uploadAllLocal();
            } catch (Throwable ignored) {
                // kh\u00f4ng c\u00f3 m\u1ea1ng th\u00ec b\u1ecf qua, d\u1eef li\u1ec7u v\u1eabn n\u1eb1m trong m\u00e1y
            }
            Db.ui(() -> {
                toast("\u0110\u00e3 l\u01b0u giao d\u1ecbch");
                resetForm();
                open(MainActivity.TAB_HOME);
            });
        });
    }

    private void resetForm() {
        if (root == null) return;
        ((EditText) root.findViewById(R.id.edt_amount)).setText("");
        ((EditText) root.findViewById(R.id.edt_description)).setText("");
        ((EditText) root.findViewById(R.id.edt_note)).setText("");
        pickedTime = System.currentTimeMillis();
        text(R.id.tv_date, format.format(new Date(pickedTime)));
        buildCategories();
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh =
                root.findViewById(R.id.refresh_add_expense);
        if (refresh != null) refresh.setRefreshing(false);
    }

    private void open(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(tab);
        }
    }

    private void toast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void text(int id, String value) {
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
