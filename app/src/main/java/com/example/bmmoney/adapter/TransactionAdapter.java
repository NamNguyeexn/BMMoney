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

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
    private final List<TransactionEntity> transactions = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public void setTransactions(List<TransactionEntity> newTransactions) {
        transactions.clear();
        if (newTransactions != null) transactions.addAll(newTransactions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TransactionViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionEntity t = transactions.get(position);
        boolean income = "INCOME".equals(t.getType());
        holder.tvTitle.setText(t.getTitle());
        holder.tvCategory.setText(t.getCategory());
        holder.tvDate.setText(dateFormat.format(new Date(t.getDate())));
        holder.tvAmount.setText((income ? "+ " : "- ") + currencyFormat.format(t.getAmount()));
        holder.tvAmount.setTextColor(Color.parseColor(income ? "#606C38" : "#BC6C25"));
        holder.tvIcon.setText(income ? "↑" : "↓");
        holder.tvIcon.setBackgroundResource(income ? R.drawable.bg_income : R.drawable.bg_expense);
        holder.itemView.setOnClickListener(v -> showTransactionDetail(v, t, income));
    }

    private void showTransactionDetail(View v, TransactionEntity t, boolean income) {
        String message = "Loại: " + (income ? "Thu nhập" : "Chi tiêu")
                + "\nDanh mục: " + t.getCategory()
                + "\nSố tiền: " + currencyFormat.format(t.getAmount())
                + "\nNgày: " + dateFormat.format(new Date(t.getDate()))
                + "\nGhi chú: " + (t.getNote() == null || t.getNote().isEmpty() ? "Không có" : t.getNote());
        new AlertDialog.Builder(v.getContext())
                .setTitle(t.getTitle())
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }

    @Override
    public int getItemCount() { return transactions.size(); }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvCategory, tvDate, tvAmount;
        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
