package com.example.bmmoney.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.TrendChartView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M\u00e0n Ph\u00e2n t\u00edch: xu h\u01b0\u1edbng chi ti\u00eau theo t\u1eebng k\u1ef3 (L1, L2, L3...),
 * so s\u00e1nh danh m\u1ee5c k\u1ef3 n\u00e0y v\u1edbi k\u1ef3 tr\u01b0\u1edbc v\u00e0 \u0111i\u1ec3m s\u00e1ng / \u0111i\u1ec3m c\u1ea7n l\u01b0u \u00fd.
 */
public class AnalyticsFragment extends Fragment {

    private static final int[] AN_NAME = {R.id.an_name_0, R.id.an_name_1, R.id.an_name_2, R.id.an_name_3};
    private static final int[] AN_AMT = {R.id.an_amt_0, R.id.an_amt_1, R.id.an_amt_2, R.id.an_amt_3};
    private static final int[] AN_THIS = {R.id.an_this_0, R.id.an_this_1, R.id.an_this_2, R.id.an_this_3};
    private static final int[] AN_LAST = {R.id.an_last_0, R.id.an_last_1, R.id.an_last_2, R.id.an_last_3};

    private View root;
    private SwipeRefreshLayout refresh;

    /** false = xem theo k\u1ef3 (6 k\u1ef3 g\u1ea7n nh\u1ea5t), true = xem theo n\u0103m (12 k\u1ef3). */
    private boolean yearMode = false;
    private boolean animated = false;

    /** S\u1ed1 li\u1ec7u m\u1ed9t l\u1ea7n n\u1ea1p, t\u00ednh s\u1eb5n tr\u00ean lu\u1ed3ng n\u1ec1n. */
    private static class Data {
        double[] trend;
        String[] labels;
        double total;
        double previousTotal;
        List<String> names = new ArrayList<>();
        List<Double> thisAmounts = new ArrayList<>();
        List<Double> lastAmounts = new ArrayList<>();
        String bestName = "\u2014";
        double bestChange = 0;
        String worstName = "\u2014";
        double worstChange = 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_analytics, container, false);

        refresh = Refresh.setup(root, R.id.refresh_analytics, this::reloadByUser);

