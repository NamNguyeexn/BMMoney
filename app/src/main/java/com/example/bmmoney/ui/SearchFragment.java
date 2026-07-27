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
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Man Tim kiem. Da bo goi y "ngon ngu tu nhien".
 * Bo loc nhanh: Tuan nay, Khoang thoi gian nay (theo ngay chot chu ky), Nam nay,
 * An uong, Tren 100k.
 */
public class SearchFragment extends Fragment {

    private View root;
    private TransactionAdapter adapter;
    private EditText input;

    private boolean filterWeek = false;
    private boolean filterCycle = false;
    private boolean filterYear = false;
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
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(false);
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

        root.findViewById(R.id.chip_week).setOnClickListener(v -> {
            filterWeek = !filterWeek;
            if (filterWeek) {
                filterCycle = false;
                filterYear = false;
            }
            refreshChips();
            apply();
        });
        root.findViewById(R.id.chip_month).setOnClickListener(v -> {
            filterCycle = !filterCycle;
            if (filterCycle) {
                filterWeek = false;
                filterYear = false;
            }
            refreshChips();
            apply();
        });
        View chipYear = root.findViewById(R.id.chip_year);
        if (chipYear != null) {
            chipYear.setOnClickListener(v -> {
                filterYear = !filterYear;
                if (filterYear) {
                    filterWeek = false;
                    filterCycle = false;
                }
                refreshChips();
                apply();
            });
        }
        root.findViewById(R.id.chip_food).setOnClickListener(v -> {
            filterFood = !filterFood;
            refreshChips();
            apply();
        });
        root.findViewById(R.id.chip_over100k).setOnClickListener(v -> {
            filterOver100k = !filterOver100k;
            refreshChips();
            apply();
        });

        refreshChips();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        apply();
    }

    @Override
    public void onDestroyView() {
        RecyclerView recycler = root == null ? null : (RecyclerView) root.findViewById(R.id.recycler_results);
        if (recycler != null) recycler.setAdapter(null);
        adapter = null;
        input = null;
        root = null;
        super.onDestroyView();
    }

    private void refreshChips() {
        chipState(R.id.chip_week, filterWeek);
        chipState(R.id.chip_month, filterCycle);
        chipState(R.id.chip_year, filterYear);
        chipState(R.id.chip_food, filterFood);
        chipState(R.id.chip_over100k, filterOver100k);
    }

    private void chipState(int id, boolean active) {
        if (root == null) return;
        View chip = root.findViewById(id);
        if (chip == null) return;
        chip.setBackgroundResource(active ? R.drawable.bg_pill_olive : R.drawable.bg_pill_cream);
        if (chip instanceof TextView) {
            ((TextView) chip).setTextColor(getResources().getColor(active ? R.color.cream : R.color.dark_green));
        }
    }

    private void apply() {
        if (root == null || getContext() == null || input == null) return;

        final String keyword = input.getText().toString().trim().toLowerCase(Locale.getDefault());
        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final long weekStart = Cycle.startOfWeek(now);
        final long[] cycle = Cycle.bounds(cycleDay, now, 0);
        final long yearStart = Cycle.startOfYear(now);
        final long yearEnd = Cycle.endOfYear(now);
        final boolean week = filterWeek;
        final boolean inCycle = filterCycle;
        final boolean year = filterYear;
        final boolean food = filterFood;
        final boolean over = filterOver100k;

        Db.load(() -> {
            List<TransactionEntity> result = new ArrayList<>();
            List<TransactionEntity> all = AppDatabase
                    .dao(requireContext().getApplicationContext()).getAllTransactions();
            if (all == null) return result;
            for (TransactionEntity t : all) {
                if (!keyword.isEmpty() && !matches(t, keyword)) continue;
                if (week && t.getDate() < weekStart) continue;
                if (inCycle && (t.getDate() < cycle[0] || t.getDate() > cycle[1])) continue;
                if (year && (t.getDate() < yearStart || t.getDate() > yearEnd)) continue;
                if (food && !"\u0102n u\u1ed1ng".equals(t.getCategory())) continue;
                if (over && t.getAmount() < 100000d) continue;
                result.add(t);
            }
            return result;
        }, result -> {
            if (root == null || result == null || adapter == null) return;

            double total = 0d;
            for (TransactionEntity t : result) {
                if ("EXPENSE".equals(t.getType())) total += t.getAmount();
            }

            adapter.setTransactions(result);
            root.findViewById(R.id.tv_empty_results).setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.recycler_results).setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);

            text(R.id.tv_result_count, result.size() + " k\u1ebft qu\u1ea3");
            text(R.id.tv_search_total, "-" + Money.vnd(total));
            text(R.id.tv_search_sub, result.size() + " giao d\u1ecbch ph\u00f9 h\u1ee3p");
            text(R.id.tv_search_total_label, label(keyword, inCycle, year, cycle));
        });
    }

    private String label(String keyword, boolean inCycle, boolean year, long[] cycle) {
        if (!keyword.isEmpty()) {
            return "T\u1ed5ng chi ti\u00eau cho \u201c" + keyword + "\u201d";
        }
        if (inCycle) {
            return "T\u1ed5ng chi ti\u00eau " + Cycle.rangeLabel(cycle[0], cycle[1]);
        }
        if (year) {
            return "T\u1ed5ng chi ti\u00eau n\u0103m nay";
        }
        return "T\u1ed5ng chi ti\u00eau";
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
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
