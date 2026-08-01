package com.example.bmmoney.data;

import androidx.room.*;

import java.util.List;

/**
 * Truy van Room. Cac cau truy van cu duoc giu nguyen de phan chi tieu / thu nhap
 * khong bi anh huong; phan duoi la truy van moi cho Vay no, Tra no va man Lich.
 *
 * <p>Luu y ke toan: LEND (cho vay) va DEBT (no phai tra) KHONG duoc tinh vao
 * tong chi hay tong thu. Moi cau truy van cu deu loc san theo type nen tu dong
 * bo qua hai loai nay.</p>
 */
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

    // ---- Truy van tong hop: de SQLite tinh thay vi tai ca bang roi tinh trong Java ----
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end")
    Double getExpenseInRange(long start, long end);

    @Query("SELECT category, SUM(amount) AS total FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end GROUP BY category ORDER BY total DESC")
    List<CategoryTotal> getExpenseByCategoryInRange(long start, long end);

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    List<TransactionEntity> getRecent(int limit);

    @Insert
    void insertAll(List<TransactionEntity> list);

    @Query("DELETE FROM transactions")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM transactions")
    int count();

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date BETWEEN :start AND :end")
    Double getIncomeInRange(long start, long end);

    // =====================================================================
    // Ban va 02/08 - Vay no / Tra no / Lich
    // =====================================================================

    /** Tong so tien cua mot loai bat ky trong khoang thoi gian. */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date BETWEEN :start AND :end")
    Double getSumInRange(String type, long start, long end);

    /** Danh sach mot loai bat ky trong khoang thoi gian, moi nhat truoc. */
    @Query("SELECT * FROM transactions WHERE type = :type AND date BETWEEN :start AND :end ORDER BY date DESC")
    List<TransactionEntity> getByTypeInRange(String type, long start, long end);

    /**
     * Cac khoan con treo cua mot loai (LEND hoac DEBT), sap xep theo han
     * GAN NHAT truoc de biet can doi ai / tra ai som nhat.
     * Khoan chua dat han duoc day xuong cuoi danh sach.
     */
    @Query("SELECT * FROM transactions WHERE type = :type AND IFNULL(settled, 0) = 0 "
            + "ORDER BY CASE WHEN IFNULL(dueDate, 0) = 0 THEN 1 ELSE 0 END ASC, "
            + "IFNULL(dueDate, 0) ASC, date ASC")
    List<TransactionEntity> getOpenByType(String type);

    /** Tong so tien con treo cua mot loai: con phai doi / con phai tra. */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND IFNULL(settled, 0) = 0")
    Double getOpenTotal(String type);

    /** Toan bo ban ghi trong mot khoang, sap tang dan theo thoi gian (dung cho man Lich). */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    List<TransactionEntity> getRangeAscending(long start, long end);

    /** Doc lai mot ban ghi theo id (dung sau khi danh dau tat toan). */
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    TransactionEntity getById(int id);

    /** Danh dau tat toan / bo tat toan cho mot khoan vay hoac no. */
    @Query("UPDATE transactions SET settled = :settled WHERE id = :id")
    void setSettled(int id, int settled);
}
