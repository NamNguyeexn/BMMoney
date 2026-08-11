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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bmmoney.MainActivity;
import com.example.bmmoney.R;
import com.example.bmmoney.adapter.TransactionAdapter;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;
import com.example.bmmoney.view.DonutChartView;
import com.example.bmmoney.util.HelpTip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M\u00e0n Trang ch\u1ee7. M\u1ecdi s\u1ed1 li\u1ec7u t\u00ednh theo chu k\u1ef3 t\u00f9y ch\u1ec9nh (ng\u00e0y ch\u1ed1t dd/mm).
 * Hi\u1ec7u \u1ee9ng thanh ti\u1ebfn tr\u00ecnh v\u00e0 bi\u1ec3u \u0111\u1ed3 tr\u00f2n ch\u1ec9 ch\u1ea1y m\u1ed9t l\u1ea7n cho m\u1ed7i l\u1ea7n m\u1edf m\u00e0n
 * ho\u1eb7c khi ng\u01b0\u1eddi d\u00f9ng ch\u1ee7 \u0111\u1ed9ng k\u00e9o \u0111\u1ec3 t\u1ea3i l\u1ea1i.
 */
public class DashboardFragment extends Fragment {

    private static final int[] CAT_NAME = {R.id.cat_name_0, R.id.cat_name_1, R.id.cat_name_2, R.id.cat_name_3, R.id.cat_name_4};
    private static final int[] CAT_PCT = {R.id.cat_pct_0, R.id.cat_pct_1, R.id.cat_pct_2, R.id.cat_pct_3, R.id.cat_pct_4};
    private static final int[] CAT_AMT = {R.id.cat_amt_0, R.id.cat_amt_1, R.id.cat_amt_2, R.id.cat_amt_3, R.id.cat_amt_4};
    private static final int[] CAT_BAR = {R.id.cat_bar_0, R.id.cat_bar_1, R.id.cat_bar_2, R.id.cat_bar_3, R.id.cat_bar_4};

    /** Moc thoi gian de cac hieu ung chay lan luot tu tren xuong duoi. */
    private static final long DELAY_BUDGET = 120;
    private static final long DUR_BUDGET = 650;
    private static final long DELAY_DONUT = 900;
    private static final long DUR_DONUT = 1800;
    private static final long DELAY_CATS = 2500;
    private static final long STEP_CATS = 200;
    private static final long DUR_CATS = 600;

    private View root;
    private TransactionAdapter adapter;
    private SwipeRefreshLayout refresh;

    /** True khi hi\u1ec7u \u1ee9ng \u0111\u00e3 ch\u1ea1y r\u1ed3i, tr\u00e1nh vi\u1ec7c thanh % nh\u1ea3y nhi\u1ec1u l\u1ea7n. */
    private boolean animated = false;
    private boolean animateNext = true;

    private static class Data {
        double expense;
        double previous;
        List<CategoryTotal> categories = new ArrayList<>();
        List<TxRow> recent = new ArrayList<>();

        // Ban va 03/08: so lieu ke toan.
        // wallet   = so du vi thuc te (co tinh ca vay muon)
        // receivable / payable = con phai thu / con phai tra
        // netWorth = wallet + receivable - payable
        // netProfit = thu nhap - chi tieu cua ky (khong tinh vay muon)
        double wallet;
        double receivable;
        double payable;
        double netWorth;
        double netProfit;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        RecyclerView recycler = root.findViewById(R.id.recycler_recent);
        adapter = new TransactionAdapter();
        adapter.setOnDelete(t -> reloadQuiet());
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        // KHONG dat setHasFixedSize(true): danh sach cao wrap_content nam trong
        // ScrollView. Co dinh kich thuoc khien RecyclerView bo qua requestLayout khi
        // adapter doi du lieu, nen no giu nguyen chieu cao 0 do duoc luc danh sach con
        // rong, va giao dich moi khong bao gio hien ra.
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(false);
        recycler.setAdapter(adapter);

        refresh = Refresh.setup(root, R.id.refresh_dashboard, this::reloadByUser);

        // Ban va 11/08: bo dau hoi canh dong "Con lai". Y nghia cua no da ro ngay tren
        // the (Ngan sach - Da dung - Con lai), them mot o chu thich chi lam roi mat.
        HelpTip.attach(root, R.id.help_vs_last_month,
                "So s\u00e1nh t\u1ed5ng chi ti\u00eau k\u1ef3 n\u00e0y v\u1edbi k\u1ef3 li\u1ec1n tr\u01b0\u1edbc. "
                        + "S\u1ed1 d\u01b0\u01a1ng l\u00e0 ti\u00eau nhi\u1ec1u h\u01a1n, s\u1ed1 \u00e2m l\u00e0 ti\u00eau \u00edt h\u01a1n.");

