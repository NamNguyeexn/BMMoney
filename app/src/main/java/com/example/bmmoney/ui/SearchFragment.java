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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bmmoney.R;
import com.example.bmmoney.adapter.TxRowBinder;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionDao;
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Cycle;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.TextNorm;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * MAN TIM KIEM.
 *
 * <h3>Loi cu va cach sua</h3>
 *
 * <p>Man cu bao "12 ket qua" nhung chi ve duoc hai dong. Nguyen nhan: khi co tu khoa,
 * no keo TOAN BO ban ghi ve roi so bang Java. Bo dem tang cho moi dong khop, trong khi
 * danh sach hien thi bi cat o gioi han trang - hai con so di theo hai duong khac nhau
 * nen khong the nao khop, va phan trang thi khong the lam duoc.</p>
 *
 * <p>Phai lam vay vi {@code LIKE} cua SQLite khong doc duoc chu tieng Viet co dau. Nay
 * moi ban ghi luu san mot ban da bo dau, nen ca ba con so - bo dem, tong tien va danh
 * sach - deu sinh ra tu CUNG MOT dieu kien loc
 * ({@code TransactionDao.SEARCH_WHERE}). Chung khong con cach nao de lech nhau.</p>
 *
 * <h3>Hai thay doi ve cach dung</h3>
 *
 * <ul>
 *   <li>Mo man len la loc san <b>Hom nay</b>, khong con mac dinh thang nay, va cung
 *       khong con lua chon "tat ca" - mot danh sach khong gioi han thoi gian gan nhu
 *       khong bao gio la thu nguoi dung dang tim.</li>
 *   <li>Ket qua tai theo tung trang 20 dong, co nut <b>Xem them</b>. Nhan duoc la nho
 *       bo dem va danh sach nay da dung chung mot dieu kien.</li>
 * </ul>
 *
 * <h3>Vi sao khong dung danh sach cuon</h3>
 *
 * <p>Ca man hinh nam trong mot khung cuon. Long them mot danh sach cuon nua vao trong
 * la hai ben tranh nhau tinh chieu cao. Cac dong duoc gan THANG vao khung dung -
 * chieu cao thanh ra hien nhien, va cach nay hop voi nut "Xem them" hon vi chi phai
 * gan them dong moi thay vi ve lai ca danh sach.</p>
 */
public class SearchFragment extends Fragment {

    /** Mac dinh khi mo man. */
    private static final int TIME_TODAY = 0;
    private static final int TIME_WEEK = 1;
    private static final int TIME_MONTH = 2;
    private static final int TIME_YEAR = 3;

    /** So dong moi lan tai. */
    private static final int PAGE_SIZE = 20;

    /** Cho nguoi dung go xong roi moi tim, tranh chay truy van sau moi phim. */
    private static final long TYPING_DELAY_MS = 300L;

    private static final long BIG_AMOUNT = 100000L;

    private View root;

    /** null nghia la lay moi loai ghi chu. */
    private String kind;

    private int timeFilter = TIME_TODAY;

    private boolean onlyOver100k;

    /** Chi lay khoan chiem ty le lon trong tong chi cua ky. */
    private boolean onlyBig;

    private final List<String> pickedCategories = new ArrayList<>();

    private String keyword = "";

    /** So dong dang hien. Cung la vi tri bat dau cua trang tiep theo. */
    private int shown;

    /** Tong so ket qua khop bo loc. */
    private int totalCount;

    private final Handler typing = new Handler(Looper.getMainLooper());

