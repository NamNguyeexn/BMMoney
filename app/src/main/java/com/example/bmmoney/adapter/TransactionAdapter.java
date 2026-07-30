package com.example.bmmoney.adapter;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.R;
import com.example.bmmoney.data.TransactionEntity;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Danh sach giao dich dung chung cho Trang chu va Tim kiem. */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    /** Man hinh nao muon cho phep xoa thi dang ky listener nay. */
    public interface OnDelete {
        void onDelete(TransactionEntity transaction);
    }

    private final List<TransactionEntity> transactions = new ArrayList<>();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private OnDelete onDelete;

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

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TransactionViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder h, int position) {
        final TransactionEntity t = transactions.get(position);
        final boolean income = "INCOME".equals(t.getType());

        h.tvTitle.setText(t.getTitle());
        h.tvCategory.setText(t.getCategory());
        h.tvDate.setText(df.format(new Date(t.getDate())));
        h.tvAmount.setText((income ? "+ " : "- ") + money.format(t.getAmount()));
        h.tvAmount.setTextColor(Color.parseColor(income ? "#606C38" : "#BC6C25"));
        h.tvIcon.setText(income ? "\u2191" : "\u2193");
        h.tvIcon.setBackgroundResource(income ? R.drawable.bg_income : R.drawable.bg_expense);

        h.itemView.setOnClickListener(v -> new AlertDialog.Builder(v.getContext())
                .setTitle(t.getTitle())
                .setMessage("Lo\u1ea1i: " + (income ? "Thu nh\u1eadp" : "Chi ti\u00eau")
                        + "\nDanh m\u1ee5c: " + t.getCategory()
                        + "\nS\u1ed1 ti\u1ec1n: " + money.format(t.getAmount())
                        + "\nNg\u00e0y: " + df.format(new Date(t.getDate()))
                        + "\nGhi ch\u00fa: " + (t.getNote() == null || t.getNote().isEmpty()
                        ? "Kh\u00f4ng c\u00f3" : t.getNote()))
                .setPositiveButton("\u0110\u00f3ng", null)
                .show());

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

    /** Hoi lai truoc khi xoa de tranh mat du lieu ghi chep. */
    private void confirmDelete(View anchor, final TransactionEntity t) {
        new AlertDialog.Builder(anchor.getContext())
                .setTitle("B\u1ea1n th\u1ef1c s\u1ef1 mu\u1ed1n x\u00f3a b\u1ea3n ghi chi ti\u00eau n\u00e0y ch\u1ee9?")
                .setMessage(t.getTitle() + " \u00b7 " + money.format(t.getAmount())
                        + "\n\n\u0110i\u1ec1u n\u00e0y c\u00f3 th\u1ec3 l\u00e0m gi\u1ea3m hi\u1ec7u qu\u1ea3 ghi ch\u00fa chi ti\u00eau h\u00e0ng ng\u00e0y c\u1ee7a b\u1ea1n."
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
