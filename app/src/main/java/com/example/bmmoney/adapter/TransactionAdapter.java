package com.example.bmmoney.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.data.TxRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Danh sach ghi chu tai chinh cho Trang chu va man Lich.
 *
 * <p>Nhan {@link TxRow} thay cho ban ghi goc: ten danh muc va ten doi tac da duoc
 * SQLite noi san bang mot cau JOIN. Neu nhan ban ghi goc, moi dong se phai tu di tra
 * cuu ten - mot danh sach 200 dong hoa thanh 401 luot truy van.</p>
 *
 * <p>Phan ve mot dong nam trong {@link TxRowBinder} vi man Tim kiem cung dung.</p>
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    /** Giu ten cu de cac man hinh khong phai sua cach dang ky. */
    public interface OnDelete {
        void onDelete(TxRow row);
    }

    private final List<TxRow> rows = new ArrayList<>();

    private OnDelete onDelete;

    /** Khi bat, cot ngay hien gio thay vi ngay (dung cho man Lich). */
    private boolean showTimeOnly = false;

    public void setTransactions(List<TxRow> list) {
        rows.clear();
        if (list != null) rows.addAll(list);
        notifyDataSetChanged();
    }

    /** Bat nut xoa tren tung dong (mac dinh la tat). */
    public void setOnDelete(OnDelete listener) {
        this.onDelete = listener;
        notifyDataSetChanged();
    }

    public void setShowTimeOnly(boolean value) {
        this.showTimeOnly = value;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TransactionViewHolder(TxRowBinder.inflate(parent));
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TxRowBinder.bind(holder.itemView, rows.get(position), showTimeOnly,
                onDelete == null ? null : row -> onDelete.onDelete(row));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TransactionViewHolder(@NonNull View view) {
            super(view);
        }
    }
}
