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
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.TrendChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M\u00e0n Ph\u00e2n t\u00edch: xu h\u01b0\u1edbng chi ti\u00eau theo t\u1eebng k\u1ef3 (L1, L2, L3...),
 * so s\u00e1nh danh m\u1ee5c k\u1ef3 n\u00e0y v\u1edbi k\u1ef3 tr\u01b0\u1edbc v\u00e0 \u0111i\u1ec3m s\u00e1ng / \u0111i\u1ec3m c\u1ea7n l\u01b0u \u00fd.
 */
public class AnalyticsFragment extends Fragment {

    /**
     * Ban va 03/08: so danh muc hien thi toi da.
     *
     * <p>Truoc day con so 4 bi chot cung o BA cho: bon mang id ben duoi, vong lap
     * cat du lieu trong reload() va bon khoi XML trong fragment_analytics.xml. Nay
     * chi con mot hang so duy nhat, cac dong duoc bom tu item_cat_compare.xml.</p>
     */
    private static final int MAX_CATEGORY_ROWS = 6;

    private View root;
    private SwipeRefreshLayout refresh;

    /** false = xem theo k\u1ef3 (6 k\u1ef3 g\u1ea7n nh\u1ea5t), true = xem theo n\u0103m (12 k\u1ef3). */
    private boolean yearMode = false;
    private boolean animated = false;

    /** S\u1ed1 li\u1ec7u m\u1ed9t l\u1ea7n n\u1ea1p, t\u00ednh s\u1eb5n tr\u00ean lu\u1ed3ng n\u1ec1n. */
    private static class Data {
        double[] trend;
        String[] labels;

        // Ban va 03/08: ba duong bo sung cho bieu do xu huong.
        // trendLend  = tien cho vay ra trong ky (LEND)
        // trendDebt  = tien di vay ve trong ky (BORROW)
        double[] trendIncome;
        double[] trendLend;
        double[] trendDebt;

        // So lieu cua ky hien tai va ky truoc, dung cho the "Danh gia"
        double income;
        double previousIncome;
        double lend;
        double previousLend;
        double debt;
        double previousDebt;

        // Ban va 03/08: so lieu cong no theo nghiep vu ke toan
        double receivable;
        double payable;
        double netProfit;
        List<com.example.bmmoney.data.PartnerBalance> partners = new ArrayList<>();

        /** Danh sach khoan cho vay va khoan no con treo, han gan nhat truoc. */
        List<TxRow> debts = new ArrayList<>();
        double total;
        double previousTotal;
        List<String> names = new ArrayList<>();
        List<Double> thisAmounts = new ArrayList<>();
        List<Double> lastAmounts = new ArrayList<>();
        boolean hasPrevious = false;
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

        ViewUtils.onClick(root, R.id.btn_period_month, v -> setMode(false));
        ViewUtils.onClick(root, R.id.btn_period_year, v -> setMode(true));
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
            data.trendIncome = new double[steps];
            data.trendLend = new double[steps];
            data.trendDebt = new double[steps];
            data.labels = new String[steps];

            for (int i = 0; i < steps; i++) {
                int offset = -(steps - 1 - i);
                long[] bounds = Cycle.bounds(cycleDay, now, offset);
                Double sum = dao.getExpenseInRangeSkip(bounds[0], bounds[1], Stats.CATEGORY_BALANCE);
                data.trend[i] = sum == null ? 0d : sum;
                // Ba duong con lai. Cho vay va tra no lay rieng vi khong nam trong thu chi.
                data.trendIncome[i] = zero(dao.getIncomeInRangeSkip(bounds[0], bounds[1], Stats.CATEGORY_BALANCE));
                data.trendLend[i] = zero(dao.getSumInRange(Stats.LEND, bounds[0], bounds[1]));
                data.trendDebt[i] = zero(dao.getSumInRange(Stats.BORROW, bounds[0], bounds[1]));
                data.labels[i] = Cycle.label(cycleDay, bounds[0] + 1000L);
            }
            data.total = data.trend[steps - 1];
            data.previousTotal = steps > 1 ? data.trend[steps - 2] : 0d;
            data.income = data.trendIncome[steps - 1];
            data.previousIncome = steps > 1 ? data.trendIncome[steps - 2] : 0d;
            data.lend = data.trendLend[steps - 1];
            data.previousLend = steps > 1 ? data.trendLend[steps - 2] : 0d;
            data.debt = data.trendDebt[steps - 1];
            data.previousDebt = steps > 1 ? data.trendDebt[steps - 2] : 0d;
            // Ban va 03/08: so du cong no lay tu truy van gop, da tru phan da tra
            data.receivable = dao.totalReceivable();
            data.payable = dao.totalPayable();
            data.netProfit = dao.netProfitInRangeSkip(current[0], current[1], Stats.CATEGORY_BALANCE);
            List<com.example.bmmoney.data.PartnerBalance> partners = dao.partnerBalances();
            if (partners != null) data.partners = partners;