        ViewUtils.onClick(root, R.id.btn_header_add, v -> open(MainActivity.TAB_ADD));
        ViewUtils.onClick(root, R.id.btn_view_all, v -> open(MainActivity.TAB_SEARCH));

        // Ban va 04/08: mo hop thoai can bang so du
        View balance = root.findViewById(R.id.btn_balance);
        if (balance != null) {
            balance.setOnClickListener(v -> BalanceDialog.show(getContext(), this::afterBalance));
        }

        View daysLeft = root.findViewById(R.id.tv_days_left);
        if (daysLeft != null) {
            daysLeft.setOnClickListener(v -> CycleDialog.show(getContext(), this::reloadByUser));
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
        refresh = null;
        root = null;
        super.onDestroyView();
    }

    private void open(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(tab);
        }
    }

    /**
     * Sau khi can bang: bao cho nguoi dung biet chinh xac app da lam gi, roi nap lai
     * so lieu. Chenh lech 0 nghia la so trong app von da khop tien that.
     */
    private void afterBalance(double difference) {
        if (root == null) return;
        if (Math.abs(difference) < 1d) {
            Notice.info(root, "S\u1ed1 d\u01b0 \u0111\u00e3 kh\u1edbp, kh\u00f4ng c\u1ea7n c\u00e2n b\u1eb1ng");
        } else if (difference > 0) {
            Notice.success(root, "\u0110\u00e3 th\u00eam kho\u1ea3n thu c\u00e2n b\u1eb1ng " + Money.vnd(difference));
        } else {
            Notice.success(root, "\u0110\u00e3 th\u00eam kho\u1ea3n chi c\u00e2n b\u1eb1ng " + Money.vnd(-difference));
        }
        reloadByUser();
    }

    /** K\u00e9o \u0111\u1ec3 t\u1ea3i l\u1ea1i: cho ph\u00e9p ch\u1ea1y l\u1ea1i hi\u1ec7u \u1ee9ng m\u1ed9t l\u1ea7n. */
    public void reloadByUser() {
        animated = false;
        animateNext = true;
        reload();
    }

    /** N\u1ea1p l\u1ea1i s\u1ed1 li\u1ec7u nh\u01b0ng kh\u00f4ng ch\u1ea1y l\u1ea1i hi\u1ec7u \u1ee9ng (d\u00f9ng sau khi \u0111\u1ed3ng b\u1ed9 cloud). */
    public void reloadQuiet() {
        animateNext = false;
        reload();
    }

    public void reload() {
        if (root == null || getContext() == null) return;

        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final long[] current = Cycle.bounds(cycleDay, now, 0);
        final long[] previous = Cycle.bounds(cycleDay, now, -1);
        final double budget = Prefs.budget(getContext());
        final int warnPercent = Prefs.warnPercent(getContext());
        final int daysLeft = Cycle.daysLeft(cycleDay, now);
        final String range = Cycle.rangeLabel(current[0], current[1]);
        final TransactionDao dao = AppDatabase.dao(getContext());

        text(R.id.tv_greeting, "Xin ch\u00e0o, " + Prefs.userName(getContext()) + "! \ud83d\udc4b");
        text(R.id.tv_days_left, "C\u00f2n " + daysLeft + " ng\u00e0y");
        text(R.id.tv_days_left_stat, daysLeft + " ng\u00e0y");
        text(R.id.tv_period_range, range);

        Db.load(() -> {
            Data data = new Data();
            data.expense = value(dao.getExpenseInRangeSkip(current[0], current[1], Stats.CATEGORY_BALANCE));
            data.previous = value(dao.getExpenseInRangeSkip(previous[0], previous[1], Stats.CATEGORY_BALANCE));
            List<CategoryTotal> cats = dao.getExpenseByCategoryInRangeSkip(current[0], current[1], Stats.CATEGORY_BALANCE);
            if (cats != null) data.categories = cats;
            List<TxRow> recent = dao.getRecent(5);
            if (recent != null) data.recent = recent;

            // Ban va 03/08: bon loai cong no CO lam doi so du vi nhung KHONG
            // tinh vao thu chi, nen phai hoi database bang truy van rieng.
            data.wallet = dao.walletBalance();
            data.receivable = dao.totalReceivable();
            data.payable = dao.totalPayable();
            data.netWorth = data.wallet + data.receivable - data.payable;
            data.netProfit = dao.netProfitInRangeSkip(current[0], current[1], Stats.CATEGORY_BALANCE);
            return data;
        }, data -> {
            if (refresh != null) refresh.setRefreshing(false);
            if (root == null || data == null) return;
            bind(data, budget, warnPercent);
        });
    }

