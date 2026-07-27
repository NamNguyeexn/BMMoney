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
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.view.TrendChartView;

import java.util.ArrayList;
import java.util.List;

/**
 * Man Phan tich. Da bo khoi "Nhan dinh boi AI".
 * Xu huong chi tieu duoc ve theo cac ky L1, L2, L3... trong nam:
 * moi ky la khoang giua hai lan chot da cau hinh trong Cai dat.
 */
public class AnalyticsFragment extends Fragment {

    private static final int[] AN_NAME = {R.id.an_name_0, R.id.an_name_1, R.id.an_name_2, R.id.an_name_3};
    private static final int[] AN_AMT = {R.id.an_amt_0, R.id.an_amt_1, R.id.an_amt_2, R.id.an_amt_3};
    private static final int[] AN_THIS = {R.id.an_this_0, R.id.an_this_1, R.id.an_this_2, R.id.an_this_3};
    private static final int[] AN_LAST = {R.id.an_last_0, R.id.an_last_1, R.id.an_last_2, R.id.an_last_3};

    private View root;
    private boolean yearMode = false;

    /** Ket qua tinh toan cua mot lan nap. */
    private static class Data {
        double[] values;
        String[] labels;
        double current;
        double previous;
        List<CategoryTotal> currentCats = new ArrayList<>();
        List<CategoryTotal> previousCats = new ArrayList<>();
        String periodLabel = "";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_analytics, container, false);

        root.findViewById(R.id.btn_period_month).setOnClickListener(v -> {
            yearMode = false;
            reload();
        });
        root.findViewById(R.id.btn_period_year).setOnClickListener(v -> {
            yearMode = true;
            reload();
        });
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

        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final boolean year = yearMode;
        final TransactionDao dao = AppDatabase.dao(getContext());

        Db.load(() -> {
            Data data = new Data();

            // Che do "Nam": ve tu L1 den ky hien tai. Che do "Chu ky": 6 ky gan nhat.
            int currentIndex = Cycle.indexInYear(cycleDay, now);
            int count = year ? Math.max(1, currentIndex) : Math.min(6, Math.max(1, currentIndex));
            data.values = new double[count];
            data.labels = new String[count];

            for (int i = 0; i < count; i++) {
                int offset = -(count - 1 - i);
                long[] bounds = Cycle.bounds(cycleDay, now, offset);
                Double total = dao.getExpenseInRange(bounds[0], bounds[1]);
                data.values[i] = total == null ? 0d : total;
                data.labels[i] = "L" + Cycle.indexInYear(cycleDay, bounds[0]);
            }

            long[] current = Cycle.bounds(cycleDay, now, 0);
            long[] previous = Cycle.bounds(cycleDay, now, -1);
            Double cur = dao.getExpenseInRange(current[0], current[1]);
            Double prev = dao.getExpenseInRange(previous[0], previous[1]);
            data.current = cur == null ? 0d : cur;
            data.previous = prev == null ? 0d : prev;

            List<CategoryTotal> a = dao.getExpenseByCategoryInRange(current[0], current[1]);
            if (a != null) data.currentCats = a;
            List<CategoryTotal> b = dao.getExpenseByCategoryInRange(previous[0], previous[1]);
            if (b != null) data.previousCats = b;

            data.periodLabel = Cycle.label(cycleDay, now) + " \u00b7 " + Cycle.rangeLabel(current[0], current[1]);
            return data;
        }, data -> {
            if (root == null || data == null) return;
            bind(data);
        });
    }

    private void bind(Data data) {
        TrendChartView chart = root.findViewById(R.id.trend_chart);
        if (chart != null) chart.setData(data.values, data.labels);

        text(R.id.tv_trend_period, data.periodLabel);

        double change = Stats.changePercent(data.current, data.previous);
        String direction = change >= 0
                ? "t\u0103ng " + Money.percent(Math.abs(change))
                : "gi\u1ea3m " + Money.percent(Math.abs(change));
        text(R.id.tv_trend_summary, "K\u1ef3 n\u00e0y " + Money.vnd(data.current)
                + ", " + direction + " so v\u1edbi k\u1ef3 tr\u01b0\u1edbc.");

        for (int i = 0; i < AN_NAME.length; i++) {
            if (i < data.currentCats.size()) {
                CategoryTotal c = data.currentCats.get(i);
                String name = c.category == null || c.category.isEmpty() ? "Kh\u00e1c" : c.category;
                double last = find(data.previousCats, c.category);
                text(AN_NAME[i], name);
                text(AN_AMT[i], Money.vnd(c.total));
                text(AN_THIS[i], Money.vnd(c.total));
                text(AN_LAST[i], Money.vnd(last));
            } else {
                text(AN_NAME[i], "\u2014");
                text(AN_AMT[i], Money.vnd(0));
                text(AN_THIS[i], Money.vnd(0));
                text(AN_LAST[i], Money.vnd(0));
            }
        }

        if (!data.currentCats.isEmpty()) {
            CategoryTotal top = data.currentCats.get(0);
            CategoryTotal low = data.currentCats.get(data.currentCats.size() - 1);
            text(R.id.tv_best_category, low.category == null ? "\u2014" : low.category);
            text(R.id.tv_best_note, "Chi \u00edt nh\u1ea5t k\u1ef3 n\u00e0y: " + Money.vnd(low.total));
            text(R.id.tv_worst_category, top.category == null ? "\u2014" : top.category);
            text(R.id.tv_worst_note, "Chi nhi\u1ec1u nh\u1ea5t k\u1ef3 n\u00e0y: " + Money.vnd(top.total));
        } else {
            text(R.id.tv_best_category, "\u2014");
            text(R.id.tv_best_note, "Ch\u01b0a c\u00f3 d\u1eef li\u1ec7u");
            text(R.id.tv_worst_category, "\u2014");
            text(R.id.tv_worst_note, "Ch\u01b0a c\u00f3 d\u1eef li\u1ec7u");
        }
    }

    private double find(List<CategoryTotal> list, String category) {
        if (list == null || category == null) return 0d;
        for (CategoryTotal c : list) {
            if (category.equals(c.category)) return c.total;
        }
        return 0d;
    }

    private void text(int id, String value) {
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
