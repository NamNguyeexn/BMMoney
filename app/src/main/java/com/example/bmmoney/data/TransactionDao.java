package com.example.bmmoney.data;

import androidx.room.*;
import java.util.List;

@Dao
public interface TransactionDao {
    @Insert long insert(TransactionEntity transaction);
    @Update void update(TransactionEntity transaction);
    @Delete void delete(TransactionEntity transaction);
    @Query("SELECT * FROM transactions ORDER BY date DESC") List<TransactionEntity> getAllTransactions();
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'") Double getTotalIncome();
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'") Double getTotalExpense();
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC") List<TransactionEntity> getTransactionsByDateRange(long start, long end);
    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' GROUP BY category ORDER BY total DESC") List<CategoryTotal> getExpenseByCategory();
    @Query("SELECT type, SUM(amount) as total FROM transactions GROUP BY type") List<TypeTotal> getTotalByType();
}
