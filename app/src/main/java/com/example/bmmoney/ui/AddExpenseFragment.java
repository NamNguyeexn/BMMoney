package com.example.bmmoney.ui;

import android.content.Context;
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
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Man Them giao dich, dung chung cho hai che do:
 *
 * <ul>
 *   <li>Chi tieu: so tien, mo ta, danh muc, ngay gio, phuong thuc thanh toan, ghi chu</li>
 *   <li>Thu nhap: so tien, ngay gio, cach nhan</li>
 * </ul>
 */
public class AddExpenseFragment extends Fragment {

    /** Phuong thuc thanh toan cho khoan chi. */
    private static final List<String> PAYMENTS = Arrays.asList(
            "\ud83c\udfe6 Chuy\u1ec3n kho\u1ea3n",
            "\ud83d\udcb5 Ti\u1ec1n m\u1eb7t",
            "\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng",
            "\ud83d\udcf1 V\u00ed \u0111i\u1ec7n t\u1eed",
            "\ud83e\uddfe Kh\u00e1c");

    /** Cach nhan tien cho khoan thu. */
    private static final List<String[]> METHODS = Arrays.asList(
            new String[]{"\ud83d\udcbc", "L\u01b0\u01a1ng"},
            new String[]{"\ud83c\udfe6", "Chuy\u1ec3n kho\u1ea3n"},
            new String[]{"\ud83d\udcb5", "Ti\u1ec1n m\u1eb7t"},
            new String[]{"\ud83c\udf81", "Th\u01b0\u1edfng"},
            new String[]{"\ud83d\udcc8", "\u0110\u1ea7u t\u01b0"},
            new String[]{"\ud83e\uddfe", "Kh\u00e1c"});

    private View root;
    private boolean income = false;
    private long pickedTime = System.currentTimeMillis();
    private String payment = PAYMENTS.get(0);
    private String category = "";
    private String method = METHODS.get(0)[1];

    private final List<View> cells = new ArrayList<>();
    private final List<Categories.Item> items = new ArrayList<>();
    private final List<View> methodCells = new ArrayList<>();

