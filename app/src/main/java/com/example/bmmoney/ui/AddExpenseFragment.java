package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.MainActivity;
import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Man Them chi tieu: nhap so tien, danh muc, mo ta, ghi chu va luu vao Room + Firebase. */
public class AddExpenseFragment extends Fragment {

    private static final int[] CELL_IDS = {R.id.cat_cell_0, R.id.cat_cell_1, R.id.cat_cell_2,
            R.id.cat_cell_3, R.id.cat_cell_4, R.id.cat_cell_5};
    private static final int[] LABEL_IDS = {R.id.cat_label_0, R.id.cat_label_1, R.id.cat_label_2,
            R.id.cat_label_3, R.id.cat_label_4, R.id.cat_label_5};
    private static final String[] CATEGORIES = {"\u0102n u\u1ed1ng", "Di chuy\u1ec3n", "H\u00f3a \u0111\u01a1n",
            "Mua s\u1eafm", "Y t\u1ebf", "Gi\u1ea3i tr\u00ed"};

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private View root;
    private int selected = 0;
    private long selectedDate = System.currentTimeMillis();
    private String payment = "\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_add_expense, container, false);

        for (int i = 0; i < CELL_IDS.length; i++) {
            final int index = i;
            root.findViewById(CELL_IDS[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(index);
                }
            });
        }
        select(0);

        ((TextView) root.findViewById(R.id.tv_date)).setText(dateFormat.format(new Date(selectedDate)));
        root.findViewById(R.id.tv_date).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate();
            }
        });

        ((TextView) root.findViewById(R.id.tv_payment)).setText(payment);
        root.findViewById(R.id.tv_payment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickPayment();
            }
        });

        root.findViewById(R.id.btn_submit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        root.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                back();
            }
        });

        root.findViewById(R.id.btn_apply_suggestion).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(0);
                ((EditText) root.findViewById(R.id.edt_description)).setText("VinMart Tr\u1ea7n Duy H\u01b0ng");
                Toast.makeText(getContext(), "\u0110\u00e3 \u00e1p d\u1ee5ng g\u1ee3i \u00fd", Toast.LENGTH_SHORT).show();
            }
        });

        root.findViewById(R.id.btn_scan_receipt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "T\u00ednh n\u0103ng qu\u00e9t h\u00f3a \u0111\u01a1n s\u1ebd \u0111\u01b0\u1ee3c b\u1ed5 sung", Toast.LENGTH_SHORT).show();
            }
        });

        ViewUtils.floatForever(root.findViewById(R.id.scan_icon_box), 4f);
        return root;
    }

    private void select(int index) {
        selected = index;
        for (int i = 0; i < CELL_IDS.length; i++) {
            boolean active = i == index;
            root.findViewById(CELL_IDS[i])
                    .setBackgroundResource(active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
            TextView label = root.findViewById(LABEL_IDS[i]);
            label.setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.cream : R.color.dark_green));
        }
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(selectedDate);
        new DatePickerDialog(requireContext(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, day);
                selectedDate = picked.getTimeInMillis();
                ((TextView) root.findViewById(R.id.tv_date)).setText(dateFormat.format(new Date(selectedDate)));
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickPayment() {
        final String[] options = {"\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng", "\ud83d\udcb5 Ti\u1ec1n m\u1eb7t", "\ud83d\udcf1 V\u00ed \u0111i\u1ec7n t\u1eed", "\ud83c\udfe6 Chuy\u1ec3n kho\u1ea3n"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n")
                .setItems(options, (dialog, which) -> {
                    payment = options[which];
                    ((TextView) root.findViewById(R.id.tv_payment)).setText(payment);
                })
                .show();
    }

    private void save() {
        String rawAmount = ((EditText) root.findViewById(R.id.edt_amount)).getText().toString()
                .replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(rawAmount)) {
            Toast.makeText(getContext(), "Vui l\u00f2ng nh\u1eadp s\u1ed1 ti\u1ec1n", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount = Double.parseDouble(rawAmount);
        String description = ((EditText) root.findViewById(R.id.edt_description)).getText().toString().trim();
        String note = ((EditText) root.findViewById(R.id.edt_note)).getText().toString().trim();
        if (TextUtils.isEmpty(description)) {
            description = CATEGORIES[selected];
        }
        if (!TextUtils.isEmpty(payment)) {
            note = TextUtils.isEmpty(note) ? payment : note + " \u00b7 " + payment;
        }

        TransactionEntity transaction = new TransactionEntity(
                description, amount, "EXPENSE", CATEGORIES[selected], note, selectedDate);
        long id = AppDatabase.getInstance(requireContext()).transactionDao().insert(transaction);
        transaction.setId((int) id);

        try {
            new FirebaseSyncManager(requireContext()).uploadTransaction(transaction);
        } catch (Throwable ignored) {
        }

        Toast.makeText(getContext(), "\u0110\u00e3 l\u01b0u chi ti\u00eau", Toast.LENGTH_SHORT).show();
        ((EditText) root.findViewById(R.id.edt_amount)).setText("");
        ((EditText) root.findViewById(R.id.edt_description)).setText("");
        ((EditText) root.findViewById(R.id.edt_note)).setText("");
        back();
    }

    private void back() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(MainActivity.TAB_HOME);
        }
    }
}