            // Bao cao no: gop khoan vay goc con treo cua hai chieu, han gan nhat truoc
            List<TxRow> open = new ArrayList<>();
            List<TxRow> openLend = dao.getOpenLoans(Stats.LEND);
            List<TxRow> openDebt = dao.getOpenLoans(Stats.BORROW);
            if (openLend != null) open.addAll(openLend);
            if (openDebt != null) open.addAll(openDebt);
            java.util.Collections.sort(open, (a, b) -> {
                long da = a.dueMillis() > 0 ? a.dueMillis() : Long.MAX_VALUE;
                long db = b.dueMillis() > 0 ? b.dueMillis() : Long.MAX_VALUE;
                return Long.compare(da, db);
            });
            data.debts = open;

            long[] thisBounds = Cycle.bounds(cycleDay, now, 0);
            long[] lastBounds = Cycle.bounds(cycleDay, now, -1);
            Map<String, Double> thisMap = toMap(dao.getExpenseByCategoryInRangeSkip(thisBounds[0], thisBounds[1], Stats.CATEGORY_BALANCE));
            Map<String, Double> lastMap = toMap(dao.getExpenseByCategoryInRangeSkip(lastBounds[0], lastBounds[1], Stats.CATEGORY_BALANCE));

            int count = 0;
            for (Map.Entry<String, Double> entry : thisMap.entrySet()) {
                if (count >= MAX_CATEGORY_ROWS) break;
                double thisValue = entry.getValue();
                Double lastValue = lastMap.get(entry.getKey());
                double last = lastValue == null ? 0d : lastValue;
                data.names.add(entry.getKey());
                data.thisAmounts.add(thisValue);
                data.lastAmounts.add(last);
                count++;
            }

