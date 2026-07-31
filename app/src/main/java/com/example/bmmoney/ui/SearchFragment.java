package com.example.bmmoney.ui;

import android.os.Bundle;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * M\u00e0n T\u00ecm ki\u1ebfm v\u1edbi ba nh\u00f3m b\u1ed9 l\u1ecdc: th\u1eddi gian, danh m\u1ee5c v\u00e0 gi\u00e1 tr\u1ecb.
 * Th\u1ebb "Kho\u1ea3n chi \u0111\u00e1ng ch\u00fa \u00fd" l\u1ecdc c\u00e1c giao d\u1ecbch chi\u1ebfm t\u1eeb x% t\u1ed5ng chi c\u1ee7a k\u1ef3 (\u0111\u1eb7t trong C\u00e0i \u0111\u1eb7t).
 */
public class SearchFragment extends Fragment {

    private static final int TIME_WEEK = 0;
    private static final int TIME_MONTH = 1;
    private static final int TIME_YEAR = 2;

    private View root;
    private SwipeRefreshLayout refresh;
    private TransactionAdapter adapter;

    private int timeFilter = TIME_MONTH;
    private boolean onlyOver100k = false;
    private boolean onlyBig = false;
    private final Set<String> pickedCategories = new HashSet<>();
    private final List<TextView> categoryChips = new ArrayList<>();
    private String keyword = "";

    private static class Data {
        List<TransactionEntity> items = new ArrayList<>();
        double total;
        double periodTotal;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_search, container, false);

        RecyclerView recycler = root.findViewById(R.id.recycler_results);
        adapter = new TransactionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(false);
        adapter.setOnDelete(this::deleteTransaction);
        recycler.setAdapter(adapter);

        refresh = Refresh.setup(root, R.id.refresh_search, this::reload);

