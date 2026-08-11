package com.example.bmmoney.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.MainActivity;
import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryEntity;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.LoanEntity;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Man "Them ghi chu".
 *
 * <p><b>Ban va 03/08 - chuyen sang nghiep vu ke toan day du.</b> Man nay gio
 * phuc vu SAU loai ghi chu:</p>
 *
 * <table>
 *   <tr><td>Chi tieu</td><td>EXPENSE</td><td>tien ra vi</td><td>tinh vao lai lo</td></tr>
 *   <tr><td>Thu nhap</td><td>INCOME</td><td>tien vao vi</td><td>tinh vao lai lo</td></tr>
 *   <tr><td>Di vay</td><td>BORROW</td><td>tien vao vi</td><td>tang no phai tra</td></tr>
 *   <tr><td>Tra no goc</td><td>REPAY</td><td>tien ra vi</td><td>giam no phai tra</td></tr>
 *   <tr><td>Cho vay</td><td>LEND</td><td>tien ra vi</td><td>tang no phai thu</td></tr>
 *   <tr><td>Thu hoi no goc</td><td>COLLECT</td><td>tien vao vi</td><td>giam no phai thu</td></tr>
 * </table>
 *
 * <p><b>Khac biet quan trong so voi ban cu:</b> bon loai cong no BAY GIO CO lam
 * doi so du vi (di vay thi tien vao tui that, cho vay thi tien ra tui that),
 * nhung van KHONG tinh vao thu nhap / chi tieu / ngan sach. Cach tach nay dung
 * theo nguyen tac ke toan: vay muon la bien dong tai san va cong no, khong phai
 * lai lo.</p>
 *
 * <p><b>Chua tinh lai.</b> Ban nay chi ghi nhan phan GOC. Khi nao lam ban co lai
 * thi them mot loai INTEREST rieng, khong sua sau loai hien co.</p>
 *
 * <p>Khi ghi Tra no goc / Thu hoi no goc, nguoi dung chon dung khoan vay goc con
 * treo trong danh sach; app luu {@code loanId} de bao cao biet tru vao khoan nao.</p>
 */
public class AddExpenseFragment extends Fragment {

    /** Sau che do cua man hinh. */
    private static final int MODE_EXPENSE = 0;
    private static final int MODE_INCOME = 1;
    private static final int MODE_BORROW = 2;
    private static final int MODE_REPAY = 3;
    private static final int MODE_LEND = 4;
    private static final int MODE_COLLECT = 5;

    /** Phuong thuc thanh toan, dung cho chi tieu va ca bon loai cong no. */
    private static final List<String> PAYMENTS = Arrays.asList(
            "\ud83c\udfe6 Chuy\u1ec3n kho\u1ea3n",
            "\ud83d\udcb5 Ti\u1ec1n m\u1eb7t",
            "\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng",
            "\ud83d\udcf1 V\u00ed \u0111i\u1ec7n t\u1eed",
            "\ud83e\uddfe Kh\u00e1c");

    /** Cach nhan tien cho khoan thu. */
    private static final List<String[]> METHODS = Arrays.asList(
            new String[]{"\ud83d\udcbc", "L\u01b0\u01a1ng"},
            new String[]{"\ud83c\udfe6", "Chuy\u1ec3n kho\u1ea3n"},
            new String[]{"\ud83d\udcb5", "Ti\u1ec1n m\u1eb7t"},
            new String[]{"\ud83c\udf81", "Th\u01b0\u1edfng"},
            new String[]{"\ud83d\udcc8", "\u0110\u1ea7u t\u01b0"},
            new String[]{"\ud83e\uddfe", "Kh\u00e1c"});

    private View root;
    private int mode = MODE_EXPENSE;
    private long pickedTime = System.currentTimeMillis();
    /** 0 nghia la nguoi dung chua dat thoi han. */
    private long dueTime = 0L;
    private String payment = PAYMENTS.get(0);
    private String category = "";
    private String method = METHODS.get(0)[1];

    /** Khoan vay goc dang duoc tra bot / thu bot, null la chua chon. */
    @Nullable private TxRow pickedLoan;
    /** So con lai cua khoan vay goc dang chon, dung de canh bao tra qua tay. */
    private double pickedLoanRemaining = 0d;

    private final List<View> cells = new ArrayList<>();
    private final List<Categories.Item> items = new ArrayList<>();
    private final List<View> methodCells = new ArrayList<>();