            // \u0110i\u1ec3m s\u00e1ng / \u0111i\u1ec3m c\u1ea7n l\u01b0u \u00fd ch\u1ec9 c\u00f3 \u00fd ngh\u0129a khi \u0111\u00e3 c\u00f3 s\u1ed1 li\u1ec7u k\u1ef3 tr\u01b0\u1edbc
            data.hasPrevious = !lastMap.isEmpty();
            double bestChange = Double.MAX_VALUE;
            double worstChange = -Double.MAX_VALUE;
            for (String key : data.hasPrevious ? union(thisMap, lastMap) : new ArrayList<String>()) {
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
        if (chart != null) {
            // Thu tu bat buoc: chi / thu / cho vay / tra no, trung voi mau o phan chu thich
            chart.setSeries(new double[][]{
                    data.trend, data.trendIncome, data.trendLend, data.trendDebt}, data.labels);
        }

        bindEvaluation(data);
        bindDebtReport(data);

        bindCategories(data, animate);

        if (!data.hasPrevious) {
            // K\u1ef3 \u0111\u1ea7u ti\u00ean: ch\u01b0a c\u00f3 g\u00ec \u0111\u1ec3 so s\u00e1nh n\u00ean \u0111\u1ec3 tr\u1ed1ng
            text(R.id.tv_best_category, "\u2014");
            text(R.id.tv_best_note, "Ch\u01b0a c\u00f3 s\u1ed1 li\u1ec7u k\u1ef3 tr\u01b0\u1edbc \u0111\u1ec3 so s\u00e1nh");
            text(R.id.tv_worst_category, "\u2014");
            text(R.id.tv_worst_note, "Ghi \u0111\u1ee7 m\u1ed9t k\u1ef3 n\u1eefa l\u00e0 xem \u0111\u01b0\u1ee3c so s\u00e1nh");
            return;
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

    /**
     * The "Phan tich theo danh muc" - ve lai ngay 03/08.
     *
     * <p><b>Truoc:</b> bon o co dinh. Khong co danh muc thu tu thi van tro ra mot dong
     * "— / 0 ₫" trong tron; ky dau tien chua ghi gi thi ca bon o deu trong nhu vay.
     * Nguoi dung cung khong biet mot danh muc chiem bao nhieu phan tram, hay tang giam
     * bao nhieu so voi ky truoc - phai tu nhin do dai hai thanh ma doan.</p>
     *
     * <p><b>Nay:</b> co bao nhieu danh muc thi ve bay nhieu dong (toi da
     * {@link #MAX_CATEGORY_ROWS}), khong con o trong. Moi dong noi ro ba dieu: chiem bao
     * nhieu phan tram tong chi, tang hay giam bao nhieu phan tram, va ky truoc la bao nhieu.
     * Chua co so lieu thi hien mot dong nhac thay vi cac o rong.</p>
     */
    private void bindCategories(Data data, boolean animate) {
        ViewGroup container = root.findViewById(R.id.container_categories_analytics);
        View empty = root.findViewById(R.id.tv_no_category_data);
        if (container == null) return;

        container.removeAllViews();

        final int rows = Math.min(data.names.size(), MAX_CATEGORY_ROWS);
        if (rows == 0) {
            if (empty != null) empty.setVisibility(View.VISIBLE);
            text(R.id.tv_cat_total, "");
            return;
        }
        if (empty != null) empty.setVisibility(View.GONE);
        text(R.id.tv_cat_total, rows + " danh m\u1ee5c");

        // Hai thanh dung chung mot moc de so sanh duoc ky nay voi ky truoc
        double max = 0;
        for (int i = 0; i < rows; i++) {
            max = Math.max(max, Math.max(data.thisAmounts.get(i), data.lastAmounts.get(i)));
        }

        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int i = 0; i < rows; i++) {
            final double thisValue = data.thisAmounts.get(i);
            final double lastValue = data.lastAmounts.get(i);

            View row = inflater.inflate(R.layout.item_cat_compare, container, false);

            rowText(row, R.id.tv_cc_rank, String.valueOf(i + 1));
            rowText(row, R.id.tv_cc_name, data.names.get(i));
            rowText(row, R.id.tv_cc_amount, Money.vnd(thisValue));

            // Ti trong tren tong chi ca ky, giup biet danh muc nay co dang de y khong
            String share = data.total > 0
                    ? "  \u00b7  " + Money.percent(thisValue / data.total * 100d) + " t\u1ed5ng chi"
                    : "";
            rowText(row, R.id.tv_cc_last_amount,
                    "K\u1ef3 tr\u01b0\u1edbc " + Money.vnd(lastValue) + share);

            TextView delta = row.findViewById(R.id.tv_cc_delta);
            if (delta != null) {
                if (lastValue <= 0) {
                    // Ky truoc khong ghi danh muc nay, tinh phan tram se ra vo cung
                    delta.setText("M\u1edbi");
                    delta.setTextColor(ContextCompat.getColor(delta.getContext(), R.color.olive));
                } else {
                    double change = Stats.changePercent(thisValue, lastValue);
                    boolean up = change > 0;
                    delta.setText((up ? "\u2191 " : change < 0 ? "\u2193 " : "= ")
                            + Money.percent(Math.abs(change)));
                    // Chi tieu tang la dieu can luu y nen to nau dat, giam thi to xanh
                    delta.setTextColor(ContextCompat.getColor(delta.getContext(),
                            up ? R.color.burnt : R.color.net_positive));
                }
            }

            bar(row.findViewById(R.id.bar_cc_this),
                    max > 0 ? (float) (thisValue / max * 100d) : 0f, animate, 80L * i);
            bar(row.findViewById(R.id.bar_cc_last),
                    max > 0 ? (float) (lastValue / max * 100d) : 0f, animate, 80L * i + 40);

            container.addView(row);
        }
    }

    private void rowText(View row, int id, String value) {
        View view = row.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }

    /**
     * The "Danh gia": moi loai mot dong, dong nao khong co so lieu thi an di.
     */
    private void bindEvaluation(Data data) {
        text(R.id.tv_trend_summary, "K\u1ef3 n\u00e0y \u0111\u00e3 chi " + Money.vnd(data.total)
                + " \u00b7 " + Stats.changePhrase(data.total, data.previousTotal));

        line(R.id.tv_eval_income, data.income > 0,
                "K\u1ef3 n\u00e0y \u0111\u00e3 thu " + Money.vnd(data.income)
                        + " \u00b7 " + Stats.changePhrase(data.income, data.previousIncome));

        // Ban va 03/08: lai lo chi tinh thu nhap - chi tieu, vay/tra khong dinh vao
        line(R.id.tv_eval_profit, data.income > 0 || data.total > 0,
                (data.netProfit >= 0
                        ? "K\u1ef3 n\u00e0y l\u00e3i " : "K\u1ef3 n\u00e0y l\u1ed7 ")
                        + Money.vnd(Math.abs(data.netProfit))
                        + " \u00b7 thu " + Money.vnd(data.income)
                        + " tr\u1eeb chi " + Money.vnd(data.total));

        line(R.id.tv_eval_lend, data.lend > 0 || data.receivable > 0,
                "K\u1ef3 n\u00e0y cho vay " + Money.vnd(data.lend)
                        + " \u00b7 c\u00f2n ph\u1ea3i thu " + Money.vnd(data.receivable));

        line(R.id.tv_eval_debt, data.debt > 0 || data.payable > 0,
                "K\u1ef3 n\u00e0y \u0111i vay " + Money.vnd(data.debt)
                        + " \u00b7 c\u00f2n ph\u1ea3i tr\u1ea3 " + Money.vnd(data.payable));
    }

    /**
     * The "Bao cao no": biet can doi ai / tra ai truoc.
     * Khung cao co dinh khoang ba dong roi cuon, giong phan Danh muc tuy chinh.
     */
    private void bindDebtReport(Data data) {
        android.widget.LinearLayout container = root.findViewById(R.id.container_debts);
        if (container == null) return;
        container.removeAllViews();

        boolean empty = data.debts.isEmpty();
        View scroll = root.findViewById(R.id.scroll_debts);
        if (scroll != null) scroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        View none = root.findViewById(R.id.tv_no_debt);
        if (none != null) none.setVisibility(empty ? View.VISIBLE : View.GONE);
        View hint = root.findViewById(R.id.tv_debt_scroll_hint);
        if (hint != null) hint.setVisibility(data.debts.size() > 3 ? View.VISIBLE : View.GONE);
        text(R.id.tv_debt_count, data.debts.size() + " kho\u1ea3n");
        if (empty) return;

        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (final TxRow t : data.debts) {
            View row = inflater.inflate(R.layout.item_debt_row, container, false);
            String rowType = Stats.normalize(t.getType());
            boolean lend = Stats.isReceivable(rowType);

            TextView icon = row.findViewById(R.id.tv_debt_icon);
            icon.setText(Stats.typeGlyph(rowType));
            icon.setBackgroundResource(com.example.bmmoney.util.TypeStyle.bg(rowType));

            ((TextView) row.findViewById(R.id.tv_debt_person)).setText(
                    t.personOrEmpty().isEmpty() ? Stats.typeName(rowType) : t.personOrEmpty());

            String due = t.dueMillis() > 0
                    ? (lend ? "H\u1ea1n \u0111\u00f2i " : "H\u1ea1n tr\u1ea3 ")
                            + df.format(new Date(t.dueMillis())) + " \u00b7 " + TxDialog.remain(t.dueMillis())
                    : (lend ? "Ch\u01b0a \u0111\u1eb7t h\u1ea1n \u0111\u00f2i" : "Ch\u01b0a \u0111\u1eb7t h\u1ea1n tr\u1ea3");
            ((TextView) row.findViewById(R.id.tv_debt_due)).setText(due);

            TextView amount = row.findViewById(R.id.tv_debt_amount);
            amount.setText(Money.vnd(t.getAmount()));
            amount.setTextColor(ContextCompat.getColor(container.getContext(),
                    com.example.bmmoney.util.TypeStyle.color(rowType)));

            row.setOnClickListener(v -> TxDialog.show(v.getContext(), t, false, this::reload));
            container.addView(row);
        }
    }

    /** Hien mot dong danh gia neu co so lieu, con khong thi an han di. */
    private void line(int id, boolean visible, String value) {
        View view = root.findViewById(id);
        if (view == null) return;
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && view instanceof TextView) ((TextView) view).setText(value);
    }

    private static double zero(Double v) {
        return v == null ? 0d : v;
    }

    /** Thanh do la the View, chi dat do rong chu khong gan chu. */
    private void bar(int id, float percent, boolean animate, long delay) {
        bar(root == null ? null : root.findViewById(id), percent, animate, delay);
    }

    /** Ban va 03/08: nhan thang View vi cac dong danh muc nay duoc bom dong. */
    private void bar(View view, float percent, boolean animate, long delay) {
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
