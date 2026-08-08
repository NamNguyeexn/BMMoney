package com.example.bmmoney.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.Random;

/** Truy van bang khoan vay. */
@Dao
public interface LoanDao {

    /**
     * Cot chung cua moi bao cao cong no.
     *
     * <p>{@code paid} la mot truy van con chay tren index {@code index_transactions_loanId}.
     * Ban cu tai het bang giao dich len roi cong bang vong lap Java de biet moi khoan
     * con lai bao nhieu - so lieu dung nhung chi phi tang theo tong so ban ghi, ke ca
     * khi chi co ba khoan vay.</p>
     */
    String BALANCE_COLUMNS =
            "SELECT l.loanId AS loanId, l.direction AS type, "
                    + "COALESCE(p.name, '') AS person, l.principal AS principal, "
                    + "COALESCE((SELECT SUM(t.amount) FROM transactions t "
                    + "          WHERE t.loanId = l.loanId AND t.deleted = 0 "
                    + "            AND t.type IN ('REPAY', 'COLLECT')), 0) AS paid, "
                    + "l.dueDate AS dueDate, l.writtenOff AS writtenOff, l.settled AS settled "
                    + "FROM loans l LEFT JOIN partners p ON p.id = l.partnerId ";

    /** Han gan nhat len truoc; khoan chua dat han xuong cuoi. */
    String DUE_FIRST =
            "ORDER BY CASE WHEN l.dueDate = 0 THEN 1 ELSE 0 END ASC, l.dueDate ASC";

    /** Khoan con treo mot chieu: LEND la phai thu, BORROW la phai tra. */
    @Query(BALANCE_COLUMNS
            + "WHERE l.deleted = 0 AND l.direction = :direction "
            + "AND l.settled = 0 AND l.writtenOff = 0 "
            + "AND l.principal > COALESCE((SELECT SUM(t.amount) FROM transactions t "
            + "    WHERE t.loanId = l.loanId AND t.deleted = 0 "
            + "      AND t.type IN ('REPAY', 'COLLECT')), 0) "
            + DUE_FIRST)
    List<LoanBalance> openByDirection(String direction);

    /** Moi khoan, ke ca da dong. Dung cho bao cao o man Phan tich. */
    @Query(BALANCE_COLUMNS + "WHERE l.deleted = 0 " + DUE_FIRST)
    List<LoanBalance> allBalances();

    @Query(BALANCE_COLUMNS + "WHERE l.loanId = :loanId LIMIT 1")
    LoanBalance balanceOf(String loanId);

    /** Tong du no con lai mot chieu. Dung cho the Phai thu / Phai tra o Trang chu. */
    @Query("SELECT COALESCE(SUM(l.principal - COALESCE((SELECT SUM(t.amount) "
            + "    FROM transactions t WHERE t.loanId = l.loanId AND t.deleted = 0 "
            + "      AND t.type IN ('REPAY', 'COLLECT')), 0)), 0) "
            + "FROM loans l WHERE l.deleted = 0 AND l.direction = :direction "
            + "AND l.settled = 0 AND l.writtenOff = 0")
    long outstandingTotal(String direction);

    @Query("SELECT * FROM loans WHERE loanId = :loanId LIMIT 1")
    LoanEntity byId(String loanId);

    @Query("SELECT COUNT(*) FROM loans WHERE deleted = 0")
    int count();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(LoanEntity loan);

    @Update
    void update(LoanEntity loan);

    @Query("UPDATE loans SET settled = :settled, updatedAt = :now WHERE loanId = :loanId")
    void setSettled(String loanId, int settled, long now);

    @Query("UPDATE loans SET writtenOff = :writtenOff, updatedAt = :now WHERE loanId = :loanId")
    void setWrittenOff(String loanId, int writtenOff, long now);

    @Query("UPDATE loans SET deleted = 1, updatedAt = :now WHERE loanId = :loanId")
    void softDelete(String loanId, long now);

    @Query("SELECT * FROM loans WHERE updatedAt > :since")
    List<LoanEntity> changedSince(long since);

    @Query("SELECT * FROM loans")
    List<LoanEntity> getAllForSync();

    @Query("DELETE FROM loans")
    void wipe();

    /**
     * Sinh ma khoan vay khong dung so thu tu.
     *
     * <p>Ban cu dung {@code "L" + id} voi {@code id} la so thu tu dong vua chen. Hai
     * may cung ghi mot khoan vay luc dang ngoai mang deu ra {@code L7}; lan dong bo
     * sau, mot khoan se de len khoan kia. Do la mat du lieu that su va khong he co
     * thong bao nao.</p>
     *
     * <p>Ma o day ghep moc thoi gian (he 36) voi phan ngau nhien, nen hai may khong
     * the sinh trung ma van ngan gon va sap xep duoc theo thoi gian.</p>
     */
    default String newLoanId() {
        String stamp = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String salt = Integer.toString(new Random().nextInt(46655) + 1296, 36).toUpperCase();
        return "L" + stamp + salt;
    }
}
