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
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Man "Them ghi chu" (truoc day ten la Them chi tieu).
 *
 * <p><b>Ban va 02/08.</b> Man nay gio phuc vu BON loai ghi chu:</p>
 * <ul>
 *   <li>Chi tieu: so tien, mo ta, danh muc, thoi gian, thanh toan, ghi chu</li>
 *   <li>Thu nhap: so tien, thoi gian, nguon thu</li>
 *   <li>Vay no (cho vay): ai vay, vay khi nao, vay bao nhieu, thoi han nguoi do vay, thanh toan</li>
 *   <li>Tra no: tra ai, tra khi nao, tra bao nhieu, thoi han can phai tra, thanh toan</li>
 * </ul>
 *
 * <p><b>Quan trong ve ke toan:</b> hai loai vay no va tra no KHONG lam thay doi
 * so du vi. Chung duoc luu voi type rieng ({@link Stats#LEND} va
 * {@link Stats#DEBT}) nen moi truy van tong thu / tong chi / ngan sach cu van
 * chay dung ma khong can sua gi them.</p>
 */
public class AddExpenseFragment extends Fragment {

    /** Bon che do cua man hinh. */
    private static final int MODE_EXPENSE = 0;
    private static final int MODE_INCOME = 1;
    private static final int MODE_LEND = 2;
    private static final int MODE_DEBT = 3;

    /** Phuong thuc thanh toan, dung cho ca chi tieu lan vay no / tra no. */
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

        root.findViewById(R.id.tv_date).setOnClickListener(v ->
                DateTimeDialog.show(getContext(), pickedTime, time -> {
                    pickedTime = time;
                    text(R.id.tv_date, format.format(new Date(pickedTime)));
                }));

        // Thoi han doi / thoi han phai tra
        root.findViewById(R.id.tv_due).setOnClickListener(v ->
                DateTimeDialog.show(getContext(),
                        dueTime > 0 ? dueTime : pickedTime + 7L * 24 * 60 * 60 * 1000,
                        time -> {
                            dueTime = time;
                            text(R.id.tv_due, dayFormat.format(new Date(dueTime)));
                        }));

        root.findViewById(R.id.tv_payment).setOnClickListener(v ->
                SelectDialog.show(getContext(), "Ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n",
                        PAYMENTS, payment, (index, value) -> {
                            payment = value;
                            text(R.id.tv_payment, payment);
                        }));

        root.findViewById(R.id.btn_mode_expense).setOnClickListener(v -> setMode(MODE_EXPENSE));
        root.findViewById(R.id.btn_mode_income).setOnClickListener(v -> setMode(MODE_INCOME));
        root.findViewById(R.id.btn_mode_lend).setOnClickListener(v -> setMode(MODE_LEND));
        root.findViewById(R.id.btn_mode_debt).setOnClickListener(v -> setMode(MODE_DEBT));
        root.findViewById(R.id.btn_submit).setOnClickListener(v -> save());

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
        root = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------- che do nhap
    private void setMode(int next) {
        if (mode == next) return;
        mode = next;
        applyMode();
    }

    private boolean isLend() {
        return mode == MODE_LEND;
    }

    private boolean isDebt() {
        return mode == MODE_DEBT;
    }

    private boolean isDebtKind() {
        return isLend() || isDebt();
    }

    /** Bat/tat va doi nhan cho tung o nhap theo che do dang chon. */
    private void applyMode() {
        if (root == null) return;

        // Tieu de man giu nguyen la "Them ghi chu" cho ca bon che do
        text(R.id.tv_add_title, "Th\u00eam ghi ch\u00fa");

        switch (mode) {
            case MODE_INCOME:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n thu");
                text(R.id.tv_amount_label, "S\u1ed1 ti\u1ec1n nh\u1eadn");
                text(R.id.tv_date_label, "Nh\u1eadn khi n\u00e0o");
                text(R.id.tv_mode_hint,
                        "Kho\u1ea3n n\u00e0y c\u1ed9ng v\u00e0o t\u1ed5ng thu nh\u1eadp c\u1ee7a k\u1ef3.");
                break;
            case MODE_LEND:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n cho vay");
                text(R.id.tv_amount_label, "Vay bao nhi\u00eau");
                text(R.id.tv_person_label, "Ai vay");
                text(R.id.tv_date_label, "Vay khi n\u00e0o");
                text(R.id.tv_due_label, "Th\u1eddi h\u1ea1n ng\u01b0\u1eddi \u0111\u00f3 vay");
                text(R.id.tv_mode_hint,
                        "Cho vay \u2014 ng\u01b0\u1eddi kh\u00e1c n\u1ee3 b\u1ea1n. "
                                + "Kho\u1ea3n n\u00e0y KH\u00d4NG t\u00ednh v\u00e0o thu chi hay ng\u00e2n s\u00e1ch.");
                break;
            case MODE_DEBT:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n n\u1ee3");
                text(R.id.tv_amount_label, "Tr\u1ea3 bao nhi\u00eau");
                text(R.id.tv_person_label, "Tr\u1ea3 ai");
                text(R.id.tv_date_label, "Tr\u1ea3 khi n\u00e0o");
                text(R.id.tv_due_label, "Th\u1eddi h\u1ea1n c\u1ea7n ph\u1ea3i tr\u1ea3");
                text(R.id.tv_mode_hint,
                        "N\u1ee3 ph\u1ea3i tr\u1ea3 \u2014 b\u1ea1n n\u1ee3 ng\u01b0\u1eddi kh\u00e1c. "
                                + "Kho\u1ea3n n\u00e0y KH\u00d4NG t\u00ednh v\u00e0o thu chi hay ng\u00e2n s\u00e1ch.");
                break;
            default:
                text(R.id.btn_submit, "L\u01b0u kho\u1ea3n chi");
                text(R.id.tv_amount_label, "S\u1ed1 ti\u1ec1n");
                text(R.id.tv_date_label, "Th\u1eddi gian");
                text(R.id.tv_mode_hint,
                        "Kho\u1ea3n n\u00e0y tr\u1eeb v\u00e0o ng\u00e2n s\u00e1ch chi ti\u00eau c\u1ee7a k\u1ef3.");
                break;
        }

        boolean expense = mode == MODE_EXPENSE;
        boolean income = mode == MODE_INCOME;

        show(R.id.box_person, isDebtKind());
        show(R.id.box_due, isDebtKind());
        show(R.id.box_description, expense);
        show(R.id.box_category, expense);
        show(R.id.box_income_method, income);
        // Nguoi dung yeu cau co phan "thanh toan" o ca hai giao dien vay no va tra no
        show(R.id.box_payment, expense || isDebtKind());
        show(R.id.box_note, !income);

        text(R.id.tv_due, dueTime > 0
                ? dayFormat.format(new Date(dueTime))
                : "Ch\u01b0a \u0111\u1eb7t th\u1eddi h\u1ea1n");

        tab(R.id.btn_mode_expense, expense);
        tab(R.id.btn_mode_income, income);
        tab(R.id.btn_mode_lend, isLend());
        tab(R.id.btn_mode_debt, isDebt());
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
            ((TextView) view).setTextColor(getResources().getColor(
                    active ? R.color.cream : R.color.olive));
        }
    }

    // ------------------------------------------------------------- danh muc chi tieu
    private void buildCategories() {
        if (root == null || getContext() == null) return;

        LinearLayout container = root.findViewById(R.id.container_cats);
        if (container == null) return;

        container.removeAllViews();
        cells.clear();
        items.clear();
        items.addAll(Categories.all(getContext()));

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

        if (category.isEmpty() && !items.isEmpty()) category = items.get(0).name;
        highlightCategories();
    }

    private void highlightCategories() {
        for (int i = 0; i < cells.size() && i < items.size(); i++) {
            boolean active = items.get(i).name.equals(category);
            cells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    // ------------------------------------------------------------- cach nhan tien
    private void buildMethods() {
        if (root == null || getContext() == null) return;

        LinearLayout container = root.findViewById(R.id.container_methods);
        if (container == null) return;

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

    private void highlightMethods() {
        for (int i = 0; i < methodCells.size() && i < METHODS.size(); i++) {
            boolean active = METHODS.get(i)[1].equals(method);
            methodCells.get(i).setBackgroundResource(
                    active ? R.drawable.bg_cat_selected : R.drawable.bg_cat_unselected);
        }
    }

    // ------------------------------------------------------------- luu
    private void save() {
        if (root == null || getContext() == null) return;

        EditText amountField = root.findViewById(R.id.edt_amount);
        String rawAmount = amountField.getText().toString().replaceAll("[^0-9]", "");
        if (rawAmount.isEmpty()) {
            Notice.error(root, "Nh\u1eadp s\u1ed1 ti\u1ec1n tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9", null);
            return;
        }
        double amount = Double.parseDouble(rawAmount);

        final TransactionEntity entity;
        switch (mode) {
            case MODE_INCOME: entity = buildIncome(amount); break;
            case MODE_LEND: entity = buildDebtKind(amount, Stats.LEND); break;
            case MODE_DEBT: entity = buildDebtKind(amount, Stats.DEBT); break;
            default: entity = buildExpense(amount); break;
        }
        if (entity == null) return;

        final Context app = getContext().getApplicationContext();
        final int savedMode = mode;

        Db.io(() -> {
            AppDatabase.dao(app).insert(entity);
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
            case MODE_LEND: return "\u0110\u00e3 l\u01b0u kho\u1ea3n cho vay";
            case MODE_DEBT: return "\u0110\u00e3 l\u01b0u kho\u1ea3n n\u1ee3";
            default: return "\u0110\u00e3 l\u01b0u giao d\u1ecbch";
        }
    }

    @Nullable
    private TransactionEntity buildExpense(double amount) {
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

        return new TransactionEntity(title, amount, Stats.EXPENSE, category, fullNote, pickedTime);
    }

    /** Khoan thu chi can: so tien, ngay gio, cach nhan. */
    private TransactionEntity buildIncome(double amount) {
        return new TransactionEntity(method, amount, Stats.INCOME, method, "", pickedTime);
    }

    /**
     * Khoan cho vay hoac khoan no phai tra.
     *
     * <p>Cot category duoc dung de luu phuong thuc thanh toan, con person / dueDate
     * la hai cot moi them o ban va nay. settled = 0 nghia la chua tat toan.</p>
     */
    @Nullable
    private TransactionEntity buildDebtKind(double amount, String type) {
        EditText personField = root.findViewById(R.id.edt_person);
        String person = personField.getText().toString().trim();
        if (person.isEmpty()) {
            Notice.error(root, Stats.LEND.equals(type)
                    ? "Ghi t\u00ean ng\u01b0\u1eddi vay nh\u00e9"
                    : "Ghi t\u00ean ng\u01b0\u1eddi c\u1ea7n tr\u1ea3 nh\u00e9", null);
            return null;
        }

        EditText noteField = root.findViewById(R.id.edt_note);
        String note = noteField.getText().toString().trim();

        TransactionEntity entity = new TransactionEntity(
                person, amount, type, payment, note, pickedTime);
        entity.setPerson(person);
        entity.setDueDate(dueTime > 0 ? dueTime : null);
        entity.setSettled(0);
        return entity;
    }

    private void resetForm() {
        if (root == null) return;
        ((EditText) root.findViewById(R.id.edt_amount)).setText("");
        ((EditText) root.findViewById(R.id.edt_description)).setText("");
        ((EditText) root.findViewById(R.id.edt_person)).setText("");
        ((EditText) root.findViewById(R.id.edt_note)).setText("");
        pickedTime = System.currentTimeMillis();
        dueTime = 0L;
        text(R.id.tv_date, format.format(new Date(pickedTime)));
        text(R.id.tv_due, "Ch\u01b0a \u0111\u1eb7t th\u1eddi h\u1ea1n");
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
