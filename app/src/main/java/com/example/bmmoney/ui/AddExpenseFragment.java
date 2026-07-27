package com.example.bmmoney.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Man Them chi tieu: chi nhap tay (da bo quet hoa don va goi y AI),
 * cho chon ca ngay va gio.
 */
public class AddExpenseFragment extends Fragment {

    private static final int[] CELL = {R.id.cat_cell_0, R.id.cat_cell_1, R.id.cat_cell_2,
            R.id.cat_cell_3, R.id.cat_cell_4, R.id.cat_cell_5};
    private static final int[] LABEL = {R.id.cat_label_0, R.id.cat_label_1, R.id.cat_label_2,
            R.id.cat_label_3, R.id.cat_label_4, R.id.cat_label_5};
    private static final String[] PAYMENTS = {
            "\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng",
            "\ud83d\udcb5 Ti\u1ec1n m\u1eb7t",
            "\ud83d\udcf1 V\u00ed \u0111i\u1ec7n t\u1eed",
            "\ud83c\udfe6 Chuy\u1ec3n kho\u1ea3n"};

    private final SimpleDateFormat display = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final Calendar picked = Calendar.getInstance();

    private View root;
    private String category = "";
    private int paymentIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_add_expense, container, false);

        root.findViewById(R.id.btn_back).setOnClickListener(v -> open(MainActivity.TAB_HOME));
        root.findViewById(R.id.tv_date).setOnClickListener(v -> pickDateTime());
        root.findViewById(R.id.tv_payment).setOnClickListener(v -> {
            paymentIndex = (paymentIndex + 1) % PAYMENTS.length;
            text(R.id.tv_payment, PAYMENTS[paymentIndex]);
        });
        root.findViewById(R.id.btn_submit).setOnClickListener(v -> save());

        text(R.id.tv_payment, PAYMENTS[paymentIndex]);
        updateDateLabel();
        bindCategories();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Danh muc co the vua duoc them/sua trong Cai dat
        bindCategories();
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }

    /** Nap 6 o danh muc dau tien tu danh sach danh muc tuy chinh. */
    private void bindCategories() {
        if (root == null || getContext() == null) return;
        List<Categories.Item> items = Categories.all(getContext());
        for (int i = 0; i < CELL.length; i++) {
            View cell = root.findViewById(CELL[i]);
            TextView label = root.findViewById(LABEL[i]);
            if (cell == null) continue;
            if (i < items.size()) {
                final Categories.Item item = items.get(i);
                cell.setVisibility(View.VISIBLE);
                if (label != null) label.setText(item.emoji + " " + item.name);
                cell.setOnClickListener(v -> {
                    category = item.name;
                    highlight();
                });
            } else {
                cell.setVisibility(View.GONE);
            }
        }
        if (category.isEmpty() && !items.isEmpty()) {
            category = items.get(0).name;
        }
        highlight();
    }

    private void highlight() {
        if (root == null || getContext() == null) return;
        List<Categories.Item> items = Categories.all(getContext());
        for (int i = 0; i < CELL.length && i < items.size(); i++) {
            View cell = root.findViewById(CELL[i]);
            if (cell == null) continue;
            boolean active = items.get(i).name.equals(category);
            cell.setBackgroundResource(active ? R.drawable.bg_pill_olive : R.drawable.bg_field_small);
            TextView label = root.findViewById(LABEL[i]);
            if (label != null) {
                label.setTextColor(getResources().getColor(active ? R.color.cream : R.color.dark_green));
            }
        }
    }

    /** Chon ngay roi chon gio, luu day du ca ngay lan gio. */
    private void pickDateTime() {
        if (getContext() == null) return;
        new DatePickerDialog(getContext(), (view, year, month, day) -> {
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(getContext(), (timeView, hour, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hour);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);
                updateDateLabel();
            }, picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true).show();
        }, picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel() {
        text(R.id.tv_date, display.format(picked.getTime()));
    }

    private void save() {
        if (root == null || getContext() == null) return;

        EditText amountInput = root.findViewById(R.id.edt_amount);
        EditText descInput = root.findViewById(R.id.edt_description);
        EditText noteInput = root.findViewById(R.id.edt_note);

        String rawAmount = amountInput.getText().toString().replaceAll("[^0-9]", "");
        if (rawAmount.isEmpty()) {
            toast("Nh\u1eadp s\u1ed1 ti\u1ec1n tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9");
            return;
        }
        double amount = Double.parseDouble(rawAmount);
        String title = descInput.getText().toString().trim();
        if (title.isEmpty()) title = category.isEmpty() ? "Chi ti\u00eau" : category;

        String note = noteInput.getText().toString().trim();
        String payment = PAYMENTS[paymentIndex];
        if (!note.isEmpty()) note = note + " \u00b7 " + payment;
        else note = payment;

        final TransactionEntity entity = new TransactionEntity(title, amount, "EXPENSE",
                category.isEmpty() ? "Kh\u00e1c" : category, note, picked.getTimeInMillis());

        Db.io(() -> {
            AppDatabase.dao(requireContext().getApplicationContext()).insert(entity);
            new FirebaseSyncManager(requireContext().getApplicationContext()).uploadAllLocal();
            Db.ui(() -> {
                if (!isAdded()) return;
                toast("\u0110\u00e3 l\u01b0u giao d\u1ecbch");
                amountInput.setText("");
                descInput.setText("");
                noteInput.setText("");
                open(MainActivity.TAB_HOME);
            });
        });
    }

    private void open(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(tab);
        }
    }

    private void toast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void text(int id, String value) {
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