    private final SimpleDateFormat format =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final SimpleDateFormat dayFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_add_expense, container, false);

        Refresh.setup(root, R.id.refresh_add_expense, this::resetForm);

        View back = root.findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> open(MainActivity.TAB_HOME));

        text(R.id.tv_date, format.format(new Date(pickedTime)));
        text(R.id.tv_payment, payment);

        ViewUtils.onClick(root, R.id.tv_date, v ->
                DateTimeDialog.show(getContext(), pickedTime, time -> {
                    pickedTime = time;
                    text(R.id.tv_date, format.format(new Date(pickedTime)));
                }));

        // Thoi han doi / thoi han phai tra
        ViewUtils.onClick(root, R.id.tv_due, v ->
                DateTimeDialog.show(getContext(),
                        dueTime > 0 ? dueTime : pickedTime + 7L * 24 * 60 * 60 * 1000,
                        time -> {
                            dueTime = time;
                            text(R.id.tv_due, dayFormat.format(new Date(dueTime)));
                        }));

        ViewUtils.onClick(root, R.id.tv_payment, v ->
                SelectDialog.show(getContext(), "Ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n",
                        PAYMENTS, payment, (index, value) -> {
                            payment = value;
                            text(R.id.tv_payment, payment);
                        }));

        View loanRow = root.findViewById(R.id.tv_loan);
        if (loanRow != null) loanRow.setOnClickListener(v -> pickLoan());

        ViewUtils.onClick(root, R.id.btn_mode_expense, v -> setMode(MODE_EXPENSE));
        ViewUtils.onClick(root, R.id.btn_mode_income, v -> setMode(MODE_INCOME));
        ViewUtils.onClick(root, R.id.btn_mode_borrow, v -> setMode(MODE_BORROW));
        ViewUtils.onClick(root, R.id.btn_mode_repay, v -> setMode(MODE_REPAY));
        ViewUtils.onClick(root, R.id.btn_mode_lend, v -> setMode(MODE_LEND));
        ViewUtils.onClick(root, R.id.btn_mode_collect, v -> setMode(MODE_COLLECT));
        ViewUtils.onClick(root, R.id.btn_submit, v -> save());

        buildCategories();
        buildMethods();
        applyMode();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        buildCategories();
    }

    @Override
    public void onDestroyView() {
        cells.clear();
        items.clear();
        methodCells.clear();
        pickedLoan = null;
        root = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------- che do nhap
    private void setMode(int next) {
        if (mode == next) return;
        mode = next;
        // Doi che do thi bo khoan vay dang chon de tranh gan nham loanId
        pickedLoan = null;
        pickedLoanRemaining = 0d;
        applyMode();
    }

    /** Loai luu vao database ung voi che do dang chon. */
    private String currentType() {
        switch (mode) {
            case MODE_INCOME: return Stats.INCOME;
            case MODE_BORROW: return Stats.BORROW;
            case MODE_REPAY: return Stats.REPAY;
            case MODE_LEND: return Stats.LEND;
            case MODE_COLLECT: return Stats.COLLECT;
            default: return Stats.EXPENSE;
        }
    }

    /** Bon loai cong no. */
    private boolean isDebtKind() {
        return Stats.isDebtKind(currentType());
    }

    /** Hai loai tao ra khoan vay goc moi. */
    private boolean isNewLoan() {
        return mode == MODE_BORROW || mode == MODE_LEND;
    }

    /** Hai loai tat toan bot mot khoan vay goc da co. */
    private boolean isSettlement() {
        return mode == MODE_REPAY || mode == MODE_COLLECT;
    }

    /** Bat/tat va doi nhan cho tung o nhap theo che do dang chon. */
    private void applyMode() {
        if (root == null) return;

        text(R.id.tv_add_title, "Th\u00eam ghi ch\u00fa");

        switch (mode) {
            case MODE_INCOME:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n thu");
                text(R.id.tv_amount_label, "S\u1ed1 ti\u1ec1n nh\u1eadn");
                text(R.id.tv_date_label, "Nh\u1eadn khi n\u00e0o");
                text(R.id.tv_mode_hint,
                        "Ti\u1ec1n v\u00e0o v\u00ed \u00b7 c\u1ed9ng v\u00e0o thu nh\u1eadp c\u1ee7a k\u1ef3.");
                break;
            case MODE_BORROW:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n \u0111i vay");
                text(R.id.tv_amount_label, "Vay bao nhi\u00eau");
                text(R.id.tv_person_label, "Vay c\u1ee7a ai");
                text(R.id.tv_date_label, "Vay khi n\u00e0o");
                text(R.id.tv_due_label, "H\u1ea1n ph\u1ea3i tr\u1ea3");
                text(R.id.tv_mode_hint,
                        "Ti\u1ec1n v\u00e0o v\u00ed \u00b7 t\u0103ng n\u1ee3 ph\u1ea3i tr\u1ea3. "
                                + "Kh\u00f4ng t\u00ednh v\u00e0o thu nh\u1eadp.");
                break;
            case MODE_REPAY:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n tr\u1ea3 n\u1ee3");
                text(R.id.tv_amount_label, "Tr\u1ea3 bao nhi\u00eau");
                text(R.id.tv_person_label, "Tr\u1ea3 cho ai");
                text(R.id.tv_date_label, "Tr\u1ea3 khi n\u00e0o");
                text(R.id.tv_loan_label, "Tr\u1ea3 cho kho\u1ea3n vay n\u00e0o");
                text(R.id.tv_mode_hint,
                        "Ti\u1ec1n ra v\u00ed \u00b7 gi\u1ea3m n\u1ee3 ph\u1ea3i tr\u1ea3. "
                                + "Kh\u00f4ng t\u00ednh v\u00e0o chi ti\u00eau.");
                break;
            case MODE_LEND:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n cho vay");
                text(R.id.tv_amount_label, "Cho vay bao nhi\u00eau");
                text(R.id.tv_person_label, "Ai vay");
                text(R.id.tv_date_label, "Cho vay khi n\u00e0o");
                text(R.id.tv_due_label, "H\u1ea1n \u0111\u00f2i");
                text(R.id.tv_mode_hint,
                        "Ti\u1ec1n ra v\u00ed \u00b7 t\u0103ng n\u1ee3 ph\u1ea3i thu. "
                                + "Kh\u00f4ng t\u00ednh v\u00e0o chi ti\u00eau.");
                break;
            case MODE_COLLECT:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n thu n\u1ee3");
                text(R.id.tv_amount_label, "Thu bao nhi\u00eau");
                text(R.id.tv_person_label, "Thu c\u1ee7a ai");
                text(R.id.tv_date_label, "Thu khi n\u00e0o");
                text(R.id.tv_loan_label, "Thu cho kho\u1ea3n cho vay n\u00e0o");
                text(R.id.tv_mode_hint,
                        "Ti\u1ec1n v\u00e0o v\u00ed \u00b7 gi\u1ea3m n\u1ee3 ph\u1ea3i thu. "
                                + "Kh\u00f4ng t\u00ednh v\u00e0o thu nh\u1eadp.");
                break;
            default:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n chi");
                text(R.id.tv_amount_label, "S\u1ed1 ti\u1ec1n");
                text(R.id.tv_date_label, "Th\u1eddi gian");
                text(R.id.tv_mode_hint,
                        "Ti\u1ec1n ra v\u00ed \u00b7 tr\u1eeb v\u00e0o ng\u00e2n s\u00e1ch chi ti\u00eau c\u1ee7a k\u1ef3.");
                break;
        }

        boolean expense = mode == MODE_EXPENSE;
        boolean income = mode == MODE_INCOME;

        show(R.id.box_person, isDebtKind());
        // Chi khoan vay goc moi can dat han; tra bot / thu bot thi khong
        show(R.id.box_due, isNewLoan());
        show(R.id.box_loan, isSettlement());
        show(R.id.box_description, expense);
        show(R.id.box_category, expense);
        show(R.id.box_income_method, income);
        show(R.id.box_payment, expense || isDebtKind());
        show(R.id.box_note, !income);

        text(R.id.tv_due, dueTime > 0
                ? dayFormat.format(new Date(dueTime))
                : "Ch\u01b0a \u0111\u1eb7t th\u1eddi h\u1ea1n");
        showPickedLoan();

        tab(R.id.btn_mode_expense, expense);
        tab(R.id.btn_mode_income, income);
        tab(R.id.btn_mode_borrow, mode == MODE_BORROW);
        tab(R.id.btn_mode_repay, mode == MODE_REPAY);
        tab(R.id.btn_mode_lend, mode == MODE_LEND);
        tab(R.id.btn_mode_collect, mode == MODE_COLLECT);
    }

    private void show(int id, boolean visible) {
        View view = root.findViewById(id);
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** To dam nut che do dang chon. */
    private void tab(int id, boolean active) {
        View view = root.findViewById(id);
        if (view == null) return;
        view.setBackgroundResource(active ? R.drawable.bg_pill_olive : R.drawable.bg_pill_cream);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(androidx.core.content.ContextCompat.getColor(
                    view.getContext(), active ? R.color.cream : R.color.olive));
        }
    }

    // ------------------------------------------------------- chon khoan vay goc

    /**
     * Mo danh sach khoan vay goc con treo de tru bot.
     *
     * <p>Tra no goc thi chon trong cac khoan DI VAY, thu hoi no goc thi chon
     * trong cac khoan CHO VAY. Danh sach da loc san khoan da tra du hoac da
     * danh dau tat toan nen khong the chon nham.</p>
     */
    private void pickLoan() {
        if (getContext() == null) return;
        final Context app = getContext().getApplicationContext();
        final String loanType = mode == MODE_REPAY ? Stats.BORROW : Stats.LEND;

        Db.load(() -> AppDatabase.dao(app).getOpenLoans(loanType), list -> {
            if (root == null || getContext() == null) return;
            if (list == null || list.isEmpty()) {
                Notice.error(root, loanType.equals(Stats.BORROW)
                        ? "Ch\u01b0a c\u00f3 kho\u1ea3n \u0111i vay n\u00e0o \u0111ang treo"
                        : "Ch\u01b0a c\u00f3 kho\u1ea3n cho vay n\u00e0o \u0111ang treo", null);
                return;
            }
            final List<TxRow> loans = new ArrayList<>(list);
            List<String> labels = new ArrayList<>();
            for (TxRow loan : loans) {
                labels.add(loanLabel(app, loan));
            }
            String current = pickedLoan == null ? "" : loanLabel(app, pickedLoan);
            SelectDialog.show(getContext(), "Ch\u1ecdn kho\u1ea3n g\u1ed1c", labels, current,
                    (index, value) -> {
                        if (index < 0 || index >= loans.size()) return;
                        pickedLoan = loans.get(index);
                        // Tu dien ten doi tac cho nguoi dung do phai go lai
                        EditText personField = root.findViewById(R.id.edt_person);
                        if (personField != null && pickedLoan.personOrEmpty().length() > 0) {
                            personField.setText(pickedLoan.personOrEmpty());
                        }
                        loadRemaining();
                    });
        });
    }

    /** Doc lai so con lai cua khoan dang chon de hien duoi o chon. */
    private void loadRemaining() {
        if (pickedLoan == null || getContext() == null) {
            showPickedLoan();
            return;
        }
        final Context app = getContext().getApplicationContext();
        final TxRow loan = pickedLoan;
        final String loanId = loan.loanIdOrEmpty();
        Db.load(() -> loanId.isEmpty() ? 0d : AppDatabase.dao(app).paidOfLoan(loanId), paid -> {
            double left = loan.getAmount() - (paid == null ? 0d : paid);
            pickedLoanRemaining = left < 0 ? 0d : left;
            showPickedLoan();
        });
    }

    private String loanLabel(Context context, TxRow loan) {
        String who = loan.personOrEmpty();
        if (who.isEmpty()) who = "Ch\u01b0a ghi t\u00ean";
        String due = loan.dueMillis() > 0
                ? " \u00b7 h\u1ea1n " + dayFormat.format(new Date(loan.dueMillis()))
                : "";
        return who + " \u00b7 " + Money.vnd(loan.getAmount()) + due;
    }

    private void showPickedLoan() {
        if (root == null) return;
        if (pickedLoan == null) {
            text(R.id.tv_loan, "Ch\u01b0a ch\u1ecdn kho\u1ea3n g\u1ed1c");
            return;
        }
        String who = pickedLoan.personOrEmpty();
        if (who.isEmpty()) who = "Ch\u01b0a ghi t\u00ean";
        text(R.id.tv_loan, who + " \u00b7 c\u00f2n " + Money.vnd(pickedLoanRemaining));
    }

    // ------------------------------------------------------------- danh muc chi tieu

    /**
     * Dung lai khoi chon danh muc.
     *
     * <p><b>Loi 03/08 (crash khi mo man Them):</b> layout moi da doi
     * {@code container_cats} tu LinearLayout thanh mot TextView kieu o chon,
     * nhung code cu van ep thang sang LinearLayout nen no ClassCastException:
     * MaterialTextView cannot be cast to LinearLayout.</p>
     *
     * <p>Nay ho tro CA HAI kieu layout: neu la khung chua thi ve luoi o nhu cu,
     * neu la o chon thi gan su kien bam de mo SelectDialog. Nho vay khong sap
     * app ma nguoi dung van chon duoc danh muc.</p>
     */
    private void buildCategories() {
        if (root == null || getContext() == null) return;

        if (!Categories.isReady()) {
            // Chua nap xong ban sao danh muc: cho roi ve lai, tranh o chon rong
            Categories.whenReady(getContext(), this::buildCategories);
            return;
        }

        items.clear();
        items.addAll(Categories.all(getContext()));

        // Danh muc dang chon co the vua bi xoa trong Cai dat
        if (items.isEmpty()) {
            category = "";
        } else if (!hasCategory(category)) {
            category = items.get(0).name;
        }

        View found = root.findViewById(R.id.container_cats);
        if (found == null) return;

        if (found instanceof ViewGroup) {
            buildCategoryCells((ViewGroup) found);
            return;
        }

        // Layout kieu o chon: mot dong text bam vao de mo danh sach
        cells.clear();
        found.setOnClickListener(v -> pickCategory());
        showPickedCategory();
    }

    private boolean hasCategory(String name) {
        for (Categories.Item item : items) {
            if (item.name.equals(name)) return true;
        }
        return false;
    }

    /** Kieu layout cu: ve tung o danh muc vao khung chua. */
    private void buildCategoryCells(ViewGroup container) {
        container.removeAllViews();
        cells.clear();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (Categories.Item item : items) {
            View cell = inflater.inflate(R.layout.item_cat_cell, container, false);
            ((TextView) cell.findViewById(R.id.tv_cell_emoji)).setText(item.emoji);
            ((TextView) cell.findViewById(R.id.tv_cell_name)).setText(item.name);
            cell.setOnClickListener(v -> {
                category = item.name;
                highlightCategories();
            });
            container.addView(cell);
            cells.add(cell);
        }
        highlightCategories();
    }

    private void pickCategory() {
        if (root == null || getContext() == null) return;
        if (items.isEmpty()) {
            Notice.error(root,
                    "Ch\u01b0a c\u00f3 danh m\u1ee5c n\u00e0o, th\u00eam trong C\u00e0i \u0111\u1eb7t nh\u00e9", null);
            return;
        }
        List<String> labels = new ArrayList<>();
        String current = "";
        for (Categories.Item item : items) {
            String label = categoryLabel(item);
            labels.add(label);
            if (item.name.equals(category)) current = label;
        }
        SelectDialog.show(getContext(), "Ch\u1ecdn danh m\u1ee5c", labels, current,
                (index, value) -> {
                    if (index < 0 || index >= items.size()) return;
                    category = items.get(index).name;
                    showPickedCategory();
                    highlightCategories();
                });
    }

    private String categoryLabel(Categories.Item item) {
        String emoji = item.emoji == null ? "" : item.emoji.trim();
        return emoji.isEmpty() ? item.name : emoji + " " + item.name;
    }

    /** Cap nhat chu tren o chon danh muc (chi dung o layout kieu o chon). */
    private void showPickedCategory() {
        if (root == null) return;
        View found = root.findViewById(R.id.container_cats);
        if (!(found instanceof TextView)) return;
        String label = "Ch\u1ecdn danh m\u1ee5c";
        for (Categories.Item item : items) {
            if (item.name.equals(category)) label = categoryLabel(item);
        }
        ((TextView) found).setText(label);
    }

    private void highlightCategories() {
        for (int i = 0; i < cells.size() && i < items.size(); i++) {
            boolean active = items.get(i).name.equals(category);
            cells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    // ------------------------------------------------------------- cach nhan tien

    /** Giong buildCategories: ho tro ca khung chua lan o chon. */
    private void buildMethods() {
        if (root == null || getContext() == null) return;

        View found = root.findViewById(R.id.container_methods);
        if (found == null) return;

        if (found instanceof ViewGroup) {
            buildMethodCells((ViewGroup) found);
            return;
        }

        methodCells.clear();
        found.setOnClickListener(v -> pickMethod());
        showPickedMethod();
    }

    private void buildMethodCells(ViewGroup container) {
        container.removeAllViews();
        methodCells.clear();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (String[] entry : METHODS) {
            View cell = inflater.inflate(R.layout.item_cat_cell, container, false);
            ((TextView) cell.findViewById(R.id.tv_cell_emoji)).setText(entry[0]);
            ((TextView) cell.findViewById(R.id.tv_cell_name)).setText(entry[1]);
            cell.setOnClickListener(v -> {
                method = entry[1];
                highlightMethods();
            });
            container.addView(cell);
            methodCells.add(cell);
        }
        highlightMethods();
    }

    private void pickMethod() {
        if (root == null || getContext() == null) return;
        List<String> labels = new ArrayList<>();
        String current = "";
        for (String[] entry : METHODS) {
            String label = entry[0] + " " + entry[1];
            labels.add(label);
            if (entry[1].equals(method)) current = label;
        }
        SelectDialog.show(getContext(), "Ch\u1ecdn ngu\u1ed3n thu", labels, current,
                (index, value) -> {
                    if (index < 0 || index >= METHODS.size()) return;
                    method = METHODS.get(index)[1];
                    showPickedMethod();
                    highlightMethods();
                });
    }

    private void showPickedMethod() {
        if (root == null) return;
        View found = root.findViewById(R.id.container_methods);
        if (!(found instanceof TextView)) return;
        String label = "Ch\u1ecdn ngu\u1ed3n thu";
        for (String[] entry : METHODS) {
            if (entry[1].equals(method)) label = entry[0] + " " + entry[1];
        }
        ((TextView) found).setText(label);
    }

    private void highlightMethods() {
        for (int i = 0; i < methodCells.size() && i < METHODS.size(); i++) {
            boolean active = METHODS.get(i)[1].equals(method);
            methodCells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    // ------------------------------------------------------------- luu

    /**
     * Ban nhap da san sang ghi xuong.
     *
     * <p><b>Vi sao phai co lop nay:</b> danh muc va doi tac gio la KHOA SO, ma tra ra
     * khoa thi phai doc co so du lieu - viec do khong duoc lam tren luong giao dien.
     * Nen phan doc form chi tra ve TEN, con phan ghi o luong nen moi doi ten thanh
     * khoa. Tach nhu vay thi khong con cach nao lo tay truy van tren luong chinh.</p>
     */
    private static class Draft {
        TransactionEntity tx;

        /** Ten danh muc can tra thanh khoa. null la khong gan danh muc. */
        String categoryName;

        /** Ten doi tac can tra thanh khoa. null la khong gan doi tac. */
        String partnerName;

        /** Khac null thi phai MO MOT KHOAN GOC moi truoc khi ghi dong nay. */
        String loanDirection;

        /** Han tra / han doi cua khoan goc moi. 0 la khong dat han. */
        long dueDate;
    }

    private void save() {
        if (root == null || getContext() == null) return;

        EditText amountField = root.findViewById(R.id.edt_amount);
        String rawAmount = amountField.getText().toString().replaceAll("[^0-9]", "");
        if (rawAmount.isEmpty()) {
            Notice.error(root, "Nh\u1eadp s\u1ed1 ti\u1ec1n tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9", null);
            return;
        }
        // So tien la so nguyen DONG. Tien Viet khong co phan le nen khong co ly do gi
        // de mang kieu thap phan qua cac phep cong tru.
        final long amount;
        try {
            amount = Long.parseLong(rawAmount);
        } catch (NumberFormatException e) {
            Notice.error(root, "S\u1ed1 ti\u1ec1n qu\u00e1 l\u1ed7n", null);
            return;
        }

        final Draft draft;
        switch (mode) {
            case MODE_INCOME: draft = buildIncome(amount); break;
            case MODE_BORROW: draft = buildLoan(amount, Stats.BORROW); break;
            case MODE_LEND: draft = buildLoan(amount, Stats.LEND); break;
            case MODE_REPAY: draft = buildSettlement(amount, Stats.REPAY); break;
            case MODE_COLLECT: draft = buildSettlement(amount, Stats.COLLECT); break;
            default: draft = buildExpense(amount); break;
        }
        if (draft == null || draft.tx == null) return;

        final Context app = getContext().getApplicationContext();
        final int savedMode = mode;

        Db.io(() -> {
            long now = System.currentTimeMillis();
            TransactionEntity tx = draft.tx;

            // Doi TEN thanh KHOA. Cac ham ensure(...) tu tao dong moi neu chua co, nen
            // go ten mot doi tac moi la no duoc them vao bang doi tac ngay tai day.
            if (draft.categoryName != null && !draft.categoryName.isEmpty()) {
                tx.setCategoryId(AppDatabase.categories(app)
                        .ensure(draft.categoryName, CategoryEntity.FALLBACK_EMOJI));
            }

            Integer partnerId = null;
            if (draft.partnerName != null && !draft.partnerName.isEmpty()) {
                partnerId = AppDatabase.partners(app).ensure(draft.partnerName);
                tx.setPartnerId(partnerId);
            }

            if (draft.loanDirection != null) {
                // KHOAN GOC PHAI DUOC MO TRUOC.
                // Cot loanId cua giao dich la khoa ngoai tro sang bang khoan vay. Ghi
                // dong tien truoc khi dau khoan ton tai la vi pham rang buoc va SQLite
                // se tu choi thang - khong phai lo im lang.
                String loanId = AppDatabase.loans(app).newLoanId();

                LoanEntity header = new LoanEntity();
                header.setLoanId(loanId);
                header.setDirection(draft.loanDirection);
                header.setPrincipal(tx.getAmount());
                header.setPartnerId(partnerId);
                header.setOpenedDate(tx.getDate());
                header.setDueDate(draft.dueDate);
                header.setSettled(0);
                header.setWrittenOff(0);
                header.setUpdatedAt(now);
                AppDatabase.loans(app).insert(header);

                tx.setLoanId(loanId);
            }

            // Dong dau moc sua doi TRUOC khi ghi. Thieu buoc nay updatedAt = 0, ma sao
            // luu tang dan chi lay dong co updatedAt > moc lan truoc.
            tx.setUpdatedAt(now);
            AppDatabase.dao(app).insert(tx);
            // Khong day len cloud ngay: gom thay doi roi sao luu mot lan cho do ton bo nho
            AutoBackup.scheduleSoon(app);
            Db.ui(() -> {
                Notice.success(root, doneMessage(savedMode));
                resetForm();
                open(MainActivity.TAB_HOME);
            });
        });
    }

    private String doneMessage(int savedMode) {
        switch (savedMode) {
            case MODE_INCOME: return "\u0110\u00e3 l\u01b0u kho\u1ea3n thu";
            case MODE_BORROW: return "\u0110\u00e3 l\u01b0u kho\u1ea3n \u0111i vay";
            case MODE_REPAY: return "\u0110\u00e3 l\u01b0u kho\u1ea3n tr\u1ea3 n\u1ee3";
            case MODE_LEND: return "\u0110\u00e3 l\u01b0u kho\u1ea3n cho vay";
            case MODE_COLLECT: return "\u0110\u00e3 l\u01b0u kho\u1ea3n thu n\u1ee3";
            default: return "\u0110\u00e3 l\u01b0u giao d\u1ecbch";
        }
    }

    @Nullable
    private Draft buildExpense(long amount) {
        if (category.isEmpty()) {
            Notice.error(root, "Ch\u1ecdn m\u1ed9t danh m\u1ee5c nh\u00e9", null);
            return null;
        }
        EditText descField = root.findViewById(R.id.edt_description);
        EditText noteField = root.findViewById(R.id.edt_note);

        String title = descField.getText().toString().trim();
        if (title.isEmpty()) title = category;

        String note = noteField.getText().toString().trim();
        String fullNote = note.isEmpty() ? payment : note + " \u00b7 " + payment;

        Draft draft = new Draft();
        draft.tx = new TransactionEntity(title, amount, Stats.EXPENSE, null, fullNote, pickedTime);
        draft.categoryName = category;
        return draft;
    }

    /**
     * Khoan thu chi can: so tien, ngay gio, cach nhan.
     *
     * <p>Cach nhan tien da nam trong tieu de nen KHONG tao mot danh muc rieng cho no.
     * Danh muc la de phan loai CHI TIEU; nhoi "L\u01b0\u01a1ng" hay "Chuy\u1ec3n
     * kho\u1ea3n" vao day se lam danh sach danh muc phinh ra bang nhung muc khong ai
     * chon khi ghi mot khoan chi.</p>
     */
    private Draft buildIncome(long amount) {
        Draft draft = new Draft();
        draft.tx = new TransactionEntity(method, amount, Stats.INCOME, null, "", pickedTime);
        return draft;
    }

    /**
     * Khoan vay goc moi: DI VAY (BORROW) hoac CHO VAY (LEND).
     *
     * <p>Cot category luu phuong thuc thanh toan. Ma loanId duoc gan sau khi
     * chen xong vi phai co id tu database moi sinh duoc ma duy nhat.</p>
     */
    @Nullable
    private Draft buildLoan(long amount, String type) {
        EditText personField = root.findViewById(R.id.edt_person);
        String person = personField.getText().toString().trim();
        if (person.isEmpty()) {
            Notice.error(root, Stats.LEND.equals(type)
                    ? "Ghi t\u00ean ng\u01b0\u1eddi vay nh\u00e9"
                    : "Ghi t\u00ean ng\u01b0\u1eddi cho vay nh\u00e9", null);
            return null;
        }

        EditText noteField = root.findViewById(R.id.edt_note);
        String note = noteField.getText().toString().trim();

        // Phuong thuc thanh toan di vao ghi chu chu khong vao danh muc, cung ly do
        // nhu khoan thu o tren.
        String fullNote = note.isEmpty() ? payment : payment + " \u00b7 " + note;

        TransactionEntity tx = new TransactionEntity(
                person, amount, type, null, fullNote, pickedTime);
        // 0 nghia la khong dat han. Cot nay la kieu nguyen thuy NOT NULL nen khong
        // nhan null nua - va nho vay moi truy van ve han khong phai boc IFNULL.
        tx.setDueDate(dueTime);
        tx.setSettled(0);
        tx.setWrittenOff(0);

        Draft draft = new Draft();
        draft.tx = tx;
        draft.partnerName = person;
        draft.loanDirection = type;
        draft.dueDate = dueTime;
        return draft;
    }

    /**
     * Khoan tat toan bot: TRA NO GOC (REPAY) hoac THU HOI NO GOC (COLLECT).
     *
     * <p>Bat buoc phai gan vao mot khoan vay goc con treo, neu khong bao cao cong
     * no se khong biet tru vao dau. So tien vuot qua so con lai bi chan ngay tai
     * day de khong tao ra cong no am.</p>
     */
    @Nullable
    private Draft buildSettlement(long amount, String type) {
        if (pickedLoan == null) {
            Notice.error(root, "Ch\u1ecdn kho\u1ea3n g\u1ed1c c\u1ea7n t\u1ea5t to\u00e1n nh\u00e9", null);
            return null;
        }
        if (pickedLoanRemaining > 0 && amount > pickedLoanRemaining + 0.5d) {
            Notice.error(root, "S\u1ed1 ti\u1ec1n l\u1edbn h\u01a1n s\u1ed1 c\u00f2n l\u1ea1i ("
                    + Money.vnd(pickedLoanRemaining) + ")", null);
            return null;
        }

        // Ban cu tu bay ra ma "L" + id khi khoan goc khong co ma. Nay moi khoan goc
        // deu duoc cap ma ngay luc mo, nen thieu ma la du lieu that su sai - bay ra
        // mot ma moi chi lam dong tien tat toan tru vao mot khoan khong ton tai.
        String loanId = pickedLoan.loanIdOrEmpty();
        if (loanId.isEmpty()) {
            Notice.error(root,
                    "Kho\u1ea3n g\u1ed1c n\u00e0y thi\u1ebfu m\u00e3, ch\u1ecdn l\u1ea1i nh\u00e9", null);
            return null;
        }

        EditText personField = root.findViewById(R.id.edt_person);
        String person = personField.getText().toString().trim();
        if (person.isEmpty()) person = pickedLoan.personOrEmpty();

        EditText noteField = root.findViewById(R.id.edt_note);
        String note = noteField.getText().toString().trim();
        String fullNote = note.isEmpty() ? payment : payment + " \u00b7 " + note;

        TransactionEntity tx = new TransactionEntity(
                person, amount, type, null, fullNote, pickedTime);
        tx.setLoanId(loanId);

        Draft draft = new Draft();
        draft.tx = tx;
        draft.partnerName = person;
        return draft;
    }

    private void resetForm() {
        if (root == null) return;
        ((EditText) root.findViewById(R.id.edt_amount)).setText("");
        ((EditText) root.findViewById(R.id.edt_description)).setText("");
        ((EditText) root.findViewById(R.id.edt_person)).setText("");
        ((EditText) root.findViewById(R.id.edt_note)).setText("");
        pickedTime = System.currentTimeMillis();
        dueTime = 0L;
        pickedLoan = null;
        pickedLoanRemaining = 0d;
        text(R.id.tv_date, format.format(new Date(pickedTime)));
        text(R.id.tv_due, "Ch\u01b0a \u0111\u1eb7t th\u1eddi h\u1ea1n");
        showPickedLoan();
        buildCategories();
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh =
                root.findViewById(R.id.refresh_add_expense);
        if (refresh != null) refresh.setRefreshing(false);
    }

    private void open(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(tab);
        }
    }

    private void text(int id, String value) {
        if (root == null) return;
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value);
    }
}
