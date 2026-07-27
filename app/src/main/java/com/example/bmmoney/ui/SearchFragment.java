package com.example.bmmoney.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.R;
import com.example.bmmoney.adapter.TransactionAdapter;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Man Tim kiem: loc giao dich theo tu khoa va cac bo loc nhanh. */
public class SearchFragment extends Fragment {

    private View root;
    private TransactionAdapter adapter;
    private EditText input;

    private boolean filterWeek = false;
    private boolean filterMonth = false;
    private boolean filterFood = false;
    private boolean filterOver100k = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_search, container, false);

        adapter = new TransactionAdapter();
        RecyclerView recycler = root.findViewById(R.id.recycler_results);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        input = root.findViewById(R.id.edt_search);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                apply();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        root.findViewById(R.id.chip_week).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterWeek = !filterWeek;
                chipState(v, filterWeek);
                apply();
            }
        });
        root.findViewById(R.id.chip_month).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterMonth = !filterMonth;
                chipState(v, filterMonth);
                apply();
            }
        });
        root.findViewById(R.id.chip_food).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterFood = !filterFood;
                chipState(v, filterFood);
                apply();
            }
        });
        root.findViewById(R.id.chip_over100k).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterOver100k = !filterOver100k;
                chipState(v, filterOver100k);
                apply();
            }
        });

        setExample(R.id.chip_example_0, "Di chuy\u1ec3n");
        setExample(R.id.chip_example_1, "Mua s\u1eafm");
        setExample(R.id.chip_example_2, "Th\u1ebb");

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        apply();
    }

    private void setExample(int id, final String keyword) {
        View view = root.findViewById(id);
        if (view == null) return;
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText(keyword);
            }
        });
    }

    private void chipState(View chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_pill_olive : R.drawable.bg_pill_cream);
        if (chip instanceof TextView) {
            ((TextView) chip).setTextColor(getResources().getColor(active ? R.color.cream : R.color.dark_green));
        }
    }

    private void apply() {
        if (root == null || getContext() == null) return;

        List<TransactionEntity> all = AppDatabase.getInstance(getContext())
                .transactionDao().getAllTransactions();
        if (all == null) all = new ArrayList<>();

        String keyword = input.getText().toString().trim().toLowerCase(Locale.getDefault());
        long weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        long monthStart = Stats.startOfMonth(0);

        List<TransactionEntity> result = new ArrayList<>();
        double total = 0;
        for (TransactionEntity t : all) {
            if (!keyword.isEmpty() && !matches(t, keyword)) continue;
            if (filterWeek && t.getDate() < weekStart) continue;
            if (filterMonth && t.getDate() < monthStart) continue;
            if (filterFood && !"\u0102n u\u1ed1ng".equals(t.getCategory())) continue;
            if (filterOver100k && t.getAmount() < 100000d) continue;
            result.add(t);
            total += "EXPENSE".equals(t.getType()) ? t.getAmount() : 0d;
        }

        adapter.setTransactions(result);
        root.findViewById(R.id.tv_empty_results).setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.recycler_results).setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);

        text(R.id.tv_result_count, result.size() + " k\u1ebft qu\u1ea3");
        text(R.id.tv_search_total, "-" + Money.vnd(total));
        text(R.id.tv_search_sub, result.size() + " giao d\u1ecbch ph\u00f9 h\u1ee3p");
        text(R.id.tv_search_total_label, keyword.isEmpty()
                ? "T\u1ed5ng chi ti\u00eau" : "T\u1ed5ng chi ti\u00eau cho \u201c" + keyword + "\u201d");
    }

    private boolean matches(TransactionEntity t, String keyword) {
        return contains(t.getTitle(), keyword)
                || contains(t.getCategory(), keyword)
                || contains(t.getNote(), keyword)
                || contains(String.valueOf((long) t.getAmount()), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(keyword);
    }

    private void text(int id, String value) {
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }
}
