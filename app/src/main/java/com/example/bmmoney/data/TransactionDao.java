package com.example.bmmoney.data;

import androidx.room.*;

import java.util.List;

/**
 * Truy van Room.
 *
 * <p><b>Quy uoc ke toan (ban va 03/08, chua tinh lai):</b></p>
 * <pre>
 * soDuVi   = INCOME - EXPENSE + BORROW - REPAY - LEND + COLLECT
 * phaiThu  = LEND   - COLLECT
 * phaiTra  = BORROW - REPAY
 * taiSanRong = soDuVi + phaiThu - phaiTra
 * </pre>
 *
 * <p>Bon loai cong no LAM DOI so du vi nhung KHONG tinh vao thu nhap / chi tieu,
 * nen moi truy van ngan sach va bieu do danh muc van chi loc EXPENSE / INCOME
 * nhu cu.</p>
 *
 * <p>Moi phep cong don deu day xuong SQLite thay vi tai ca bang len roi cong
 * trong Java.</p>
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

    // ---- Thu chi: giu nguyen, chi loc EXPENSE / INCOME ----
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

    /** Tong so tien cua mot loai bat ky trong khoang thoi gian. */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date BETWEEN :start AND :end")
    Double getSumInRange(String type, long start, long end);

    /** Danh sach mot loai bat ky trong khoang thoi gian, moi nhat truoc. */
    @Query("SELECT * FROM transactions WHERE type = :type AND date BETWEEN :start AND :end ORDER BY date DESC")
    List<TransactionEntity> getByTypeInRange(String type, long start, long end);

    /** Toan bo ban ghi trong mot khoang, sap tang dan (dung cho man Lich). */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    List<TransactionEntity> getRangeAscending(long start, long end);

    /** Doc lai mot ban ghi theo id. */
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    TransactionEntity getById(int id);

    /** Danh dau tat toan / bo tat toan thu cong cho mot khoan vay goc. */
    @Query("UPDATE transactions SET settled = :settled WHERE id = :id")
    void setSettled(int id, int settled);

    /** Danh dau xoa so mot khoan no khong doi duoc nua. */
    @Query("UPDATE transactions SET writtenOff = :writtenOff WHERE id = :id")
    void setWrittenOff(int id, int writtenOff);

    /** Gan ma khoan vay cho mot ban ghi vua chen. */
    @Query("UPDATE transactions SET loanId = :loanId WHERE id = :id")
    void setLoanId(int id, String loanId);

    // =====================================================================
    // Ban va 03/08 - ba bao cao theo nghiep vu ke toan
    // =====================================================================

    /**
     * Bao cao 1 - SO DU VI.
     * Tien vao: INCOME, BORROW, COLLECT. Tien ra: EXPENSE, REPAY, LEND.
     */
    @Query("SELECT IFNULL(SUM(CASE "
            + "WHEN type IN ('INCOME', 'BORROW', 'COLLECT') THEN amount "
            + "WHEN type IN ('EXPENSE', 'REPAY', 'LEND') THEN -amount "
            + "ELSE 0 END), 0) FROM transactions")
    double walletBalance();

    /** So du vi tinh den mot moc thoi gian (dung cho man Lich va bieu do). */
    @Query("SELECT IFNULL(SUM(CASE "
            + "WHEN type IN ('INCOME', 'BORROW', 'COLLECT') THEN amount "
            + "WHEN type IN ('EXPENSE', 'REPAY', 'LEND') THEN -amount "
            + "ELSE 0 END), 0) FROM transactions WHERE date <= :until")
    double walletBalanceUntil(long until);

    /** Bao cao 2 - TONG CON PHAI THU = LEND - COLLECT, bo qua khoan da xoa so. */
    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'LEND' THEN amount ELSE -amount END), 0) "
            + "FROM transactions WHERE type IN ('LEND', 'COLLECT') AND IFNULL(writtenOff, 0) = 0")
    double totalReceivable();

    /** Bao cao 2 - TONG CON PHAI TRA = BORROW - REPAY. */
    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'BORROW' THEN amount ELSE -amount END), 0) "
            + "FROM transactions WHERE type IN ('BORROW', 'REPAY') AND IFNULL(writtenOff, 0) = 0")
    double totalPayable();

    /** Bao cao 3 - LAI LO THUAN cua mot ky = INCOME - EXPENSE. */
    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) "
            + "FROM transactions WHERE type IN ('INCOME', 'EXPENSE') AND date BETWEEN :start AND :end")
    double netProfitInRange(long start, long end);

    /**
     * Bao cao 2 - CONG NO GOP THEO DOI TAC.
     * Chi tra ve doi tac con so du khac 0, xep theo quy mo giam dan.
     */
    @Query("SELECT IFNULL(person, '') AS person, "
            + "SUM(CASE WHEN type = 'LEND' THEN amount WHEN type = 'COLLECT' THEN -amount ELSE 0 END) AS receivable, "
            + "SUM(CASE WHEN type = 'BORROW' THEN amount WHEN type = 'REPAY' THEN -amount ELSE 0 END) AS payable, "
            + "IFNULL(MIN(CASE WHEN type IN ('BORROW', 'LEND') AND IFNULL(settled, 0) = 0 "
            + "AND IFNULL(dueDate, 0) > 0 THEN dueDate END), 0) AS nextDue "
            + "FROM transactions "
            + "WHERE type IN ('BORROW', 'REPAY', 'LEND', 'COLLECT') AND IFNULL(writtenOff, 0) = 0 "
            + "GROUP BY IFNULL(person, '') "
            + "HAVING receivable <> 0 OR payable <> 0 "
            + "ORDER BY (ABS(receivable) + ABS(payable)) DESC")
    List<PartnerBalance> partnerBalances();

    /**
     * Danh sach khoan vay goc kem so da tra bot.
     * Xep theo han gan nhat truoc, khoan chua dat han xuong cuoi.
     */
    @Query("SELECT l.loanId AS loanId, l.type AS type, l.person AS person, "
            + "l.amount AS principal, "
            + "IFNULL((SELECT SUM(p.amount) FROM transactions p "
            + "WHERE p.loanId = l.loanId AND p.type IN ('REPAY', 'COLLECT')), 0) AS paid, "
            + "IFNULL(l.dueDate, 0) AS dueDate, "
            + "IFNULL(l.writtenOff, 0) AS writtenOff, "
            + "IFNULL(l.settled, 0) AS settled "
            + "FROM transactions l "
            + "WHERE l.type IN ('BORROW', 'LEND') AND l.loanId IS NOT NULL "
            + "ORDER BY CASE WHEN IFNULL(l.dueDate, 0) = 0 THEN 1 ELSE 0 END ASC, "
            + "IFNULL(l.dueDate, 0) ASC, l.date ASC")
    List<LoanBalance> loanBalances();

    /**
     * Cac khoan vay goc CON TREO cua mot loai (BORROW hoac LEND):
     * chua xoa so, chua danh dau tat toan va chua tra du goc.
     */
    @Query("SELECT * FROM transactions l "
            + "WHERE l.type = :type AND IFNULL(l.writtenOff, 0) = 0 AND IFNULL(l.settled, 0) = 0 "
            + "AND l.amount > IFNULL((SELECT SUM(p.amount) FROM transactions p "
            + "WHERE p.loanId = l.loanId AND p.type IN ('REPAY', 'COLLECT')), 0) "
            + "ORDER BY CASE WHEN IFNULL(l.dueDate, 0) = 0 THEN 1 ELSE 0 END ASC, "
            + "IFNULL(l.dueDate, 0) ASC, l.date ASC")
    List<TransactionEntity> getOpenLoans(String type);

    /**
     * Giu lai ten cu de cac man hinh chua chuyen doi van bien dich duoc.
     * Y nghia moi: cac khoan vay goc con treo cua loai truyen vao.
     */
    @Query("SELECT * FROM transactions WHERE type = :type AND IFNULL(settled, 0) = 0 "
            + "AND IFNULL(writtenOff, 0) = 0 "
            + "ORDER BY CASE WHEN IFNULL(dueDate, 0) = 0 THEN 1 ELSE 0 END ASC, "
            + "IFNULL(dueDate, 0) ASC, date ASC")
    List<TransactionEntity> getOpenByType(String type);

    /** Tong so tien goc con treo cua mot loai. */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type "
            + "AND IFNULL(settled, 0) = 0 AND IFNULL(writtenOff, 0) = 0")
    Double getOpenTotal(String type);

    /** Tong da tra bot / thu bot cho mot khoan vay goc. */
    @Query("SELECT IFNULL(SUM(amount), 0) FROM transactions "
            + "WHERE loanId = :loanId AND type IN ('REPAY', 'COLLECT')")
    double paidOfLoan(String loanId);

    /** Ma khoan vay lon nhat dang co, dung de sinh ma tiep theo. */
    @Query("SELECT MAX(id) FROM transactions")
    Integer maxId();

    // =====================================================================
    // Ban va 04/08 - KHOAN THU / CHI CAN BANG
    //
    // Khoan can bang lam doi so du vi (walletBalance van tinh no, dung y muon)
    // nhung khong phai chi tieu / thu nhap thuc. Nam truy van duoi day la ban
    // "Skip" cua cac truy van bao cao: giong het ban goc, chi them dieu kien
    // loai mot danh muc ra. Cac man Trang chu / Phan tich / Cai dat dung ban nay
    // de ngan sach va bieu do danh muc khong bi khoan can bang lam meo.
    // =====================================================================

    /** Tong chi tieu cua ky, bo qua mot danh muc. */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' "
            + "AND date BETWEEN :start AND :end AND IFNULL(category, '') != :skip")
    Double getExpenseInRangeSkip(long start, long end, String skip);

    /** Tong thu nhap cua ky, bo qua mot danh muc. */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' "
            + "AND date BETWEEN :start AND :end AND IFNULL(category, '') != :skip")
    Double getIncomeInRangeSkip(long start, long end, String skip);

    /** Chi tieu gop theo danh muc trong ky, bo qua mot danh muc. */
    @Query("SELECT category, SUM(amount) AS total FROM transactions "
            + "WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end "
            + "AND IFNULL(category, '') != :skip GROUP BY category ORDER BY total DESC")
    List<CategoryTotal> getExpenseByCategoryInRangeSkip(long start, long end, String skip);

    /** Lai lo thuan cua ky, bo qua mot danh muc. */
    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) "
            + "FROM transactions WHERE type IN ('INCOME', 'EXPENSE') "
            + "AND date BETWEEN :start AND :end AND IFNULL(category, '') != :skip")
    double netProfitInRangeSkip(long start, long end, String skip);

    /**
     * Tong so tien da can bang tu truoc den nay: thu tinh cong, chi tinh tru.
     * Duong nghia la app hay ghi thieu tien vao, am nghia la hay ghi thieu tien ra.
     */
    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) "
            + "FROM transactions WHERE IFNULL(category, '') = :category")
    double balanceAdjustTotal(String category);

    /** Ban ghi moi nhat cua mot danh muc, dung de khoe lan can bang gan nhat. */
    @Query("SELECT * FROM transactions WHERE IFNULL(category, '') = :category "
            + "ORDER BY date DESC LIMIT 1")
    TransactionEntity latestOfCategory(String category);
}