    private Runnable pendingSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_search, container, false);

        Refresh.setup(root, R.id.refresh_search, new Runnable() {
            @Override
            public void run() {
                reload();
            }
        });

        setupKeyword();
        setupKindChips();
        setupTimeChips();
        setupAmountChips();
        setupCategoryChips();

        View loadMore = root.findViewById(R.id.btn_load_more);
        if (loadMore != null) {
            loadMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadNextPage();
                }
            });
        }

        query(true);
        return root;
    }

    @Override
    public void onDestroyView() {
        // Bo lich tim con treo, tranh cham vao giao dien da bi go
        if (pendingSearch != null) typing.removeCallbacks(pendingSearch);
        super.onDestroyView();
    }

    // =====================================================================
    // Cac the loc
    // =====================================================================

    private void setupKeyword() {
        EditText input = root.findViewById(R.id.edt_search);
        if (input == null) return;

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s == null ? "" : s.toString();
                if (pendingSearch != null) typing.removeCallbacks(pendingSearch);
                pendingSearch = new Runnable() {
                    @Override
                    public void run() {
                        query(true);
                    }
                };
                typing.postDelayed(pendingSearch, TYPING_DELAY_MS);
            }
        });
    }

    private void setupKindChips() {
        kindChip(R.id.chip_kind_expense, Stats.EXPENSE);
        kindChip(R.id.chip_kind_income, Stats.INCOME);
        kindChip(R.id.chip_kind_lend, Stats.LEND);
        kindChip(R.id.chip_kind_borrow, Stats.BORROW);
        kindChip(R.id.chip_kind_repay, Stats.REPAY);
        kindChip(R.id.chip_kind_collect, Stats.COLLECT);
        paintKindChips();
    }

    private void kindChip(int id, final String type) {
        View chip = root.findViewById(id);
        if (chip == null) return;
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Bam lai chinh the dang chon thi bo chon
                kind = type.equals(kind) ? null : type;
                paintKindChips();
                query(true);
            }
        });
    }

    private void paintKindChips() {
        paint(R.id.chip_kind_expense, Stats.EXPENSE.equals(kind));
        paint(R.id.chip_kind_income, Stats.INCOME.equals(kind));
        paint(R.id.chip_kind_lend, Stats.LEND.equals(kind));
        paint(R.id.chip_kind_borrow, Stats.BORROW.equals(kind));
        paint(R.id.chip_kind_repay, Stats.REPAY.equals(kind));
        paint(R.id.chip_kind_collect, Stats.COLLECT.equals(kind));
    }

    private void setupTimeChips() {
        timeChip(R.id.chip_today, TIME_TODAY);
        timeChip(R.id.chip_week, TIME_WEEK);
        timeChip(R.id.chip_month, TIME_MONTH);
        timeChip(R.id.chip_year, TIME_YEAR);
        paintTimeChips();
    }

    private void timeChip(int id, final int value) {
        View chip = root.findViewById(id);
        if (chip == null) return;
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Luon phai co mot khoang thoi gian - bam lai the dang chon khong bo chon
                if (timeFilter == value) return;
                timeFilter = value;
                paintTimeChips();
                query(true);
            }
        });
    }

    private void paintTimeChips() {
        paint(R.id.chip_today, timeFilter == TIME_TODAY);
        paint(R.id.chip_week, timeFilter == TIME_WEEK);
        paint(R.id.chip_month, timeFilter == TIME_MONTH);
        paint(R.id.chip_year, timeFilter == TIME_YEAR);
    }

    private void setupAmountChips() {
        View over = root.findViewById(R.id.chip_over100k);
        if (over != null) {
            over.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onlyOver100k = !onlyOver100k;
                    paint(R.id.chip_over100k, onlyOver100k);
                    query(true);
                }
            });
        }

        View big = root.findViewById(R.id.chip_big);
        if (big != null) {
            big.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onlyBig = !onlyBig;
                    paint(R.id.chip_big, onlyBig);
                    query(true);
                }
            });
        }

        paint(R.id.chip_over100k, onlyOver100k);
        paint(R.id.chip_big, onlyBig);
    }

    private void setupCategoryChips() {
        LinearLayout box = root.findViewById(R.id.container_cat_chips);
        if (box == null) return;

        if (!Categories.isReady()) {
            // Chua nap xong ban sao danh muc: cho roi ve lai, tranh mat the loc
            Categories.whenReady(getContext(), this::setupCategoryChips);
            return;
        }

        box.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Doc tu ban sao trong bo nho, khong cham co so du lieu tren luong giao dien
        for (Categories.Item item : Categories.all(getContext())) {
            final String name = item.name;
            if (name == null || name.isEmpty()) continue;

            View chip = inflater.inflate(R.layout.item_chip, box, false);
            TextView label = chip.findViewById(R.id.tv_chip);
            if (label != null) label.setText(item.label());

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pickedCategories.contains(name)) {
                        pickedCategories.remove(name);
                    } else {
                        pickedCategories.add(name);
                    }
                    paintCategoryChips();
                    query(true);
                }
            });

            chip.setTag(name);
            box.addView(chip);
        }

        paintCategoryChips();
    }

    private void paintCategoryChips() {
        LinearLayout box = root == null ? null : root.findViewById(R.id.container_cat_chips);
        if (box == null) return;
        for (int i = 0; i < box.getChildCount(); i++) {
            View chip = box.getChildAt(i);
            Object tag = chip.getTag();
            boolean on = tag instanceof String && pickedCategories.contains(tag);
            TextView label = chip.findViewById(R.id.tv_chip);
            if (label != null) paint(label, on);
        }
    }

    private void paint(int id, boolean on) {
        View chip = root == null ? null : root.findViewById(id);
        if (chip instanceof TextView) paint((TextView) chip, on);
    }

    /** The dang chon: nen o liu chu kem. The thuong: nen kem chu xanh dam. */
    private void paint(TextView chip, boolean on) {
        Context context = chip.getContext();
        chip.setBackgroundResource(on ? R.drawable.bg_pill_olive : R.drawable.bg_pill_cream);
        chip.setTextColor(ContextCompat.getColor(context,
                on ? R.color.cream : R.color.dark_green));
    }

    // =====================================================================
    // Truy van
    // =====================================================================

    /** Ket qua mot lan tim: ba con so tu cung mot dieu kien loc. */
    private static class Page {
        List<TxRow> items;
        int count;
        long total;

        /** Tong chi cua ca ky, dung lam mau so cho the "Dang chu y". */
        long scopeTotal;
    }

    /**
     * Tai lai tu dau.
     *
     * <p>Phai la {@code public} - {@code MainActivity} goi khi nguoi dung quay lai
     * the nay.</p>
     */
    public void reload() {
        if (root == null) return;
        setupCategoryChips();
        query(true);
    }

    private void loadNextPage() {
        query(false);
    }

    /**
     * Chay mot lan tim.
     *
     * @param fromStart true la tim lai tu dau, false la tai them mot trang
     */
    private void query(final boolean fromStart) {
        Context context = getContext();
        if (context == null || root == null) return;

        final Context app = context.getApplicationContext();
        final int offset = fromStart ? 0 : shown;

        final long[] bounds = timeBounds();
        final String type = kind;
        final String like = TextNorm.like(keyword);
        final String digits = TextNorm.digitsLike(keyword);
        final int bigPercent = Prefs.bigPercent(context);
        final boolean over100k = onlyOver100k;
        final boolean big = onlyBig;

        // Room khong chap nhan IN () - danh sach rong la loi cu phap. Khi khong loc
        // danh muc thi bao allCats = 1 va truyen mot phan tu gia cho dung cu phap.
        final int allCats = pickedCategories.isEmpty() ? 1 : 0;
        final List<String> cats = new ArrayList<>(pickedCategories);
        if (cats.isEmpty()) cats.add("");

        Db.load(new Db.Work<Page>() {
            @Override
            public Page run() {
                TransactionDao dao = AppDatabase.dao(app);
                Page page = new Page();

                page.scopeTotal = dao.searchScopeTotal(0, bounds[0], bounds[1], Stats.EXPENSE);

                long minAmount = 0L;
                if (over100k) minAmount = BIG_AMOUNT;
                if (big) {
                    long threshold = page.scopeTotal * bigPercent / 100L;
                    if (threshold > minAmount) minAmount = threshold;
                }

                page.count = dao.searchCount(0, bounds[0], bounds[1], type,
                        0, allCats, cats, minAmount, like, digits);

                page.total = dao.searchTotal(0, bounds[0], bounds[1], type,
                        0, allCats, cats, minAmount, like, digits);

                page.items = dao.searchPage(0, bounds[0], bounds[1], type,
                        0, allCats, cats, minAmount, like, digits, PAGE_SIZE, offset);

                return page;
            }
        }, new Db.Done<Page>() {
            @Override
            public void run(Page page) {
                render(page, fromStart);
            }
        });
    }

    /** Gan ket qua vao man hinh. */
    private void render(Page page, boolean fromStart) {
        if (root == null) return;

        SwipeRefreshLayout spinner = root.findViewById(R.id.refresh_search);
        if (spinner != null) spinner.setRefreshing(false);

        LinearLayout box = root.findViewById(R.id.container_results);
        TextView countLabel = root.findViewById(R.id.tv_result_count);
        TextView empty = root.findViewById(R.id.tv_empty_results);
        TextView pageInfo = root.findViewById(R.id.tv_page_info);
        View loadMore = root.findViewById(R.id.btn_load_more);
        TextView totalLabel = root.findViewById(R.id.tv_search_total_label);
        TextView totalValue = root.findViewById(R.id.tv_search_total);
        TextView totalSub = root.findViewById(R.id.tv_search_sub);

        if (page == null) {
            if (countLabel != null) countLabel.setText("0 k\u1ebft qu\u1ea3");
            if (box != null) box.removeAllViews();
            if (empty != null) empty.setVisibility(View.VISIBLE);
            if (pageInfo != null) pageInfo.setVisibility(View.GONE);
            if (loadMore != null) loadMore.setVisibility(View.GONE);
            return;
        }

        if (fromStart) {
            shown = 0;
            if (box != null) box.removeAllViews();
        }

        totalCount = page.count;

        // Gan them tung dong moi - khong ve lai nhung dong da co
        if (box != null && page.items != null) {
            for (final TxRow row : page.items) {
                View item = TxRowBinder.inflate(box);
                TxRowBinder.bind(item, row, false, new TxRowBinder.OnDelete() {
                    @Override
                    public void onDelete(TxRow deleted) {
                        deleteTransaction(deleted);
                    }
                });
                box.addView(item);
            }
            shown += page.items.size();
        }

        // Bo dem va danh sach sinh ra tu cung mot dieu kien, nen hai so nay luon khop
        if (countLabel != null) {
            countLabel.setText(shown >= totalCount
                    ? totalCount + " k\u1ebft qu\u1ea3"
                    : shown + "/" + totalCount + " k\u1ebft qu\u1ea3");
        }

        if (empty != null) {
            empty.setVisibility(totalCount == 0 ? View.VISIBLE : View.GONE);
        }

        if (pageInfo != null) {
            if (totalCount > PAGE_SIZE) {
                pageInfo.setVisibility(View.VISIBLE);
                pageInfo.setText("\u0110ang xem " + shown + " tr\u00ean " + totalCount);
            } else {
                pageInfo.setVisibility(View.GONE);
            }
        }

        if (loadMore != null) {
            loadMore.setVisibility(shown < totalCount ? View.VISIBLE : View.GONE);
        }

        if (totalLabel != null) totalLabel.setText(totalLabel(kind));
        if (totalValue != null) totalValue.setText(Money.vnd(page.total));
        if (totalSub != null) {
            totalSub.setText(totalCount + " giao d\u1ecbch \u00b7 " + timeLabel());
        }
    }

    private String totalLabel(String type) {
        if (type == null) return "T\u1ed5ng ti\u1ec1n";
        return "T\u1ed5ng " + Stats.typeName(type).toLowerCase();
    }

    private String timeLabel() {
        switch (timeFilter) {
            case TIME_WEEK:
                return "tu\u1ea7n n\u00e0y";
            case TIME_MONTH:
                return "th\u00e1ng n\u00e0y";
            case TIME_YEAR:
                return "n\u0103m nay";
            case TIME_TODAY:
            default:
                return "h\u00f4m nay";
        }
    }

    /** Xoa MEM roi tim lai tu dau. */
    private void deleteTransaction(TxRow row) {
        Context context = getContext();
        if (context == null || row == null) return;

        final Context app = context.getApplicationContext();
        final int id = row.getId();

        Db.load(new Db.Work<Boolean>() {
            @Override
            public Boolean run() {
                AppDatabase.dao(app).softDelete(id, System.currentTimeMillis());
                return Boolean.TRUE;
            }
        }, new Db.Done<Boolean>() {
            @Override
            public void run(Boolean ok) {
                query(true);
            }
        });
    }

    // =====================================================================
    // Khoang thoi gian
    // =====================================================================

    /**
     * Moc dau va moc cuoi cua khoang dang chon.
     *
     * <p>Thang lay theo CHU KY nguoi dung dat trong cai dat chu khong theo lich -
     * nguoi nhan luong ngay 25 thi "thang nay" cua ho bat dau tu ngay 25.</p>
     */
    private long[] timeBounds() {
        long now = System.currentTimeMillis();
        Context context = getContext();

        switch (timeFilter) {
            case TIME_WEEK:
                return new long[]{Cycle.startOfWeek(now), endOfDay(now)};

            case TIME_MONTH:
                int cycleDay = context == null ? 1 : Prefs.cycleDay(context);
                return Cycle.bounds(cycleDay, now, 0);

            case TIME_YEAR:
                return new long[]{Cycle.startOfYear(now), Cycle.endOfYear(now)};

            case TIME_TODAY:
            default:
                return new long[]{startOfDay(now), endOfDay(now)};
        }
    }

    private long startOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long endOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }
}
