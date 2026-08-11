package com.example.bmmoney.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bmmoney.R;
import com.example.bmmoney.adapter.TransactionAdapter;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ban va 02/08 - man Lich.
 *
 * <p>Y do: nhin mot thang la biet ngay nao co bien dong tai chinh dang luu y.
 * Moi ngay co mot cham nho nam ngay duoi so ngay duong lich:</p>
 *
 * <ul>
 *   <li>cham dam: tong bien dong trong ngay vuot moc dang chu y;</li>
 *   <li>cham nhat hon: dat tu 50% moc tro len;</li>
 *   <li>cham rat nhat: ngay do co giao dich nhung chua dang ke;</li>
 *   <li>khong co cham: ngay do khong phat sinh ban ghi nao.</li>
 * </ul>
 *
 * <p>Moc lay tu "mocdang chu y" trong Cai dat (Prefs.bigPercent) nhan voi tong
 * bien dong ca thang, dung dung cong thuc ma man Tim kiem dang dung nen hai man
 * hinh luon hieu "dang chu y" giong nhau. Bien dong tinh gop ca bon loai
 * thu / chi / cho vay / dang no theo dung gia tri tuyet doi, vi o day dang do
 * do lon cua bien dong chu khong phai so du vi.</p>
 *
 * <p>Bam mot ngay se liet ke ban ghi cua ngay do tu dau ngay den cuoi ngay;
 * bam mot ban ghi se mo dung popup cua man Tim kiem (TxDialog).</p>
 */
public class CalendarFragment extends Fragment {

    private static final String[] MONTH_KEYS = new String[0]; // giu cho tuong lai

    private View root;
    private SwipeRefreshLayout refresh;
    private TransactionAdapter adapter;

    /** Thang dang xem, luon dat ve ngay 1 luc 00:00. */
    private final Calendar month = Calendar.getInstance();
    /** Ngay dang chon trong thang, tinh theo dau ngay. */
    private long selectedDay;

    private final SimpleDateFormat dayFormat =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    /** Ket qua nap mot thang: tong tuyet doi tung ngay + ban ghi cua ngay dang chon. */
    private static class Data {
        double[] byDay = new double[32];
        double monthTotal;
        double threshold;
        List<TxRow> dayItems = new ArrayList<>();
        double dayExpense;
        double dayIncome;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_calendar, container, false);

        startOfDay(month);
        month.set(Calendar.DAY_OF_MONTH, 1);
        selectedDay = startOfToday();

        RecyclerView recycler = root.findViewById(R.id.recycler_day);
        adapter = new TransactionAdapter();
        adapter.setShowTimeOnly(true);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(false);
        recycler.setAdapter(adapter);

        refresh = Refresh.setup(root, R.id.refresh_calendar, this::reload);