        EditText search = root.findViewById(R.id.edt_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s.toString().trim().toLowerCase(Locale.getDefault());
                reload();
            }
        });

        root.findViewById(R.id.chip_week).setOnClickListener(v -> setTime(TIME_WEEK));
        root.findViewById(R.id.chip_month).setOnClickListener(v -> setTime(TIME_MONTH));
        root.findViewById(R.id.chip_year).setOnClickListener(v -> setTime(TIME_YEAR));

        root.findViewById(R.id.chip_over100k).setOnClickListener(v -> {
            onlyOver100k = !onlyOver100k;
            styleChip((TextView) v, onlyOver100k);
            reload();
        });
        root.findViewById(R.id.chip_big).setOnClickListener(v -> {
            onlyBig = !onlyBig;
            styleChip((TextView) v, onlyBig);
            reload();
        });

        buildCategoryChips();
        styleTimeChips();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        buildCategoryChips();
        reload();
    }

    @Override
    public void onDestroyView() {
        RecyclerView recycler = root == null ? null : (RecyclerView) root.findViewById(R.id.recycler_results);
        if (recycler != null) recycler.setAdapter(null);
        categoryChips.clear();
        adapter = null;
        refresh = null;
        root = null;
        super.onDestroyView();
    }

    /** T\u1ea1o th\u1ebb l\u1ecdc cho \u0111\u00fang danh s\u00e1ch danh m\u1ee5c \u0111ang c\u00f3 trong C\u00e0i \u0111\u1eb7t. */
    private void buildCategoryChips() {
        if (root == null || getContext() == null) return;
        LinearLayout container = root.findViewById(R.id.container_cat_chips);
        if (container == null) return;

        container.removeAllViews();
        categoryChips.clear();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (Categories.Item item : Categories.all(getContext())) {
            final String name = item.name;
            View view = inflater.inflate(R.layout.item_chip, container, false);
            TextView chip = view.findViewById(R.id.tv_chip);
            chip.setText(item.emoji + " " + name);
            chip.setOnClickListener(v -> {
                if (pickedCategories.contains(name)) {
                    pickedCategories.remove(name);
                } else {
                    pickedCategories.add(name);
                }
                styleChip(chip, pickedCategories.contains(name));
                reload();
            });
            styleChip(chip, pickedCategories.contains(name));
            container.addView(view);
            categoryChips.add(chip);
        }
    }

    private void setTime(int filter) {
        timeFilter = filter;
        styleTimeChips();
        reload();
    }

    private void styleTimeChips() {
        if (root == null) return;
        styleChip(root.findViewById(R.id.chip_week), timeFilter == TIME_WEEK);
        styleChip(root.findViewById(R.id.chip_month), timeFilter == TIME_MONTH);
        styleChip(root.findViewById(R.id.chip_year), timeFilter == TIME_YEAR);
    }

    private void styleChip(TextView chip, boolean active) {
        if (chip == null || getContext() == null) return;
        chip.setBackgroundResource(active ? R.drawable.bg_pill_olive : R.drawable.bg_pill_cream);
        chip.setTextColor(ContextCompat.getColor(getContext(),
                active ? R.color.cream : R.color.dark_green));
    }

    /** Xoa mot ban ghi (da hoi xac nhan o adapter) roi nap lai ket qua. */
    private void deleteTransaction(final TransactionEntity item) {
        if (getContext() == null || item == null) return;
        final android.content.Context app = getContext().getApplicationContext();
        final TransactionDao dao = AppDatabase.dao(app);
        Db.io(() -> {
            dao.delete(item);
            // Hen sao luu sau vai phut de gom nhieu thay doi vao mot lan ghi cloud
            AutoBackup.scheduleSoon(app);
            Db.ui(() -> {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "\u0110\u00e3 x\u00f3a b\u1ea3n ghi chi ti\u00eau",
                        Toast.LENGTH_SHORT).show();
                reload();
            });
        });
    }

    public void reload() {
        if (root == null || getContext() == null) return;

        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final int bigPercent = Prefs.bigPercent(getContext());
        final String key = keyword;
        final int filter = timeFilter;
        final boolean over100k = onlyOver100k;
        final boolean big = onlyBig;
        final Set<String> cats = new HashSet<>(pickedCategories);

        final long from;
        final long to;
        if (filter == TIME_WEEK) {
            from = Cycle.startOfWeek(now);
            to = now;
        } else if (filter == TIME_YEAR) {
            from = Cycle.startOfYear(now);
            to = Cycle.endOfYear(now);
        } else {
            long[] bounds = Cycle.bounds(cycleDay, now, 0);
            from = bounds[0];
            to = bounds[1];
        }

        final com.example.bmmoney.data.TransactionDao dao = AppDatabase.dao(getContext());

        Db.load(() -> {
            Data data = new Data();
            List<TransactionEntity> all = dao.getTransactionsByDateRange(from, to);
            if (all == null) return data;

            double periodTotal = 0;
            for (TransactionEntity t : all) {
                if (Stats.EXPENSE.equals(t.getType())) periodTotal += t.getAmount();
            }
            data.periodTotal = periodTotal;
            double threshold = periodTotal * bigPercent / 100d;

            for (TransactionEntity t : all) {
                if (!key.isEmpty()) {
                    String title = t.getTitle() == null ? "" : t.getTitle().toLowerCase(Locale.getDefault());
                    String note = t.getNote() == null ? "" : t.getNote().toLowerCase(Locale.getDefault());
                    String category = t.getCategory() == null ? "" : t.getCategory().toLowerCase(Locale.getDefault());
                    if (!title.contains(key) && !note.contains(key) && !category.contains(key)) continue;
                }
                if (!cats.isEmpty() && !cats.contains(t.getCategory())) continue;
                if (over100k && t.getAmount() < 100000) continue;
                if (big && (threshold <= 0 || t.getAmount() < threshold)) continue;

                data.items.add(t);
                if (Stats.EXPENSE.equals(t.getType())) data.total += t.getAmount();
            }
            return data;
        }, data -> {
            if (refresh != null) refresh.setRefreshing(false);
            if (root == null || data == null) return;

            adapter.setTransactions(data.items);
            text(R.id.tv_result_count, data.items.size() + " k\u1ebft qu\u1ea3");
            text(R.id.tv_search_total_label, "T\u1ed5ng chi c\u1ee7a k\u1ebft qu\u1ea3");
            text(R.id.tv_search_total, Money.vnd(data.total));
            text(R.id.tv_search_sub, big
                    ? "Kho\u1ea3n chi t\u1eeb " + bigPercent + "% t\u1ed5ng chi c\u1ee7a k\u1ef3 tr\u1edf l\u00ean"
                    : subtitle(filter));

            root.findViewById(R.id.tv_empty_results)
                    .setVisibility(data.items.isEmpty() ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.recycler_results)
                    .setVisibility(data.items.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private String subtitle(int filter) {
        if (filter == TIME_WEEK) return "Trong tu\u1ea7n n\u00e0y";
        if (filter == TIME_YEAR) return "Trong n\u0103m nay";
        return "Trong k\u1ef3 chi ti\u00eau hi\u1ec7n t\u1ea1i";
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }
}
