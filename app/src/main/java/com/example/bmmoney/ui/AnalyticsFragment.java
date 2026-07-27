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
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.TrendChartView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Man Phan tich: so sanh ky nay / ky truoc theo danh muc va xu huong 7 thang. */
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

        // Khong goi reload() o day: onResume() luon chay ngay sau onCreateView()
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }

    private void reload() {
        if (root == null || getContext() == null) return;

        TransactionDao dao = AppDatabase.getInstance(getContext()).transactionDao();

        long fromNow = yearMode ? Stats.startOfMonth(-11) : Stats.startOfMonth(0);
        long toNow = Stats.endOfMonth(0);
        long fromPrev = yearMode ? Stats.startOfMonth(-23) : Stats.startOfMonth(-1);
        long toPrev = yearMode ? Stats.endOfMonth(-12) : Stats.endOfMonth(-1);

        List<CategoryTotal> current = dao.getExpenseByCategoryInRange(fromNow, toNow);
        Map<String, Double> previous = new HashMap<>();
        List<CategoryTotal> prevList = dao.getExpenseByCategoryInRange(fromPrev, toPrev);
        if (prevList != null) {
            for (CategoryTotal c : prevList) {
                previous.put(c.category == null ? "" : c.category, c.total);
            }
        }

        double totalNow = value(dao.getExpenseInRange(fromNow, toNow));
        double totalPrev = value(dao.getExpenseInRange(fromPrev, toPrev));

        int count = current == null ? 0 : current.size();
        double max = 0;
        for (int i = 0; i < NAME_IDS.length && i < count; i++) {
            CategoryTotal c = current.get(i);
            max = Math.max(max, c.total);
            max = Math.max(max, prev(previous, c.category));
        }
        if (max <= 0) max = 1;

        for (int i = 0; i < NAME_IDS.length; i++) {
            if (i < count) {
                CategoryTotal c = current.get(i);
                double prevTotal = prev(previous, c.category);
                text(NAME_IDS[i], c.category == null || c.category.isEmpty() ? "Kh\u00e1c" : c.category);
                text(AMT_IDS[i], Money.plain(c.total / 1000d) + " \u20ab");
                ViewUtils.animateBar(root.findViewById(THIS_IDS[i]), (float) (c.total / max * 100d), 800, 100L * i);
                ViewUtils.animateBar(root.findViewById(LAST_IDS[i]), (float) (prevTotal / max * 100d), 800, 100L * i + 60);
            } else {
                text(NAME_IDS[i], "\u2014");
                text(AMT_IDS[i], "0 \u20ab");
                ViewUtils.animateBar(root.findViewById(THIS_IDS[i]), 0f, 300, 0);
                ViewUtils.animateBar(root.findViewById(LAST_IDS[i]), 0f, 300, 0);
            }
        }

        text(R.id.tv_trend_summary,
                Money.signedPercent(Stats.changePercent(totalNow, totalPrev)) + " so k\u1ef3 tr\u01b0\u1edbc");

        // Danh muc tiet kiem nhat / vuot muc nhat so voi ky truoc
        String best = "\u2014";
        String worst = "\u2014";
        double bestChange = Double.MAX_VALUE;
        double worstChange = -Double.MAX_VALUE;
        if (current != null) {
            for (CategoryTotal c : current) {
                double delta = Stats.changePercent(c.total, prev(previous, c.category));
                if (delta < bestChange) {
                    bestChange = delta;
                    best = c.category;
                }
                if (delta > worstChange) {
                    worstChange = delta;
                    worst = c.category;
                }
            }
        }
        text(R.id.tv_best_category, best);
        text(R.id.tv_worst_category, worst);
        if (count > 0) {
            text(R.id.tv_best_note, String.format(Locale.US, "%.0f", bestChange) + "% ti\u1ebft ki\u1ec7m");
            text(R.id.tv_worst_note, Money.signedPercent(worstChange) + " v\u01b0\u1ee3t m\u1ee9c");
        }

        // Xu huong 7 ky gan nhat
        double[] values = new double[7];
        String[] labels = new String[7];
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            int offset = -(6 - i);
            values[i] = value(dao.getExpenseInRange(Stats.startOfMonth(offset), Stats.endOfMonth(offset)));
            calendar.setTimeInMillis(Stats.startOfMonth(offset));
            labels[i] = "T" + (calendar.get(Calendar.MONTH) + 1);
        }
        TrendChartView chart = root.findViewById(R.id.trend_chart);
        if (chart != null && totalNow > 0) {
            chart.setData(values, labels);
        }
    }

    private double prev(Map<String, Double> map, String category) {
        Double v = map.get(category == null ? "" : category);
        return v == null ? 0d : v;
    }

    private double value(Double v) {
        return v == null ? 0d : v;
    }

    private void text(int id, String value) {
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
