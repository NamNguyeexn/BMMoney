package com.example.bmmoney.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.R;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.ui.TxDialog;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Danh sach ghi chu tai chinh, dung chung cho Trang chu, Tim kiem va man Lich.
 *
 * <p><b>Ban va 02/08:</b></p>
 * <ul>
 *   <li>Ho tro du bon loai: chi tieu, thu nhap, cho vay, no phai tra.</li>
 *   <li>Cho vay va no phai tra KHONG hien dau + hay - vi khong lam doi so du vi.</li>
 *   <li>Bam vao mot dong se mo {@link TxDialog} theo dung chu de cua app thay cho
 *       AlertDialog trang mac dinh truoc day.</li>
 * </ul>
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    /** Man hinh nao muon cho phep xoa thi dang ky listener nay. */
    public interface OnDelete {
        void onDelete(TransactionEntity transaction);
    }

    private final List<TransactionEntity> transactions = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private OnDelete onDelete;

    /** Khi bat, cot ngay hien gio thay vi ngay (dung cho man Lich). */
    private boolean showTimeOnly = false;

    public void setTransactions(List<TransactionEntity> list) {
        transactions.clear();
        if (list != null) transactions.addAll(list);
        notifyDataSetChanged();
    }

    /** Bat nut xoa tren tung dong (mac dinh la tat). */
    public void setOnDelete(OnDelete listener) {
        this.onDelete = listener;
        notifyDataSetChanged();
    }

    /** Man Lich chi xem trong mot ngay nen hien gio cho de doc. */
    public void setShowTimeOnly(boolean value) {
        this.showTimeOnly = value;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TransactionViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder h, int position) {
        final TransactionEntity t = transactions.get(position);
        final String type = t.getType();
        final boolean income = Stats.INCOME.equals(type);
        final boolean lend = Stats.LEND.equals(type);
        final boolean debt = Stats.DEBT.equals(type);

        h.tvTitle.setText(title(t));
        h.tvCategory.setText(badge(t));
        h.tvDate.setText(showTimeOnly
                ? tf.format(new Date(t.getDate()))
                : df.format(new Date(t.getDate())));

        h.tvAmount.setText(Stats.typeSign(type) + Money.vnd(t.getAmount()));
        h.tvAmount.setTextColor(Color.parseColor(
                lend ? "#DDA15E" : debt ? "#283618" : income ? "#606C38" : "#BC6C25"));

        h.tvIcon.setText(Stats.typeGlyph(type));
        h.tvIcon.setBackgroundResource(lend ? R.drawable.bg_lend
                : debt ? R.drawable.bg_debt
                : income ? R.drawable.bg_income : R.drawable.bg_expense);

        h.itemView.setOnClickListener(v -> TxDialog.show(v.getContext(), t,
                onDelete != null,
                onDelete == null ? null : () -> {
                    if (onDelete != null) onDelete.onDelete(null);
                }));

        if (h.btnDelete != null) {
            if (onDelete == null) {
                h.btnDelete.setVisibility(View.GONE);
                h.btnDelete.setOnClickListener(null);
            } else {
                h.btnDelete.setVisibility(View.VISIBLE);
                h.btnDelete.setOnClickListener(v -> confirmDelete(v, t));
            }
        }
    }

    /** Khoan vay / no lay ten nguoi lam tieu de neu nguoi dung khong dat ten rieng. */
    private String title(TransactionEntity t) {
        String raw = t.getTitle();
        if (raw != null && !raw.trim().isEmpty()) return raw;
        String person = t.personOrEmpty();
        if (!person.isEmpty()) return person;
        return Stats.typeName(t.getType());
    }

    /** The nho ben duoi tieu de: danh muc, hoac han doi / han tra voi khoan vay no. */
    private String badge(TransactionEntity t) {
        if (Stats.isDebtKind(t.getType())) {
            if (t.isSettled()) return "\u0110\u00e3 t\u1ea5t to\u00e1n";
            if (t.dueMillis() > 0) {
                return (Stats.LEND.equals(t.getType()) ? "H\u1ea1n \u0111\u00f2i " : "H\u1ea1n tr\u1ea3 ")
                        + df.format(new Date(t.dueMillis()));
            }
            return Stats.typeName(t.getType());
        }
        String category = t.getCategory();
        return category == null || category.isEmpty() ? "Kh\u00e1c" : category;
    }

    /** Hoi lai truoc khi xoa de tranh mat du lieu ghi chep. */
    private void confirmDelete(View anchor, final TransactionEntity t) {
        new androidx.appcompat.app.AlertDialog.Builder(anchor.getContext(), R.style.Theme_Bmm_Dialog)
                .setTitle("B\u1ea1n th\u1ef1c s\u1ef1 mu\u1ed1n x\u00f3a b\u1ea3n ghi n\u00e0y ch\u1ee9?")
                .setMessage(title(t) + " \u00b7 " + Money.vnd(t.getAmount())
                        + "\n\n\u0110i\u1ec1u n\u00e0y c\u00f3 th\u1ec3 l\u00e0m gi\u1ea3m hi\u1ec7u qu\u1ea3 ghi ch\u00fa h\u00e0ng ng\u00e0y c\u1ee7a b\u1ea1n."
                        + "\nWhat gets measured gets managed.")
                .setNegativeButton("Gi\u1eef l\u1ea1i", null)
                .setPositiveButton("X\u00f3a", (dialog, which) -> {
                    if (onDelete != null) onDelete.onDelete(t);
                })
                .show();
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIcon;
        final TextView tvTitle;
        final TextView tvCategory;
        final TextView tvDate;
        final TextView tvAmount;
        final TextView btnDelete;

        TransactionViewHolder(@NonNull View v) {
            super(v);
            tvIcon = v.findViewById(R.id.tvIcon);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvDate = v.findViewById(R.id.tvDate);
            tvAmount = v.findViewById(R.id.tvAmount);
            btnDelete = v.findViewById(R.id.btn_delete_tx);
        }
    }
}
