package com.example.bmmoney.ui;

import android.content.SharedPreferences;
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
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.DonutChartView;

import java.util.ArrayList;
import java.util.List;

/** Man Trang chu: tong quan ngan sach, bieu do tron, danh muc va giao dich gan day. */
public class DashboardFragment extends Fragment {

    private static final int[] CAT_NAME = {R.id.cat_name_0, R.id.cat_name_1, R.id.cat_name_2, R.id.cat_name_3, R.id.cat_name_4};
    private static final int[] CAT_PCT = {R.id.cat_pct_0, R.id.cat_pct_1, R.id.cat_pct_2, R.id.cat_pct_3, R.id.cat_pct_4};
    private static final int[] CAT_AMT = {R.id.cat_amt_0, R.id.cat_amt_1, R.id.cat_amt_2, R.id.cat_amt_3, R.id.cat_amt_4};
    private static final int[] CAT_BAR = {R.id.cat_bar_0, R.id.cat_bar_1, R.id.cat_bar_2, R.id.cat_bar_3, R.id.cat_bar_4};

    private View root;
    private TransactionAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        RecyclerView recycler = root.findViewById(R.id.recycler_recent);
        adapter = new TransactionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        root.findViewById(R.id.btn_header_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(MainActivity.TAB_ADD);
            }
        });
        root.findViewById(R.id.btn_view_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(MainActivity.TAB_SEARCH);
            }
        });

        ViewUtils.floatForever(root.findViewById(R.id.ai_sparkle_box), 4f);
        reload();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void open(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(tab);
        }
    }

    /** Nap lai toan bo so lieu tu Room. */
    public void reload() {
        if (root == null || getContext() == null) return;

        List<TransactionEntity> all = AppDatabase.getInstance(getContext())
                .transactionDao().getAllTransactions();
        if (all == null) all = new ArrayList<>();

        long monthStart = Stats.startOfMonth(0);
        long monthEnd = Stats.endOfMonth(0);
        double expense = Stats.totalExpense(all, monthStart, monthEnd);
        double lastMonth = Stats.totalExpense(all, Stats.startOfMonth(-1), Stats.endOfMonth(-1));
        double budget = Prefs.budget(getContext());
        double remaining = budget - expense;
        double usedPercent = budget > 0 ? expense / budget * 100d : 0d;
        int daysLeft = Stats.daysLeftInMonth();

        text(R.id.tv_greeting, "Xin ch\u00e0o, " + Prefs.userName(getContext()) + "! \ud83d\udc4b");
        text(R.id.tv_total_expense, Money.vnd(expense));
        text(R.id.tv_budget_line, "Ng\u00e2n s\u00e1ch: " + Money.vnd(budget));
        text(R.id.tv_remaining, "C\u00f2n l\u1ea1i: " + Money.vnd(remaining));
        text(R.id.tv_used_percent, Money.percent(usedPercent) + " \u0111\u00e3 d\u00f9ng");
        text(R.id.tv_days_left, "C\u00f2n " + daysLeft + " ng\u00e0y");
        text(R.id.tv_days_left_stat, daysLeft + " ng\u00e0y");
        text(R.id.tv_vs_last_month, Money.signedPercent(Stats.changePercent(expense, lastMonth)));

        ViewUtils.animateBar(root.findViewById(R.id.budget_bar), (float) Math.min(100d, usedPercent), 900, 200);

        List<Stats.Slice> slices = Stats.topWithOther(Stats.byCategory(all, monthStart, monthEnd), 5);
        float[] percents = new float[Math.max(1, slices.size())];
        for (int i = 0; i < CAT_NAME.length; i++) {
            if (i < slices.size()) {
                Stats.Slice slice = slices.get(i);
                double pct = expense > 0 ? slice.total / expense * 100d : 0d;
                percents[i] = (float) pct;
                text(CAT_NAME[i], slice.name);
                text(CAT_PCT[i], Money.percent(pct));
                text(CAT_AMT[i], Money.vnd(slice.total));
                ViewUtils.animateBar(root.findViewById(CAT_BAR[i]), (float) pct, 800, 120L * i);
            } else {
                text(CAT_NAME[i], "\u2014");
                text(CAT_PCT[i], "0%");
                text(CAT_AMT[i], Money.vnd(0));
                ViewUtils.animateBar(root.findViewById(CAT_BAR[i]), 0f, 300, 0);
            }
        }

        DonutChartView donut = root.findViewById(R.id.donut_chart);
        double change = Stats.changePercent(expense, lastMonth);
        String arrow = change >= 0 ? "\u2191" : "\u2193";
        donut.setData(slices.isEmpty() ? null : percents, Money.shortVnd(expense),
                arrow + " " + String.format(java.util.Locale.US, "%.1f", Math.abs(change)) + "% th\u00e1ng tr\u01b0\u1edbc");

        List<TransactionEntity> recent = all.size() > 5 ? all.subList(0, 5) : all;
        adapter.setTransactions(new ArrayList<>(recent));
        root.findViewById(R.id.tv_empty_recent).setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.recycler_recent).setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void text(int id, String value) {
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