        ViewUtils.onClick(root, R.id.btn_prev_month, v -> shiftMonth(-1));
        ViewUtils.onClick(root, R.id.btn_next_month, v -> shiftMonth(1));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        RecyclerView recycler = root == null ? null : (RecyclerView) root.findViewById(R.id.recycler_day);
        if (recycler != null) recycler.setAdapter(null);
        adapter = null;
        refresh = null;
        root = null;
        super.onDestroyView();
    }

    /** Chuyen thang. Ngay dang chon nhay ve mung 1 cho khoi lech thang. */
    private void shiftMonth(int delta) {
        month.add(Calendar.MONTH, delta);
        Calendar first = (Calendar) month.clone();
        selectedDay = first.getTimeInMillis();
        reload();
    }

    public void reload() {
        if (root == null || getContext() == null) return;

        final long monthStart = month.getTimeInMillis();
        Calendar end = (Calendar) month.clone();
        end.add(Calendar.MONTH, 1);
        final long monthEnd = end.getTimeInMillis() - 1;

        final int daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        final int bigPercent = Prefs.bigPercent(getContext());
        final long dayStart = selectedDay;
        final long dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1;

        final TransactionDao dao = AppDatabase.dao(getContext());

        Db.load(() -> {
            Data data = new Data();

            List<TxRow> all = dao.getRangeAscending(monthStart, monthEnd);
            if (all != null) {
                Calendar cal = Calendar.getInstance();
                for (TxRow t : all) {
                    cal.setTimeInMillis(t.getDate());
                    int day = cal.get(Calendar.DAY_OF_MONTH);
                    if (day < 1 || day > 31) continue;
                    // Do lon bien dong: khong phan biet loai, khong bu tru nhau
                    double value = Math.abs(t.getAmount());
                    data.byDay[day] += value;
                    data.monthTotal += value;
                }
            }
            data.threshold = data.monthTotal * bigPercent / 100d;

            List<TxRow> items = dao.getRangeAscending(dayStart, dayEnd);
            if (items != null) {
                data.dayItems = items;
                for (TxRow t : items) {
                    if (Stats.EXPENSE.equals(t.getType())) data.dayExpense += t.getAmount();
                    else if (Stats.INCOME.equals(t.getType())) data.dayIncome += t.getAmount();
                }
            }
            return data;
        }, data -> {
            if (refresh != null) refresh.setRefreshing(false);
            if (root == null || data == null || getContext() == null) return;

            text(R.id.tv_month_title, "Th\u00e1ng " + (month.get(Calendar.MONTH) + 1)
                    + "/" + month.get(Calendar.YEAR));
            text(R.id.tv_month_total, data.monthTotal > 0
                    ? "Bi\u1ebfn \u0111\u1ed9ng c\u1ea3 th\u00e1ng " + Money.vnd(data.monthTotal)
                            + " \u00b7 m\u1ed1c \u0111\u00e1ng ch\u00fa \u00fd " + Money.vnd(data.threshold)
                    : "Th\u00e1ng n\u00e0y ch\u01b0a c\u00f3 b\u1ea3n ghi n\u00e0o");

            buildGrid(data, daysInMonth);
            bindDay(data);
        });
    }

    /** Do luoi ngay: sau hang bay o, tuan bat dau tu thu Hai. */
    private void buildGrid(Data data, int daysInMonth) {
        LinearLayout weeks = root.findViewById(R.id.container_weeks);
        if (weeks == null) return;
        weeks.removeAllViews();

        Calendar first = (Calendar) month.clone();
        // Calendar.MONDAY = 2 nen quy ve 0..6 voi thu Hai la 0
        int lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;

        LayoutInflater inflater = LayoutInflater.from(weeks.getContext());
        long today = startOfToday();

        int cell = 0;
        int total = lead + daysInMonth;
        int rows = (total + 6) / 7;

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(weeks.getContext());
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int c = 0; c < 7; c++, cell++) {
                View view = inflater.inflate(R.layout.item_calendar_day, row, false);
                TextView label = view.findViewById(R.id.tv_day);
                View box = view.findViewById(R.id.day_box);
                View dot = view.findViewById(R.id.day_dot);

                int day = cell - lead + 1;
                if (day < 1 || day > daysInMonth) {
                    // O dem cua thang truoc / thang sau
                    label.setText("");
                    box.setBackground(null);
                    dot.setVisibility(View.INVISIBLE);
                    view.setClickable(false);
                    row.addView(view);
                    continue;
                }

                Calendar c1 = (Calendar) month.clone();
                c1.set(Calendar.DAY_OF_MONTH, day);
                final long dayMillis = c1.getTimeInMillis();

                label.setText(String.valueOf(day));

                boolean picked = dayMillis == selectedDay;
                boolean isToday = dayMillis == today;
                if (picked) {
                    box.setBackgroundResource(R.drawable.bg_day_selected);
                    label.setTextColor(ContextCompat.getColor(view.getContext(), R.color.cream));
                } else if (isToday) {
                    box.setBackgroundResource(R.drawable.bg_day_today);
                    label.setTextColor(ContextCompat.getColor(view.getContext(), R.color.dark_green));
                } else {
                    box.setBackground(null);
                    label.setTextColor(ContextCompat.getColor(view.getContext(), R.color.dark_green));
                }

                double value = data.byDay[day];
                if (value <= 0) {
                    dot.setVisibility(View.INVISIBLE);
                } else {
                    dot.setVisibility(View.VISIBLE);
                    if (data.threshold > 0 && value >= data.threshold) {
                        dot.setBackgroundResource(R.drawable.dot_cal_strong);
                    } else if (data.threshold > 0 && value >= data.threshold / 2) {
                        dot.setBackgroundResource(R.drawable.dot_cal_medium);
                    } else {
                        dot.setBackgroundResource(R.drawable.dot_cal_light);
                    }
                }

                view.setOnClickListener(v -> {
                    selectedDay = dayMillis;
                    reload();
                });
                row.addView(view);
            }
            weeks.addView(row);
        }
    }

    /** Danh sach ban ghi cua ngay dang chon, xep tu dau ngay den cuoi ngay. */
    private void bindDay(Data data) {
        text(R.id.tv_day_title, "Ng\u00e0y " + dayFormat.format(new Date(selectedDay)));
        text(R.id.tv_day_count, data.dayItems.size() + " b\u1ea3n ghi");

        StringBuilder sum = new StringBuilder();
        if (data.dayExpense > 0) sum.append("Chi ").append(Money.vnd(data.dayExpense));
        if (data.dayIncome > 0) {
            if (sum.length() > 0) sum.append(" \u00b7 ");
            sum.append("Thu ").append(Money.vnd(data.dayIncome));
        }
        text(R.id.tv_day_summary, sum.length() > 0 ? sum.toString()
                : "S\u1eafp x\u1ebfp t\u1eeb \u0111\u1ea7u ng\u00e0y \u0111\u1ebfn cu\u1ed1i ng\u00e0y");

        if (adapter != null) adapter.setTransactions(data.dayItems);

        boolean empty = data.dayItems.isEmpty();
        ViewUtils.setVisibility(root, R.id.tv_empty_day, empty ? View.VISIBLE : View.GONE);
        ViewUtils.setVisibility(root, R.id.recycler_day, empty ? View.GONE : View.VISIBLE);
    }

    private static long startOfToday() {
        Calendar cal = Calendar.getInstance();
        startOfDay(cal);
        return cal.getTimeInMillis();
    }

    private static void startOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
