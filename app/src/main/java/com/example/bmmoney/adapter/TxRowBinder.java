package com.example.bmmoney.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.ui.ConfirmDialog;
import com.example.bmmoney.ui.TxDialog;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.TypeStyle;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * VE MOT DONG GIAO DICH.
 *
 * <p>Tach rieng khoi {@link TransactionAdapter} vi hai noi can dung chung cach ve:</p>
 *
 * <ul>
 *   <li>Trang chu va man Lich dung {@code RecyclerView} - danh sach dai, can tai su
 *       dung o hien thi;</li>
 *   <li>Man Tim kiem do thang cac dong vao mot {@code LinearLayout} - xem phan giai
 *       thich trong {@code SearchFragment}.</li>
 * </ul>
 *
 * <p>Neu de moi noi tu ve, hai danh sach se dan dan khac nhau ve dinh dang mot cach
 * am tham. Mot ham dung chung thi khong the lech.</p>
 */
public final class TxRowBinder {

    /** Man hinh nao cho phep xoa thi dang ky listener nay. */
    public interface OnDelete {
        void onDelete(TxRow row);
    }

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private TxRowBinder() {
    }

    /** Tao mot o hien thi tu {@code item_transaction.xml}. */
    public static View inflate(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
    }

    /**
     * Do du lieu vao mot o da co san.
     *
     * @param showTimeOnly cot ngay hien gio thay vi ngay (dung cho man Lich)
     * @param onDelete     null la an nut xoa
     */
    public static void bind(View view, final TxRow row,
                            boolean showTimeOnly, final OnDelete onDelete) {
        if (view == null || row == null) return;

        TextView tvIcon = view.findViewById(R.id.tvIcon);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvCategory = view.findViewById(R.id.tvCategory);
        TextView tvDate = view.findViewById(R.id.tvDate);
        TextView tvAmount = view.findViewById(R.id.tvAmount);
        TextView btnDelete = view.findViewById(R.id.btn_delete_tx);

        final String type = Stats.normalize(row.getType());

        if (tvTitle != null) tvTitle.setText(title(row));
        if (tvCategory != null) tvCategory.setText(badge(row));

        if (tvDate != null) {
            tvDate.setText(showTimeOnly
                    ? TIME_FMT.format(new Date(row.getDate()))
                    : dayLabel(row.getDate()));
        }

        if (tvAmount != null) {
            // Dau + / - lay theo huong tien ra vao vi, khong theo thu chi
            tvAmount.setText(Stats.typeSign(type) + Money.vnd(row.getAmount()));
            tvAmount.setTextColor(ContextCompat.getColor(
                    view.getContext(), TypeStyle.color(type)));
        }

        if (tvIcon != null) {
            tvIcon.setText(Stats.typeGlyph(type));
            tvIcon.setBackgroundResource(TypeStyle.bg(type));
        }

        view.setOnClickListener(v -> TxDialog.show(v.getContext(), row,
                onDelete != null,
                onDelete == null ? null : () -> onDelete.onDelete(null)));

        if (btnDelete != null) {
            if (onDelete == null) {
                btnDelete.setVisibility(View.GONE);
                btnDelete.setOnClickListener(null);
            } else {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> confirmDelete(v, row, onDelete));
            }
        }
    }

    /**
     * Nhan ngay cho cot ben phai: "Hom nay", "Hom qua", con lai la dd/MM/yyyy.
     *
     * <p>So sanh theo NGAY LICH chu khong theo khoang cach 24 gio. Mot khoan ghi luc
     * 23:50 hom qua chi cach hien tai vai chuc phut, nhung no van thuoc ve hom qua -
     * lay hieu mili giay roi chia cho 86.400.000 se doc ra "hom nay", sai voi cach
     * nguoi dung nhin vao cuon so cua minh.</p>
     *
     * <p>Moc so sanh lay tai thoi diem ve, nen danh sach dang mo qua nua dem se tu dung
     * ngay o lan nap lai ke tiep.</p>
     */
    public static String dayLabel(long millis) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);

        Calendar today = Calendar.getInstance();
        if (sameDay(target, today)) return "H\u00f4m nay";

        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(target, today)) return "H\u00f4m qua";

        return DATE_FMT.format(new Date(millis));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Khoan vay / no lay ten nguoi lam tieu de neu khong dat ten rieng. */
    public static String title(TxRow row) {
        String raw = row.getTitle();
        if (raw != null && !raw.trim().isEmpty()) return raw;
        String person = row.personOrEmpty();
        if (!person.isEmpty()) return person;
        return Stats.typeName(row.getType());
    }

    /** The nho duoi tieu de: danh muc, hoac han doi / han tra voi khoan cong no. */
    public static String badge(TxRow row) {
        String kind = Stats.normalize(row.getType());

        if (Stats.isDebtKind(kind)) {
            if (Stats.isSettlement(kind)) {
                String who = row.personOrEmpty();
                return who.isEmpty()
                        ? Stats.typeName(kind)
                        : Stats.typeName(kind) + " \u00b7 " + who;
            }
            if (row.isWrittenOff()) return "\u0110\u00e3 x\u00f3a s\u1ed5";
            if (row.isSettled()) return "\u0110\u00e3 t\u1ea5t to\u00e1n";
            if (row.dueMillis() > 0) {
                return (Stats.isReceivable(kind) ? "H\u1ea1n \u0111\u00f2i " : "H\u1ea1n tr\u1ea3 ")
                        + DATE_FMT.format(new Date(row.dueMillis()));
            }
            return Stats.typeName(kind);
        }

        String category = row.categoryOrEmpty();
        return category.isEmpty() ? "Kh\u00e1c" : category;
    }

    private static void confirmDelete(View anchor, final TxRow row, final OnDelete onDelete) {
        ConfirmDialog.show(anchor.getContext(),
                "\u2715",
                "X\u00f3a giao d\u1ecbch n\u00e0y?",
                title(row) + " \u00b7 " + Money.vnd(row.getAmount()),
                "X\u00f3a",
                () -> onDelete.onDelete(row));
    }
}
