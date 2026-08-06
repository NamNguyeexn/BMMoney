package com.example.bmmoney.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * M\u00e0n T\u00ecm ki\u1ebfm v\u1edbi b\u1ed1n nh\u00f3m b\u1ed9 l\u1ecdc: lo\u1ea1i ghi ch\u00fa, th\u1eddi gian, danh m\u1ee5c v\u00e0 gi\u00e1 tr\u1ecb.
 *
 * <p><b>Ban va 06/08 - ba thay doi:</b></p>
 * <ol>
 *   <li><b>Sua loi "N ket qua nhung khong thay dong nao".</b> RecyclerView nam trong
 *       ScrollView voi wrap_content ma lai bi dat setHasFixedSize(true). Loi hua do
 *       khien RecyclerView BO QUA requestLayout() khi adapter doi du lieu, nen no giu
 *       nguyen chieu cao do duoc o lan layout dau tien - luc adapter con rong, tuc la
 *       0px. O dem ket qua nam ngoai danh sach nen van hien dung so.</li>
 *   <li><b>Vao man khong loc thoi gian nua.</b> Mac dinh la {@link #TIME_ALL} thay vi
 *       thang nay. Bam lai dung the dang chon thi bo chon.</li>
 *   <li><b>Truy van day het xuong SQLite va co phan trang.</b> Truoc day keo ca bang
 *       len roi loc bang Java; nay chi doc dung {@link #PAGE_SIZE} dong moi lan.</li>
 * </ol>
 */
public class SearchFragment extends Fragment {

    /** Ban va 06/08: khong loc thoi gian - trang thai mac dinh khi moi vao man. */
    private static final int TIME_ALL = -1;
    private static final int TIME_WEEK = 0;
    private static final int TIME_MONTH = 1;
    private static final int TIME_YEAR = 2;

    /** So dong nap them moi lan bam "Xem them". */
    private static final int PAGE_SIZE = 20;

    /** Cho go xong hang moi truy van, tranh chay 10 lan cho 10 chu vua go. */
    private static final long TYPING_DELAY_MS = 300L;

    /** Moc cua the loc "Tren 100k". */
    private static final double BIG_AMOUNT = 100000d;

    /**
     * Loc theo loai ghi chu. null nghia la xem tat ca.
     * Voi LEND va BORROW, man hinh chuyen sang che do "so du con treo":
     * bo qua bo loc thoi gian, chi liet ke khoan chua tat toan va xep theo
     * han gan nhat truoc, dong thoi khoa cac the danh muc lai.
     */
    private String kind = null;

    private View root;
    private SwipeRefreshLayout refresh;
    private TransactionAdapter adapter;

    private int timeFilter = TIME_ALL;
    private boolean onlyOver100k = false;
    private boolean onlyBig = false;
    private final Set<String> pickedCategories = new HashSet<>();
    private final List<TextView> categoryChips = new ArrayList<>();
    private String keyword = "";

    /** So ket qua dang ve tren man. Bam "Xem them" thi cong them PAGE_SIZE. */
    private int shown = PAGE_SIZE;

    private final Handler typing = new Handler(Looper.getMainLooper());
    private final Runnable typingTask = this::reload;

    private static class Data {
        List<TransactionEntity> items = new ArrayList<>();
        int count;
        double total;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_search, container, false);

        RecyclerView recycler = root.findViewById(R.id.recycler_results);
        adapter = new TransactionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        // KHONG dat setHasFixedSize(true): danh sach nam trong ScrollView voi
        // wrap_content nen chieu cao PHU THUOC so dong. Dat true la RecyclerView
        // bo qua requestLayout() khi du lieu ve, giu nguyen chieu cao 0px.
        recycler.setHasFixedSize(false);
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
                typing.removeCallbacks(typingTask);
                typing.postDelayed(typingTask, TYPING_DELAY_MS);
            }
        });

        root.findViewById(R.id.chip_time_all).setOnClickListener(v -> setTime(TIME_ALL));
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

        // Sau the loai, dung dung sau loai cua nghiep vu ke toan
        root.findViewById(R.id.chip_kind_expense).setOnClickListener(v -> setKind(Stats.EXPENSE));
        root.findViewById(R.id.chip_kind_income).setOnClickListener(v -> setKind(Stats.INCOME));
        root.findViewById(R.id.chip_kind_borrow).setOnClickListener(v -> setKind(Stats.BORROW));
        root.findViewById(R.id.chip_kind_repay).setOnClickListener(v -> setKind(Stats.REPAY));
        root.findViewById(R.id.chip_kind_lend).setOnClickListener(v -> setKind(Stats.LEND));
        root.findViewById(R.id.chip_kind_collect).setOnClickListener(v -> setKind(Stats.COLLECT));

        // Phan trang: moi lan bam mo rong cua so them PAGE_SIZE dong
        root.findViewById(R.id.btn_load_more).setOnClickListener(v -> {
            shown += PAGE_SIZE;
            query();
        });

        buildCategoryChips();
        styleTimeChips();
        styleKindChips();
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
        typing.removeCallbacks(typingTask);
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
            // Bon loai cong no khong thuoc danh muc chi tieu nao nen khoa lai
            boolean usable = !isDebtKind();
            chip.setEnabled(usable);
            chip.setClickable(usable);
            chip.setAlpha(usable ? 1f : 0.4f);
            container.addView(view);
            categoryChips.add(chip);
        }
    }

    /** Bam lai dung the dang chon thi bo chon, quay ve xem tat ca. */
    private void setKind(String next) {
        kind = next.equals(kind) ? null : next;
        if (isDebtKind()) {
            // Danh muc khong con y nghia voi bon loai cong no
            pickedCategories.clear();
        }
        styleKindChips();
        buildCategoryChips();
        reload();
    }

    private boolean isDebtKind() {
        return Stats.isDebtKind(kind);
    }

    /**
     * Chi hai loai khoan vay GOC moi xem theo "so du con treo";
     * tra no goc / thu hoi no goc la dong tien nen van xem theo khoang thoi gian.
     */
    private boolean isLoanKind() {
        return Stats.BORROW.equals(kind) || Stats.LEND.equals(kind);
    }

    private void styleKindChips() {
        if (root == null) return;
        styleChip(root.findViewById(R.id.chip_kind_expense), Stats.EXPENSE.equals(kind));
        styleChip(root.findViewById(R.id.chip_kind_income), Stats.INCOME.equals(kind));
        styleChip(root.findViewById(R.id.chip_kind_borrow), Stats.BORROW.equals(kind));
        styleChip(root.findViewById(R.id.chip_kind_repay), Stats.REPAY.equals(kind));
        styleChip(root.findViewById(R.id.chip_kind_lend), Stats.LEND.equals(kind));
        styleChip(root.findViewById(R.id.chip_kind_collect), Stats.COLLECT.equals(kind));

        // Khoa nhom the thoi gian khi dang xem so du con treo
        boolean timeUsable = !isLoanKind();
        dim(R.id.chip_time_all, timeUsable);
        dim(R.id.chip_week, timeUsable);
        dim(R.id.chip_month, timeUsable);
        dim(R.id.chip_year, timeUsable);
    }

    /** Lam mo va chan bam mot the khi bo loc do khong con y nghia. */
    private void dim(int id, boolean usable) {
        View view = root.findViewById(id);
        if (view == null) return;
        view.setEnabled(usable);
        view.setClickable(usable);
        view.setAlpha(usable ? 1f : 0.4f);
    }

    /** Bam lai dung the thoi gian dang chon thi bo loc, quay ve "Tat ca". */
    private void setTime(int filter) {
        timeFilter = (timeFilter == filter && filter != TIME_ALL) ? TIME_ALL : filter;
        styleTimeChips();
        reload();
    }

    private void styleTimeChips() {
        if (root == null) return;
        styleChip(root.findViewById(R.id.chip_time_all), timeFilter == TIME_ALL);
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
        final Context app = getContext().getApplicationContext();
        final TransactionDao dao = AppDatabase.dao(app);
        Db.io(() -> {
            dao.delete(item);
            // Hen sao luu sau vai phut de gom nhieu thay doi vao mot lan ghi cloud
            AutoBackup.scheduleSoon(app);
            Db.ui(() -> {
                if (getContext() == null) return;
                Notice.success(root, "\u0110\u00e3 x\u00f3a b\u1ea3n ghi");
                reload();
            });
        });
    }

    /** Nap lai tu dau. Moi lan doi bo loc deu quay ve trang dau. */
    public void reload() {
        shown = PAGE_SIZE;
        query();
    }

    /**
     * Doc mot trang ket qua.
     *
     * <p>Moi dieu kien co the day xuong SQLite deu duoc day xuong. Rieng buoc so
     * tu khoa van lam bang Java: SQLite LIKE khong phan biet hoa - thuong cho chu
     * co dau tieng Viet nen go "an sang" se khong ra "An sang".</p>
     */
    private void query() {
        if (root == null || getContext() == null) return;

        final int cycleDay = Prefs.cycleDay(getContext());
        final long now = System.currentTimeMillis();
        final int bigPercent = Prefs.bigPercent(getContext());

        final String key = keyword;
        final int filter = timeFilter;
        final boolean over100k = onlyOver100k;
        final boolean big = onlyBig;
        final String kindFilter = kind;
        final boolean debtMode = isDebtKind();
        final boolean loanMode = isLoanKind();
        final int limit = shown;

        // Khoan vay goc con treo khong xet thoi gian; the "Tat ca" cung vay
        final int ignoreTime = (loanMode || filter == TIME_ALL) ? 1 : 0;
        final long fromTime;
        final long toTime;
        if (filter == TIME_WEEK) {
            fromTime = Cycle.startOfWeek(now);
            toTime = now;
        } else if (filter == TIME_YEAR) {
            fromTime = Cycle.startOfYear(now);
            toTime = Cycle.endOfYear(now);
        } else if (filter == TIME_MONTH) {
            long[] bounds = Cycle.bounds(cycleDay, now, 0);
            fromTime = bounds[0];
            toTime = bounds[1];
        } else {
            fromTime = 0L;
            toTime = Long.MAX_VALUE;
        }

        final int openOnly = loanMode ? 1 : 0;

        // O tong tien: chua chon loai nao thi van chi cong CHI TIEU nhu truoc
        final String totalType = kindFilter == null ? Stats.EXPENSE : kindFilter;

        final List<String> cats = new ArrayList<>(pickedCategories);
        final int allCats = (debtMode || cats.isEmpty()) ? 1 : 0;
        // Tranh menh de IN () rong - mot so ban SQLite cu khong nhan
        if (cats.isEmpty()) cats.add("");

        final TransactionDao dao = AppDatabase.dao(getContext().getApplicationContext());

        Db.load(() -> {
            Data data = new Data();

            // Nguong "dang chu y" van tinh tren tong CA KY, khong phai trang dang xem
            double scopeTotal = dao.searchScopeTotal(ignoreTime, fromTime, toTime, totalType, openOnly);
            double threshold = scopeTotal * bigPercent / 100d;

            double minAmount = over100k ? BIG_AMOUNT : 0d;
            if (big) {
                // Chua co so lieu de tinh nguong thi khong khoan nao goi la dang chu y
                if (threshold <= 0) return data;
                minAmount = Math.max(minAmount, threshold);
            }

            if (key.isEmpty()) {
                // Duong nhanh: SQLite lo het, chi keo ve dung so dong dang ve
                data.count = dao.searchCount(ignoreTime, fromTime, toTime, kindFilter, openOnly,
                        allCats, cats, minAmount);
                data.total = dao.searchTotal(ignoreTime, fromTime, toTime, totalType, openOnly,
                        allCats, cats, minAmount);
                List<TransactionEntity> page = dao.searchPage(ignoreTime, fromTime, toTime,
                        kindFilter, openOnly, allCats, cats, minAmount, limit, 0);
                if (page != null) data.items = page;
            } else {
                // Co tu khoa: SQLite van loc het phan con lai, Java chi con so tu khoa
                List<TransactionEntity> pool = dao.searchAll(ignoreTime, fromTime, toTime,
                        kindFilter, openOnly, allCats, cats, minAmount);
                if (pool == null) return data;
                for (TransactionEntity t : pool) {
                    if (!matchKeyword(t, key)) continue;
                    data.count++;
                    if (totalType.equals(Stats.normalize(t.getType()))) data.total += t.getAmount();
                    if (data.items.size() < limit) data.items.add(t);
                }
            }
            return data;
        }, data -> {
            if (refresh != null) refresh.setRefreshing(false);
            if (root == null || data == null) return;

            adapter.setTransactions(data.items);

            text(R.id.tv_result_count, data.count + " k\u1ebft qu\u1ea3");
            text(R.id.tv_search_total_label, totalLabel(kindFilter));
            text(R.id.tv_search_total, Money.vnd(data.total));
            text(R.id.tv_search_sub, big
                    ? "Kho\u1ea3n t\u1eeb " + bigPercent + "% t\u1ed5ng c\u1ee7a nh\u00f3m n\u00e0y tr\u1edf l\u00ean"
                    : loanMode
                    ? (Stats.LEND.equals(kindFilter)
                            ? "C\u00f2n ph\u1ea3i thu \u00b7 h\u1ea1n g\u1ea7n nh\u1ea5t tr\u01b0\u1edbc"
                            : "C\u00f2n ph\u1ea3i tr\u1ea3 \u00b7 h\u1ea1n g\u1ea7n nh\u1ea5t tr\u01b0\u1edbc")
                    : subtitle(filter));

            boolean empty = data.items.isEmpty();
            show(R.id.tv_empty_results, empty);
            show(R.id.recycler_results, !empty);

            // Phan trang: con bao nhieu ket qua chua ve
            int remain = data.count - data.items.size();
            show(R.id.btn_load_more, remain > 0);
            text(R.id.btn_load_more, "Xem th\u00eam " + Math.min(remain, PAGE_SIZE) + " k\u1ebft qu\u1ea3");
            show(R.id.tv_page_info, remain > 0);
            text(R.id.tv_page_info, "\u0110ang xem " + data.items.size() + "/" + data.count);
        });
    }

    /** So khop tu khoa: tieu de, ghi chu, danh muc, ten doi tac va ca so tien. */
    private static boolean matchKeyword(TransactionEntity t, String key) {
        if (contains(t.getTitle(), key)) return true;
        if (contains(t.getNote(), key)) return true;
        if (contains(t.getCategory(), key)) return true;
        if (contains(t.personOrEmpty(), key)) return true;
        // O tim kiem co ghi "...so tien": go 250 thi ra ca khoan 250.000 d
        String digits = key.replaceAll("[^0-9]", "");
        return !digits.isEmpty() && String.valueOf((long) t.getAmount()).contains(digits);
    }

    private static boolean contains(String value, String key) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(key);
    }

    /** Nhan cua o tong tien doi theo loai dang xem. */
    private String totalLabel(String kindFilter) {
        if (Stats.LEND.equals(kindFilter)) return "T\u1ed5ng c\u00f2n ph\u1ea3i thu";
        if (Stats.BORROW.equals(kindFilter)) return "T\u1ed5ng c\u00f2n ph\u1ea3i tr\u1ea3";
        if (Stats.REPAY.equals(kindFilter)) return "T\u1ed5ng \u0111\u00e3 tr\u1ea3 n\u1ee3 g\u1ed1c";
        if (Stats.COLLECT.equals(kindFilter)) return "T\u1ed5ng \u0111\u00e3 thu n\u1ee3 g\u1ed1c";
        if (Stats.INCOME.equals(kindFilter)) return "T\u1ed5ng thu nh\u1eadp";
        return "T\u1ed5ng chi c\u1ee7a k\u1ebft qu\u1ea3";
    }

    private String subtitle(int filter) {
        if (filter == TIME_WEEK) return "Trong tu\u1ea7n n\u00e0y";
        if (filter == TIME_YEAR) return "Trong n\u0103m nay";
        if (filter == TIME_MONTH) return "Trong k\u1ef3 chi ti\u00eau hi\u1ec7n t\u1ea1i";
        return "T\u1ea5t c\u1ea3 th\u1eddi gian";
    }

    private void text(int id, String value) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }

    private void show(int id, boolean visible) {
        if (root == null) return;
        View view = root.findViewById(id);
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