    private final SimpleDateFormat format =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

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
                SelectDialog.show(getContext(), "Ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n",
                        PAYMENTS, payment, (index, value) -> {
                            payment = value;
                            text(R.id.tv_payment, payment);
                        }));

        root.findViewById(R.id.btn_mode_expense).setOnClickListener(v -> setMode(false));
        root.findViewById(R.id.btn_mode_income).setOnClickListener(v -> setMode(true));
        root.findViewById(R.id.btn_submit).setOnClickListener(v -> save());

        buildCategories();
        buildMethods();
        applyMode();
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
        methodCells.clear();
        root = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------- che do nhap
    private void setMode(boolean toIncome) {
        if (income == toIncome) return;
        income = toIncome;
        applyMode();
    }

    /** Bat/tat cac o nhap theo che do dang chon. */
    private void applyMode() {
        if (root == null) return;

        text(R.id.tv_add_title, income ? "Th\u00eam thu nh\u1eadp" : "Th\u00eam chi ti\u00eau");
        text(R.id.btn_submit, income ? "Th\u00eam thu nh\u1eadp" : "Th\u00eam chi ti\u00eau");

        show(R.id.box_description, !income);
        show(R.id.box_category, !income);
        show(R.id.box_payment, !income);
        show(R.id.box_note, !income);
        show(R.id.box_income_method, income);

        tab(R.id.btn_mode_expense, !income);
        tab(R.id.btn_mode_income, income);
    }

    private void show(int id, boolean visible) {
        View view = root.findViewById(id);
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void tab(int id, boolean active) {
        View view = root.findViewById(id);
        if (view == null) return;
        view.setBackgroundResource(active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(getResources().getColor(
                    active ? R.color.dark_green : R.color.olive));
        }
    }

    // ------------------------------------------------------------- danh muc chi tieu
    private void buildCategories() {
        if (root == null || getContext() == null) return;

        LinearLayout container = root.findViewById(R.id.container_cats);
        if (container == null) return;

        container.removeAllViews();
        cells.clear();
        items.clear();
        items.addAll(Categories.all(getContext()));

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (Categories.Item item : items) {
            View cell = inflater.inflate(R.layout.item_cat_cell, container, false);
            ((TextView) cell.findViewById(R.id.tv_cell_emoji)).setText(item.emoji);
            ((TextView) cell.findViewById(R.id.tv_cell_name)).setText(item.name);
            cell.setOnClickListener(v -> {
                category = item.name;
                highlightCategories();
            });
            container.addView(cell);
            cells.add(cell);
        }

        if (category.isEmpty() && !items.isEmpty()) category = items.get(0).name;
        highlightCategories();
    }

    private void highlightCategories() {
        for (int i = 0; i < cells.size() && i < items.size(); i++) {
            boolean active = items.get(i).name.equals(category);
            cells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    // ------------------------------------------------------------- cach nhan tien
    private void buildMethods() {
        if (root == null || getContext() == null) return;

        LinearLayout container = root.findViewById(R.id.container_methods);
        if (container == null) return;

        container.removeAllViews();
        methodCells.clear();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (String[] entry : METHODS) {
            View cell = inflater.inflate(R.layout.item_cat_cell, container, false);
            ((TextView) cell.findViewById(R.id.tv_cell_emoji)).setText(entry[0]);
            ((TextView) cell.findViewById(R.id.tv_cell_name)).setText(entry[1]);
            cell.setOnClickListener(v -> {
                method = entry[1];
                highlightMethods();
            });
            container.addView(cell);
            methodCells.add(cell);
        }
        highlightMethods();
    }

    private void highlightMethods() {
        for (int i = 0; i < methodCells.size() && i < METHODS.size(); i++) {
            boolean active = METHODS.get(i)[1].equals(method);
            methodCells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    // ------------------------------------------------------------- luu
    private void save() {
        if (root == null || getContext() == null) return;

        EditText amountField = root.findViewById(R.id.edt_amount);
        String rawAmount = amountField.getText().toString().replaceAll("[^0-9]", "");
        if (rawAmount.isEmpty()) {
            toast("Nh\u1eadp s\u1ed1 ti\u1ec1n tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9");
            return;
        }
        double amount = Double.parseDouble(rawAmount);

        final TransactionEntity entity = income ? buildIncome(amount) : buildExpense(amount);
        if (entity == null) return;

        final Context app = getContext().getApplicationContext();
        final boolean wasIncome = income;

        Db.io(() -> {
            AppDatabase.dao(app).insert(entity);
            // Khong day len cloud ngay: gom thay doi roi sao luu mot lan cho do ton bo nho
            AutoBackup.scheduleSoon(app);
            Db.ui(() -> {
                toast(wasIncome ? "\u0110\u00e3 l\u01b0u kho\u1ea3n thu"
                        : "\u0110\u00e3 l\u01b0u giao d\u1ecbch");
                resetForm();
                open(MainActivity.TAB_HOME);
            });
        });
    }

    @Nullable
    private TransactionEntity buildExpense(double amount) {
        if (category.isEmpty()) {
            toast("Ch\u1ecdn m\u1ed9t danh m\u1ee5c nh\u00e9");
            return null;
        }
        EditText descField = root.findViewById(R.id.edt_description);
        EditText noteField = root.findViewById(R.id.edt_note);

        String title = descField.getText().toString().trim();
        if (title.isEmpty()) title = category;

        String note = noteField.getText().toString().trim();
        String fullNote = note.isEmpty() ? payment : note + " \u00b7 " + payment;

        return new TransactionEntity(title, amount, Stats.EXPENSE, category, fullNote, pickedTime);
    }

    /** Khoan thu chi can: so tien, ngay gio, cach nhan. */
    private TransactionEntity buildIncome(double amount) {
        return new TransactionEntity(method, amount, Stats.INCOME, method, "", pickedTime);
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