        root.findViewById(R.id.btn_period_month).setOnClickListener(v -> setMode(false));
        root.findViewById(R.id.btn_period_year).setOnClickListener(v -> setMode(true));
        applyModeStyle();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        refresh = null;
        root = null;
        super.onDestroyView();
    }

    private void setMode(boolean year) {
        if (yearMode == year) return;
        yearMode = year;
        animated = false;
        applyModeStyle();
        reload();
    }

    private void applyModeStyle() {
        if (root == null || getContext() == null) return;
        TextView month = root.findViewById(R.id.btn_period_month);
        TextView year = root.findViewById(R.id.btn_period_year);
        int active = ContextCompat.getColor(getContext(), R.color.cream);
        int inactive = ContextCompat.getColor(getContext(), R.color.dark_green);

        month.setBackgroundResource(yearMode ? 0 : R.drawable.bg_pill_olive);
        month.setTextColor(yearMode ? inactive : active);
        year.setBackgroundResource(yearMode ? R.drawable.bg_pill_olive : 0);
        year.setTextColor(yearMode ? active : inactive);
    }

    public void reloadByUser() {
        animated = false;
        reload();
    }

    public void reload() {
        if (root == null || getContext() == null) return;

        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final int steps = yearMode ? 12 : 6;
        final TransactionDao dao = AppDatabase.dao(getContext());

        long[] current = Cycle.bounds(cycleDay, now, 0);
        text(R.id.tv_trend_period, Cycle.rangeLabel(current[0], current[1]));

        Db.load(() -> {
            Data data = new Data();
            data.trend = new double[steps];
            data.labels = new String[steps];

            for (int i = 0; i < steps; i++) {
                int offset = -(steps - 1 - i);
                long[] bounds = Cycle.bounds(cycleDay, now, offset);
                Double sum = dao.getExpenseInRange(bounds[0], bounds[1]);
                data.trend[i] = sum == null ? 0d : sum;
                data.labels[i] = Cycle.label(cycleDay, bounds[0] + 1000L);
            }
            data.total = data.trend[steps - 1];
            data.previousTotal = steps > 1 ? data.trend[steps - 2] : 0d;

            long[] thisBounds = Cycle.bounds(cycleDay, now, 0);
            long[] lastBounds = Cycle.bounds(cycleDay, now, -1);
            Map<String, Double> thisMap = toMap(dao.getExpenseByCategoryInRange(thisBounds[0], thisBounds[1]));
            Map<String, Double> lastMap = toMap(dao.getExpenseByCategoryInRange(lastBounds[0], lastBounds[1]));

            int count = 0;
            for (Map.Entry<String, Double> entry : thisMap.entrySet()) {
                if (count >= 4) break;
                double thisValue = entry.getValue();
                Double lastValue = lastMap.get(entry.getKey());
                double last = lastValue == null ? 0d : lastValue;
                data.names.add(entry.getKey());
                data.thisAmounts.add(thisValue);
                data.lastAmounts.add(last);
                count++;
            }

            // \u0110i\u1ec3m s\u00e1ng: gi\u1ea3m nhi\u1ec1u nh\u1ea5t. \u0110i\u1ec3m c\u1ea7n l\u01b0u \u00fd: t\u0103ng nhi\u1ec1u nh\u1ea5t.
            double bestChange = Double.MAX_VALUE;
            double worstChange = -Double.MAX_VALUE;
            for (String key : union(thisMap, lastMap)) {
                double a = thisMap.containsKey(key) ? thisMap.get(key) : 0d;
                double b = lastMap.containsKey(key) ? lastMap.get(key) : 0d;
                if (a <= 0 && b <= 0) continue;
                double change = Stats.changePercent(a, b);
                if (change < bestChange) {
                    bestChange = change;
                    data.bestName = key;
                }
                if (change > worstChange) {
                    worstChange = change;
                    data.worstName = key;
                }
            }
            data.bestChange = bestChange == Double.MAX_VALUE ? 0 : bestChange;
            data.worstChange = worstChange == -Double.MAX_VALUE ? 0 : worstChange;
            return data;
        }, data -> {
            if (refresh != null) refresh.setRefreshing(false);
            if (root == null || data == null) return;
            bind(data);
        });
    }

    private void bind(Data data) {
        final boolean animate = !animated;
        animated = true;

        TrendChartView chart = root.findViewById(R.id.trend_chart);
        if (chart != null) chart.setData(data.trend, data.labels);

        double change = Stats.changePercent(data.total, data.previousTotal);
        String direction = change >= 0 ? "t\u0103ng" : "gi\u1ea3m";
        text(R.id.tv_trend_summary, "K\u1ef3 n\u00e0y \u0111\u00e3 chi " + Money.vnd(data.total)
                + " \u00b7 " + direction + " " + Money.percent(Math.abs(change)) + " so v\u1edbi k\u1ef3 tr\u01b0\u1edbc");

        double max = 0;
        for (int i = 0; i < data.thisAmounts.size(); i++) {
            max = Math.max(max, Math.max(data.thisAmounts.get(i), data.lastAmounts.get(i)));
        }

        for (int i = 0; i < AN_NAME.length; i++) {
            if (i < data.names.size()) {
                double thisValue = data.thisAmounts.get(i);
                double lastValue = data.lastAmounts.get(i);
                text(AN_NAME[i], data.names.get(i));
                text(AN_AMT[i], Money.vnd(thisValue));
                bar(AN_THIS[i], max > 0 ? (float) (thisValue / max * 100d) : 0f, animate, 80L * i);
                bar(AN_LAST[i], max > 0 ? (float) (lastValue / max * 100d) : 0f, animate, 80L * i + 40);
            } else {
                text(AN_NAME[i], "\u2014");
                text(AN_AMT[i], Money.vnd(0));
                bar(AN_THIS[i], 0f, false, 0);
                bar(AN_LAST[i], 0f, false, 0);
            }
        }

        text(R.id.tv_best_category, data.bestName);
        text(R.id.tv_best_note, data.bestChange <= 0
                ? "Gi\u1ea3m " + Money.percent(Math.abs(data.bestChange)) + " so v\u1edbi k\u1ef3 tr\u01b0\u1edbc, gi\u1eef nh\u1ecbp n\u00e0y nh\u00e9!"
                : "Ch\u01b0a c\u00f3 danh m\u1ee5c n\u00e0o gi\u1ea3m trong k\u1ef3 n\u00e0y");
        text(R.id.tv_worst_category, data.worstName);
        text(R.id.tv_worst_note, data.worstChange > 0
                ? "T\u0103ng " + Money.percent(data.worstChange) + " so v\u1edbi k\u1ef3 tr\u01b0\u1edbc, c\u00e2n nh\u1eafc c\u1eaft b\u1edbt"
                : "M\u1ecdi danh m\u1ee5c \u0111\u1ec1u trong t\u1ea7m ki\u1ec3m so\u00e1t");
    }

    /** an_this_* v\u00e0 an_last_* l\u00e0 th\u1ebb View, ch\u1ec9 \u0111\u1eb7t \u0111\u1ed9 r\u1ed9ng ch\u1ee9 kh\u00f4ng g\u00e1n ch\u1eef. */
    private void bar(int id, float percent, boolean animate, long delay) {
        View view = root.findViewById(id);
        if (view == null) return;
        if (animate) {
            ViewUtils.animateBar(view, percent, 650, delay);
        } else {
            ViewUtils.setBar(view, percent);
        }
    }

    private Map<String, Double> toMap(List<CategoryTotal> list) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (list == null) return map;
        for (CategoryTotal c : list) {
            String name = c.category == null || c.category.isEmpty() ? "Kh\u00e1c" : c.category;
            map.put(name, c.total);
        }
        return map;
    }

    private List<String> union(Map<String, Double> a, Map<String, Double> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String key : b.keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }
        return keys;
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
