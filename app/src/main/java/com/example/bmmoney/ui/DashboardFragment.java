package com.example.bmmoney.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.MainActivity;
import com.example.bmmoney.R;
import com.example.bmmoney.adapter.TransactionAdapter;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.DonutChartView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Man Trang chu. Moi so lieu duoc tinh theo chu ky tuy chinh (ngay chot dd/mm)
 * chu khong con co dinh theo thang duong lich.
 */
public class DashboardFragment extends Fragment {

    private static final int[] CAT_NAME = {R.id.cat_name_0, R.id.cat_name_1, R.id.cat_name_2, R.id.cat_name_3, R.id.cat_name_4};
    private static final int[] CAT_PCT = {R.id.cat_pct_0, R.id.cat_pct_1, R.id.cat_pct_2, R.id.cat_pct_3, R.id.cat_pct_4};
    private static final int[] CAT_AMT = {R.id.cat_amt_0, R.id.cat_amt_1, R.id.cat_amt_2, R.id.cat_amt_3, R.id.cat_amt_4};
    private static final int[] CAT_BAR = {R.id.cat_bar_0, R.id.cat_bar_1, R.id.cat_bar_2, R.id.cat_bar_3, R.id.cat_bar_4};

    private View root;
    private TransactionAdapter adapter;

    /** Goi du lieu cua mot lan nap, tinh san tren luong nen. */
    private static class Data {
        double expense;
        double previous;
        List<CategoryTotal> categories = new ArrayList<>();
        List<TransactionEntity> recent = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        RecyclerView recycler = root.findViewById(R.id.recycler_recent);
        adapter = new TransactionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(false);
        recycler.setAdapter(adapter);

        root.findViewById(R.id.btn_header_add).setOnClickListener(v -> open(MainActivity.TAB_ADD));
        root.findViewById(R.id.btn_view_all).setOnClickListener(v -> open(MainActivity.TAB_SEARCH));

        // Nhan vao "Con N ngay" de doi ngay chot chu ky
        View daysLeft = root.findViewById(R.id.tv_days_left);
        if (daysLeft != null) {
            daysLeft.setOnClickListener(v -> CycleDialog.show(getContext(), this::reload));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        RecyclerView recycler = root == null ? null : (RecyclerView) root.findViewById(R.id.recycler_recent);
        if (recycler != null) recycler.setAdapter(null);
        adapter = null;
        root = null;
        super.onDestroyView();
    }

    private void open(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(tab);
        }
    }

    /** Nap lai so lieu cua ky hien tai (truy van chay tren luong nen). */
    public void reload() {
        if (root == null || getContext() == null) return;

        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final long[] current = Cycle.bounds(cycleDay, now, 0);
        final long[] previous = Cycle.bounds(cycleDay, now, -1);
        final double budget = Prefs.budget(getContext());
        final int daysLeft = Cycle.daysLeft(cycleDay, now);
        final String range = Cycle.rangeLabel(current[0], current[1]);
        final TransactionDao dao = AppDatabase.dao(getContext());

        text(R.id.tv_greeting, "Xin ch\u00e0o, " + Prefs.userName(getContext()) + "! \ud83d\udc4b");
        text(R.id.tv_days_left, "C\u00f2n " + daysLeft + " ng\u00e0y");
        text(R.id.tv_days_left_stat, daysLeft + " ng\u00e0y");
        text(R.id.tv_period_range, range);

        Db.load(() -> {
            Data data = new Data();
            data.expense = value(dao.getExpenseInRange(current[0], current[1]));
            data.previous = value(dao.getExpenseInRange(previous[0], previous[1]));
            List<CategoryTotal> cats = dao.getExpenseByCategoryInRange(current[0], current[1]);
            if (cats != null) data.categories = cats;
            List<TransactionEntity> recent = dao.getRecent(5);
            if (recent != null) data.recent = recent;
            return data;
        }, data -> {
            if (root == null || data == null) return;
            bind(data, budget, range);
        });
    }

    private void bind(Data data, double budget, String range) {
        double expense = data.expense;
        double remaining = budget - expense;
        double usedPercent = budget > 0 ? expense / budget * 100d : 0d;

        text(R.id.tv_total_expense, Money.vnd(expense));
        text(R.id.tv_budget_line, "Ng\u00e2n s\u00e1ch chu k\u1ef3: " + Money.vnd(budget));
        text(R.id.tv_remaining, "C\u00f2n l\u1ea1i: " + Money.vnd(remaining));
        text(R.id.tv_used_percent, Money.percent(usedPercent) + " \u0111\u00e3 d\u00f9ng");
        text(R.id.tv_vs_last_month, Money.signedPercent(Stats.changePercent(expense, data.previous)));

        ViewUtils.animateBar(root.findViewById(R.id.budget_bar), (float) Math.min(100d, usedPercent), 700, 150);

        List<Stats.Slice> slices = Stats.topWithOther(toSlices(data.categories), 5);
        float[] percents = new float[Math.max(1, slices.size())];
        for (int i = 0; i < CAT_NAME.length; i++) {
            if (i < slices.size()) {
                Stats.Slice slice = slices.get(i);
                double pct = expense > 0 ? slice.total / expense * 100d : 0d;
                percents[i] = (float) pct;
                text(CAT_NAME[i], slice.name);
                text(CAT_PCT[i], Money.percent(pct));
                text(CAT_AMT[i], Money.vnd(slice.total));
                ViewUtils.animateBar(root.findViewById(CAT_BAR[i]), (float) pct, 600, 90L * i);
            } else {
                text(CAT_NAME[i], "\u2014");
                text(CAT_PCT[i], "0%");
                text(CAT_AMT[i], Money.vnd(0));
                ViewUtils.animateBar(root.findViewById(CAT_BAR[i]), 0f, 200, 0);
            }
        }

        DonutChartView donut = root.findViewById(R.id.donut_chart);
        double change = Stats.changePercent(expense, data.previous);
        String arrow = change >= 0 ? "\u2191" : "\u2193";
        donut.setData(slices.isEmpty() ? null : percents, Money.shortVnd(expense),
                arrow + " " + String.format(Locale.US, "%.1f", Math.abs(change)) + "% so v\u1edbi k\u1ef3 tr\u01b0\u1edbc");

        adapter.setTransactions(data.recent);
        root.findViewById(R.id.tv_empty_recent).setVisibility(data.recent.isEmpty() ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.recycler_recent).setVisibility(data.recent.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<Stats.Slice> toSlices(List<CategoryTotal> totals) {
        List<Stats.Slice> out = new ArrayList<>();
        if (totals == null) return out;
        for (CategoryTotal c : totals) {
            String name = c.category == null || c.category.isEmpty() ? "Kh\u00e1c" : c.category;
            out.add(new Stats.Slice(name, c.total));
        }
        return out;
    }

    private double value(Double v) {
        return v == null ? 0d : v;
    }

    private void text(int id, String value) {
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
