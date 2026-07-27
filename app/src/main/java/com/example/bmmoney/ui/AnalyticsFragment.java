package com.example.bmmoney.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.TrendChartView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Man Phan tich: so sanh thang nay / thang truoc theo danh muc va xu huong 7 thang. */
public class AnalyticsFragment extends Fragment {

    private static final int[] NAME_IDS = {R.id.an_name_0, R.id.an_name_1, R.id.an_name_2, R.id.an_name_3};
    private static final int[] AMT_IDS = {R.id.an_amt_0, R.id.an_amt_1, R.id.an_amt_2, R.id.an_amt_3};
    private static final int[] THIS_IDS = {R.id.an_this_0, R.id.an_this_1, R.id.an_this_2, R.id.an_this_3};
    private static final int[] LAST_IDS = {R.id.an_last_0, R.id.an_last_1, R.id.an_last_2, R.id.an_last_3};

    private View root;
    private boolean yearMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_analytics, container, false);

        root.findViewById(R.id.btn_period_month).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yearMode = false;
                reload();
            }
        });
        root.findViewById(R.id.btn_period_year).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yearMode = true;
                reload();
            }
        });

        reload();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        if (root == null || getContext() == null) return;

        List<TransactionEntity> all = AppDatabase.getInstance(getContext())
                .transactionDao().getAllTransactions();
        if (all == null) all = new ArrayList<>();

        long fromNow = yearMode ? Stats.startOfMonth(-11) : Stats.startOfMonth(0);
        long toNow = Stats.endOfMonth(0);
        long fromPrev = yearMode ? Stats.startOfMonth(-23) : Stats.startOfMonth(-1);
        long toPrev = yearMode ? Stats.endOfMonth(-12) : Stats.endOfMonth(-1);

        List<Stats.Slice> current = Stats.byCategory(all, fromNow, toNow);
        double totalNow = Stats.totalExpense(all, fromNow, toNow);
        double totalPrev = Stats.totalExpense(all, fromPrev, toPrev);

        double max = 0;
        for (int i = 0; i < NAME_IDS.length && i < current.size(); i++) {
            max = Math.max(max, current.get(i).total);
            max = Math.max(max, categoryTotal(all, current.get(i).name, fromPrev, toPrev));
        }
        if (max <= 0) max = 1;

        for (int i = 0; i < NAME_IDS.length; i++) {
            if (i < current.size()) {
                Stats.Slice slice = current.get(i);
                double prev = categoryTotal(all, slice.name, fromPrev, toPrev);
                text(NAME_IDS[i], slice.name);
                text(AMT_IDS[i], Money.plain(slice.total / 1000d) + " \u20ab");
                ViewUtils.animateBar(root.findViewById(THIS_IDS[i]), (float) (slice.total / max * 100d), 800, 100L * i);
                ViewUtils.animateBar(root.findViewById(LAST_IDS[i]), (float) (prev / max * 100d), 800, 100L * i + 60);
            } else {
                text(NAME_IDS[i], "\u2014");
                text(AMT_IDS[i], "0 \u20ab");
                ViewUtils.animateBar(root.findViewById(THIS_IDS[i]), 0f, 300, 0);
                ViewUtils.animateBar(root.findViewById(LAST_IDS[i]), 0f, 300, 0);
            }
        }

        double change = Stats.changePercent(totalNow, totalPrev);
        text(R.id.tv_trend_summary, Money.signedPercent(change) + " so k\u1ef3 tr\u01b0\u1edbc");

        // Danh muc tiet kiem nhat / vuot muc nhat so voi ky truoc
        String best = "\u2014";
        String worst = "\u2014";
        double bestChange = Double.MAX_VALUE;
        double worstChange = -Double.MAX_VALUE;
        for (Stats.Slice slice : current) {
            double prev = categoryTotal(all, slice.name, fromPrev, toPrev);
            double delta = Stats.changePercent(slice.total, prev);
            if (delta < bestChange) {
                bestChange = delta;
                best = slice.name;
            }
            if (delta > worstChange) {
                worstChange = delta;
                worst = slice.name;
            }
        }
        text(R.id.tv_best_category, best);
        text(R.id.tv_worst_category, worst);
        if (!current.isEmpty()) {
            text(R.id.tv_best_note, String.format(Locale.US, "%.0f", bestChange) + "% ti\u1ebft ki\u1ec7m");
            text(R.id.tv_worst_note, Money.signedPercent(worstChange) + " v\u01b0\u1ee3t m\u1ee9c");
        }

        // Xu huong 7 ky gan nhat
        double[] values = new double[7];
        String[] labels = new String[7];
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            int offset = -(6 - i);
            values[i] = Stats.totalExpense(all, Stats.startOfMonth(offset), Stats.endOfMonth(offset));
            calendar.setTimeInMillis(Stats.startOfMonth(offset));
            labels[i] = "T" + (calendar.get(Calendar.MONTH) + 1);
        }
        TrendChartView chart = root.findViewById(R.id.trend_chart);
        if (chart != null && totalNow > 0) {
            chart.setData(values, labels);
        }
    }

    private double categoryTotal(List<TransactionEntity> all, String category, long from, long to) {
        double sum = 0;
        for (TransactionEntity t : all) {
            if (!"EXPENSE".equals(t.getType())) continue;
            if (t.getDate() < from || t.getDate() > to) continue;
            String key = t.getCategory() == null ? "" : t.getCategory();
            if (key.equals(category)) sum += t.getAmount();
        }
        return sum;
    }

    private void text(int id, String value) {
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