    private void bind(Data data, double budget, int warnPercent) {
        final boolean animate = animateNext && !animated;
        animated = true;
        animateNext = true;

        double expense = data.expense;
        double remaining = budget - expense;
        double usedPercent = budget > 0 ? expense / budget * 100d : 0d;

        text(R.id.tv_total_expense, Money.vnd(expense));
        text(R.id.tv_budget_line, "Ng\u00e2n s\u00e1ch: " + Money.vnd(budget));
        text(R.id.tv_remaining, "C\u00f2n l\u1ea1i: " + Money.vnd(remaining));
        text(R.id.tv_used_percent, Money.percent(usedPercent) + " \u0111\u00e3 d\u00f9ng");
        text(R.id.tv_vs_last_month, Money.signedPercent(Stats.changePercent(expense, data.previous)));

        View warning = root.findViewById(R.id.tv_over_warning);
        if (warning != null) {
            boolean over = usedPercent >= warnPercent;
            warning.setVisibility(over ? View.VISIBLE : View.GONE);
            if (over) {
                text(R.id.tv_over_warning, "B\u1ea1n \u0111ang v\u01b0\u1ee3t m\u1ee9c chi ti\u00eau k\u1ef3 n\u00e0y ("
                        + Money.percent(usedPercent) + " / ng\u01b0\u1ee1ng " + warnPercent + "%)");
            }
        }

        bar(R.id.budget_bar, (float) Math.min(100d, usedPercent), animate, DELAY_BUDGET, DUR_BUDGET);

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
                bar(CAT_BAR[i], (float) pct, animate, DELAY_CATS + STEP_CATS * i, DUR_CATS);
            } else {
                text(CAT_NAME[i], "\u2014");
                text(CAT_PCT[i], "0%");
                text(CAT_AMT[i], Money.vnd(0));
                bar(CAT_BAR[i], 0f, false, 0, 0);
            }
        }

        DonutChartView donut = root.findViewById(R.id.donut_chart);
        double change = Stats.changePercent(expense, data.previous);
        String arrow = change >= 0 ? "\u2191" : "\u2193";
        donut.setData(slices.isEmpty() ? null : percents, Money.shortVnd(expense),
                arrow + " " + String.format(Locale.US, "%.1f", Math.abs(change)) + "% so v\u1edbi k\u1ef3 tr\u01b0\u1edbc");
        if (animate) donut.animateSweep(DELAY_DONUT, DUR_DONUT);

        // Ban va 03/08: the tai san rong + hai o cong no.
        // Cac so nay KHONG tham gia vao ngan sach hay % da dung ben tren.
        text(R.id.tv_net_worth, Money.vnd(data.netWorth));
        text(R.id.tv_wallet_balance, Money.vnd(data.wallet));
        text(R.id.tv_net_worth_formula,
                "V\u00ed " + Money.shortVnd(data.wallet)
                        + " + Ph\u1ea3i thu " + Money.shortVnd(data.receivable)
                        + " \u2212 Ph\u1ea3i tr\u1ea3 " + Money.shortVnd(data.payable));
        text(R.id.tv_net_profit, (data.netProfit >= 0 ? "+" : "\u2212")
                + Money.vnd(Math.abs(data.netProfit)));

        TextView netView = root.findViewById(R.id.tv_net_worth);
        if (netView != null) {
            netView.setTextColor(androidx.core.content.ContextCompat.getColor(netView.getContext(),
                    data.netWorth >= 0 ? R.color.net_positive : R.color.net_negative));
        }

        text(R.id.tv_lend_total, Money.vnd(data.receivable));
        text(R.id.tv_debt_total, Money.vnd(data.payable));
        text(R.id.tv_lend_open, "C\u00f2n ph\u1ea3i thu");
        text(R.id.tv_debt_open, "C\u00f2n ph\u1ea3i tr\u1ea3");

        HelpTip.attach(root, R.id.help_net_worth,
                "Gi\u00e1 tr\u1ecb r\u00f2ng = ti\u1ec1n trong v\u00ed + ph\u1ea3i thu \u2212 ph\u1ea3i tr\u1ea3.\n\n"
                        + "V\u00ed: " + Money.vnd(data.wallet) + "\n"
                        + "Ph\u1ea3i thu: " + Money.vnd(data.receivable) + "\n"
                        + "Ph\u1ea3i tr\u1ea3: " + Money.vnd(data.payable));

        adapter.setTransactions(data.recent);
        ViewUtils.setVisibility(root, R.id.tv_empty_recent, data.recent.isEmpty() ? View.VISIBLE : View.GONE);
        ViewUtils.setVisibility(root, R.id.recycler_recent, data.recent.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void bar(int id, float percent, boolean animate, long delay, long duration) {
        View view = root.findViewById(id);
        if (view == null) return;
        if (animate) {
            ViewUtils.animateBar(view, percent, duration, delay);
        } else {
            ViewUtils.setBar(view, percent);
        }
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
