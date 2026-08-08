package com.example.bmmoney.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Truy van bang giao dich.
 *
 * <h3>MOT DIEU KIEN DUNG CHUNG - thay doi quan trong nhat file nay</h3>
 *
 * <p>Man Tim kiem can ba con so tu cung mot bo loc: TONG SO ket qua (de hien
 * "12 ket qua"), TONG TIEN, va MOT TRANG ket qua. Ban cu viet ba cau SQL rieng, moi
 * cau chep lai dieu kien loc bang tay. Chi can sua mot cau ma quen hai cau kia la
 * bo dem va danh sach di theo hai duong khac nhau - man hinh bao "12 ket qua" nhung
 * chi ve duoc vai dong, va khong co gi bao loi ca.</p>
 *
 * <p>Nay {@link #SEARCH_WHERE} la mot hang so duy nhat, ca ba cau deu ghep tu no.
 * Sua dieu kien la ca ba doi cung luc - khong con cach nao de chung lech nhau.</p>
 *
 * <h3>Tim kiem chu chay HOAN TOAN trong SQL</h3>
 *
 * <p>Ban cu keo het ban ghi ve roi so tu khoa bang Java, vi {@code LIKE} cua SQLite
 * khong hieu chu tieng Viet co dau. Do la ly do bo dem tang cho MOI dong khop trong
 * khi danh sach hien thi bi cat o {@code limit} - hai con so khong the nao khop, va
 * phan trang thi khong the lam duoc.</p>
 *
 * <p>Nay moi ban ghi luu san mot ban da bo dau ({@code searchText}), nen dieu kien
 * tu khoa nam ngay trong {@link #SEARCH_WHERE}.</p>
 *
 * <h3>Xoa mem</h3>
 *
 * <p>Moi cau doc deu co {@code t.deleted = 0}. Xoa that thi may khac khong bao gio
 * biet dong do tung ton tai, nen lan dong bo sau se hoi sinh no.</p>
 */
@Dao
public interface TransactionDao {

    // =====================================================================
    // Cac manh SQL dung chung
    // =====================================================================

    /** Ban ghi goc kem ten danh muc / doi tac. Do vao {@link TxRow}. */
    String ROW_SELECT =
            "SELECT t.*, c.name AS categoryName, c.emoji AS categoryEmoji, "
                    + "pn.name AS partnerName ";

    /** Noi hai bang chieu. LEFT JOIN nen giao dich chua gan danh muc van hien ra. */
    String FROM_JOIN =
            "FROM transactions t "
                    + "LEFT JOIN categories c ON c.id = t.categoryId "
                    + "LEFT JOIN partners pn ON pn.id = t.partnerId ";

    /**
     * DIEU KIEN LOC DUY NHAT cua man Tim kiem.
     *
     * <p>Moi dieu kien deu co duong tat rieng, nen khong chon gi thi cau lenh khong
     * ton them chi phi:</p>
     *
     * <ul>
     *   <li>{@code :ignoreTime = 1} bo qua khoang thoi gian</li>
     *   <li>{@code :type IS NULL} lay moi loai ghi chu</li>
     *   <li>{@code :allCats = 1} lay moi danh muc</li>
     *   <li>{@code :minAmount <= 0} khong chan so tien</li>
     *   <li>{@code :keyword IS NULL} khong loc tu khoa</li>
     *   <li>{@code :openOnly = 0} lay ca khoan da tat toan</li>
     * </ul>
     *
     * <p><b>Luu y khi goi:</b> {@code :cats} KHONG duoc rong. SQLite khong chap nhan
     * {@code IN ()}. Khi khong loc danh muc thi truyen {@code allCats = 1} kem mot
     * danh sach mot phan tu bat ky.</p>
     */
    String SEARCH_WHERE =
            "WHERE t.deleted = 0 "
                    + "AND (:ignoreTime = 1 OR t.date BETWEEN :fromTime AND :toTime) "
                    + "AND (:type IS NULL OR t.type = :type) "
                    + "AND (:allCats = 1 OR c.name IN (:cats)) "
                    + "AND (:minAmount <= 0 OR t.amount >= :minAmount) "
                    + "AND (:keyword IS NULL "
                    + "     OR t.searchText LIKE :keyword "
                    + "     OR c.searchName LIKE :keyword "
                    + "     OR pn.searchName LIKE :keyword "
                    + "     OR (:digits IS NOT NULL AND CAST(t.amount AS TEXT) LIKE :digits)) "
                    + "AND (:openOnly = 0 OR EXISTS ( "
                    + "       SELECT 1 FROM loans l "
                    + "       WHERE l.loanId = t.loanId "
                    + "         AND l.deleted = 0 AND l.settled = 0 AND l.writtenOff = 0 "
                    + "         AND l.principal > COALESCE(( "
                    + "               SELECT SUM(p.amount) FROM transactions p "
                    + "               WHERE p.loanId = l.loanId AND p.deleted = 0 "
                    + "                 AND p.type IN ('REPAY', 'COLLECT')), 0))) ";

    /** Moi noi deu sap xep giong nhau: moi nhat truoc, id lam moc pha hoa. */
    String NEWEST_FIRST = "ORDER BY t.date DESC, t.id DESC ";

    /** Khoan cong no con treo: han gan nhat len truoc, chua dat han xuong cuoi. */
    String OPEN_LOAN_EXISTS =
            "EXISTS (SELECT 1 FROM loans l WHERE l.loanId = t.loanId "
                    + "  AND l.deleted = 0 AND l.settled = 0 AND l.writtenOff = 0 "
                    + "  AND l.principal > COALESCE((SELECT SUM(p.amount) FROM transactions p "
                    + "        WHERE p.loanId = l.loanId AND p.deleted = 0 "
                    + "          AND p.type IN ('REPAY', 'COLLECT')), 0)) ";

    // =====================================================================
    // Man Tim kiem - ba cau, MOT dieu kien
    // =====================================================================

    /** Tong so ket qua khop bo loc. */
    @Query("SELECT COUNT(*) " + FROM_JOIN + SEARCH_WHERE)
    int searchCount(int ignoreTime, long fromTime, long toTime, String type,
                    int openOnly, int allCats, List<String> cats, long minAmount,
                    String keyword, String digits);

    /** Tong tien cua TOAN BO ket qua khop bo loc, khong chi rieng trang dang xem. */
    @Query("SELECT COALESCE(SUM(t.amount), 0) " + FROM_JOIN + SEARCH_WHERE)
    long searchTotal(int ignoreTime, long fromTime, long toTime, String type,
                     int openOnly, int allCats, List<String> cats, long minAmount,
                     String keyword, String digits);

    /**
     * Mot trang ket qua.
     *
     * <p>Dung CHINH XAC dieu kien cua {@link #searchCount}, nen khi bo dem bao 12 thi
     * cuon het cac trang se dem duoc dung 12 dong.</p>
     */
    @Query(ROW_SELECT + FROM_JOIN + SEARCH_WHERE + NEWEST_FIRST + "LIMIT :limit OFFSET :offset")
    List<TxRow> searchPage(int ignoreTime, long fromTime, long toTime, String type,
                           int openOnly, int allCats, List<String> cats, long minAmount,
                           String keyword, String digits, int limit, int offset);

    /**
     * Tong tien cua ca khoang thoi gian dang xem, KHONG ap cac the loc chi tiet.
     *
     * <p>Dung lam mau so cho the loc "Dang chu y": mot khoan duoc coi la dang chu y
     * khi no chiem qua N phan tram tong chi cua ky.</p>
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions "
            + "WHERE deleted = 0 AND type = :type "
            + "AND (:ignoreTime = 1 OR date BETWEEN :fromTime AND :toTime)")
    long searchScopeTotal(int ignoreTime, long fromTime, long toTime, String type);

    // =====================================================================
    // Doc danh sach
    // =====================================================================

    @Query(ROW_SELECT + FROM_JOIN + "WHERE t.deleted = 0 " + NEWEST_FIRST + "LIMIT :limit")
    List<TxRow> getRecent(int limit);

    @Query(ROW_SELECT + FROM_JOIN
            + "WHERE t.deleted = 0 AND t.date BETWEEN :from AND :to " + NEWEST_FIRST)
    List<TxRow> getTransactionsByDateRange(long from, long to);

    @Query(ROW_SELECT + FROM_JOIN
            + "WHERE t.deleted = 0 AND t.date BETWEEN :from AND :to "
            + "ORDER BY t.date ASC, t.id ASC")
    List<TxRow> getRangeAscending(long from, long to);

    @Query(ROW_SELECT + FROM_JOIN
            + "WHERE t.deleted = 0 AND t.type = :type AND t.date BETWEEN :from AND :to "
            + NEWEST_FIRST)
    List<TxRow> getByTypeInRange(String type, long from, long to);

    /** Khoan vay goc mot chieu ma van con du no. */
    @Query(ROW_SELECT + FROM_JOIN
            + "WHERE t.deleted = 0 AND t.type = :type AND " + OPEN_LOAN_EXISTS
            + "ORDER BY CASE WHEN t.dueDate = 0 THEN 1 ELSE 0 END ASC, "
            + "t.dueDate ASC, t.date DESC")
    List<TxRow> getOpenLoans(String type);

    @Query(ROW_SELECT + FROM_JOIN + "WHERE t.id = :id LIMIT 1")
    TxRow byId(int id);

    /** Ban ghi goc, khong kem ten. Dung khi can sua roi ghi lai. */
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    TransactionEntity rawById(int id);

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0")
    int count();

    // =====================================================================
    // So lieu tong hop - cong ngay trong SQLite
    // =====================================================================

    /**
     * SO DU VI.
     *
     * <pre>
     * soDuVi = INCOME - EXPENSE + BORROW - REPAY - LEND + COLLECT
     * </pre>
     *
     * <p>Ban cu tai het ban ghi len roi cong bang vong lap Java. Nay la mot cau lenh
     * chay tren index.</p>
     */
    @Query("SELECT COALESCE(SUM(CASE type "
            + "WHEN 'INCOME' THEN amount WHEN 'BORROW' THEN amount WHEN 'COLLECT' THEN amount "
            + "WHEN 'EXPENSE' THEN -amount WHEN 'REPAY' THEN -amount WHEN 'LEND' THEN -amount "
            + "ELSE 0 END), 0) FROM transactions WHERE deleted = 0")
    double walletBalance();

    @Query("SELECT COALESCE(SUM(CASE type "
            + "WHEN 'INCOME' THEN amount WHEN 'BORROW' THEN amount WHEN 'COLLECT' THEN amount "
            + "WHEN 'EXPENSE' THEN -amount WHEN 'REPAY' THEN -amount WHEN 'LEND' THEN -amount "
            + "ELSE 0 END), 0) FROM transactions WHERE deleted = 0 AND date <= :until")
    double walletBalanceUntil(long until);

    /** Tong mot loai trong khoang thoi gian. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions "
            + "WHERE deleted = 0 AND type = :type AND date BETWEEN :from AND :to")
    Double getSumInRange(String type, long from, long to);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions "
            + "WHERE deleted = 0 AND type = 'INCOME' AND date BETWEEN :from AND :to")
    Double getIncomeInRange(long from, long to);

    /**
     * Tong chi cua ky, BO danh muc can bang so du.
     *
     * <p>Khoan can bang van la EXPENSE / INCOME de so du vi tu dong dung, nhung no
     * khong phai chi tieu that. Neu khong loai ra, mot lan can bang ba trieu se bop
     * meo toan bo the "Phan tich theo danh muc".</p>
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM transactions t "
            + "LEFT JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND t.type = 'EXPENSE' "
            + "AND t.date BETWEEN :from AND :to "
            + "AND COALESCE(c.name, '') <> :skipCategory")
    Double getExpenseInRangeSkip(long from, long to, String skipCategory);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM transactions t "
            + "LEFT JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND t.type = 'INCOME' "
            + "AND t.date BETWEEN :from AND :to "
            + "AND COALESCE(c.name, '') <> :skipCategory")
    Double getIncomeInRangeSkip(long from, long to, String skipCategory);

    /** Lai / lo = thu nhap - chi tieu, bo danh muc can bang. */
    @Query("SELECT COALESCE(SUM(CASE t.type "
            + "WHEN 'INCOME' THEN t.amount WHEN 'EXPENSE' THEN -t.amount ELSE 0 END), 0) "
            + "FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND t.date BETWEEN :from AND :to "
            + "AND COALESCE(c.name, '') <> :skipCategory")
    double netProfitInRangeSkip(long from, long to, String skipCategory);

    /**
     * Chi tieu gop theo danh muc.
     *
     * <p>Ten va emoji lay tu BANG DANH MUC chu khong phai tu chuoi chep tren giao
     * dich, nen doi ten danh muc la bieu do doi theo ngay lap tuc - khong con canh
     * mot danh muc bi tach lam hai cot chi vi go khac hoa thuong.</p>
     */
    @Query("SELECT c.name AS category, c.emoji AS emoji, "
            + "COALESCE(SUM(t.amount), 0) AS total, COUNT(t.id) AS items "
            + "FROM transactions t INNER JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND t.type = 'EXPENSE' "
            + "AND t.date BETWEEN :from AND :to AND c.name <> :skipCategory "
            + "GROUP BY c.id ORDER BY total DESC")
    List<CategoryTotal> getExpenseByCategoryInRangeSkip(long from, long to, String skipCategory);

    // ------------------------------------------------------------ cong no

    /** Tong con phai thu: khoan minh cho vay, tru phan da thu lai. */
    @Query("SELECT COALESCE(SUM(l.principal - COALESCE((SELECT SUM(t.amount) "
            + "    FROM transactions t WHERE t.loanId = l.loanId AND t.deleted = 0 "
            + "      AND t.type IN ('REPAY', 'COLLECT')), 0)), 0) "
            + "FROM loans l WHERE l.deleted = 0 AND l.direction = 'LEND' "
            + "AND l.settled = 0 AND l.writtenOff = 0")
    double totalReceivable();

    /** Tong con phai tra: khoan minh di vay, tru phan da tra bot. */
    @Query("SELECT COALESCE(SUM(l.principal - COALESCE((SELECT SUM(t.amount) "
            + "    FROM transactions t WHERE t.loanId = l.loanId AND t.deleted = 0 "
            + "      AND t.type IN ('REPAY', 'COLLECT')), 0)), 0) "
            + "FROM loans l WHERE l.deleted = 0 AND l.direction = 'BORROW' "
            + "AND l.settled = 0 AND l.writtenOff = 0")
    double totalPayable();

    /** So du cong no tung doi tac. Xem them {@link PartnerDao#balances()}. */
    @Query("SELECT p.id AS partnerId, p.name AS person, "
            + "COALESCE(SUM(CASE t.type WHEN 'LEND' THEN t.amount "
            + "                          WHEN 'COLLECT' THEN -t.amount ELSE 0 END), 0) AS receivable, "
            + "COALESCE(SUM(CASE t.type WHEN 'BORROW' THEN t.amount "
            + "                          WHEN 'REPAY' THEN -t.amount ELSE 0 END), 0) AS payable, "
            + "COALESCE(MIN(CASE WHEN t.dueDate > 0 AND t.settled = 0 AND t.writtenOff = 0 "
            + "                  THEN t.dueDate END), 0) AS nextDue "
            + "FROM partners p "
            + "LEFT JOIN transactions t ON t.partnerId = p.id AND t.deleted = 0 "
            + "WHERE p.deleted = 0 GROUP BY p.id "
            + "HAVING receivable <> 0 OR payable <> 0 "
            + "ORDER BY (receivable - payable) DESC")
    List<PartnerBalance> partnerBalances();

    // ------------------------------------------------------- theo moc thoi gian

    /** Tong theo thang, dung cho bieu do xu huong. */
    @Query("SELECT monthKey AS bucket, COALESCE(SUM(amount), 0) AS total, "
            + "COUNT(*) AS items FROM transactions "
            + "WHERE deleted = 0 AND type = :type AND monthKey BETWEEN :fromMonth AND :toMonth "
            + "GROUP BY monthKey ORDER BY monthKey ASC")
    List<BucketTotal> totalByMonth(String type, int fromMonth, int toMonth);

    /** Tong theo ngay, dung cho man Lich. */
    @Query("SELECT dayKey AS bucket, COALESCE(SUM(amount), 0) AS total, "
            + "COUNT(*) AS items FROM transactions "
            + "WHERE deleted = 0 AND type = :type AND dayKey BETWEEN :fromDay AND :toDay "
            + "GROUP BY dayKey ORDER BY dayKey ASC")
    List<BucketTotal> totalByDay(String type, int fromDay, int toDay);

    @Query("SELECT yearKey AS bucket, COALESCE(SUM(amount), 0) AS total, "
            + "COUNT(*) AS items FROM transactions "
            + "WHERE deleted = 0 AND type = :type GROUP BY yearKey ORDER BY yearKey ASC")
    List<BucketTotal> totalByYear(String type);

    @Query("SELECT c.id AS categoryId, c.name AS category, c.emoji AS emoji, "
            + "COALESCE(SUM(t.amount), 0) AS total, COUNT(t.id) AS items "
            + "FROM transactions t INNER JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND t.type = :type "
            + "AND t.monthKey BETWEEN :fromMonth AND :toMonth "
            + "GROUP BY c.id ORDER BY total DESC")
    List<CategoryReport> byCategoryInMonths(String type, int fromMonth, int toMonth);

    /**
     * Tong da can bang tu truoc den nay: khoan THU tru khoan CHI mang danh muc nay.
     *
     * <p>Danh muc gio la khoa so nen phai noi sang bang danh muc de so theo TEN. Doi
     * ten danh muc thi con so nay tu dung theo, khong phai sua truy van.</p>
     */
    @Query("SELECT COALESCE(SUM(CASE t.type "
            + "WHEN 'INCOME' THEN t.amount "
            + "WHEN 'EXPENSE' THEN -t.amount "
            + "ELSE 0 END), 0) "
            + "FROM transactions t INNER JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND c.name = :category")
    double balanceAdjustTotal(String category);

    /** Lan can bang gan nhat, de man hinh noi duoc "lan gan nhat ngay nao". */
    @Query("SELECT t.* FROM transactions t "
            + "INNER JOIN categories c ON c.id = t.categoryId "
            + "WHERE t.deleted = 0 AND c.name = :category "
            + "ORDER BY t.date DESC, t.id DESC LIMIT 1")
    TransactionEntity latestOfCategory(String category);

    /**
     * Da tra / da thu duoc bao nhieu cho mot khoan goc.
     *
     * <p>Tra ve {@code null} khi chua co dong tra nao, nen ben goi phai tu coi null la
     * khong. Giu nguyen kieu nay vi man Them khoan dang doi chieu voi null.</p>
     */
    @Query("SELECT SUM(amount) FROM transactions "
            + "WHERE deleted = 0 AND loanId = :loanId "
            + "AND type IN ('REPAY', 'COLLECT')")
    Double paidOfLoan(String loanId);

    // =====================================================================
    // Ghi
    // =====================================================================

    @Insert
    long insert(TransactionEntity transaction);

    @Insert
    void insertAll(List<TransactionEntity> transactions);

    @Update
    void update(TransactionEntity transaction);

    /**
     * XOA MEM.
     *
     * <p>Dong van nam lai trong bang voi {@code deleted = 1}. May khac nhin thay dau
     * xoa nay va xoa theo. Neu xoa that, may kia khong biet dong do tung ton tai nen
     * lan dong bo sau se day nguoc no tro lai - loi "giao dich da xoa tu song lai".</p>
     */
    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE id = :id")
    void softDelete(int id, long now);

    @Query("UPDATE transactions SET settled = :settled, updatedAt = :now WHERE id = :id")
    void setSettled(int id, int settled, long now);

    @Query("UPDATE transactions SET writtenOff = :writtenOff, updatedAt = :now WHERE id = :id")
    void setWrittenOff(int id, int writtenOff, long now);

    @Query("UPDATE transactions SET loanId = :loanId, updatedAt = :now WHERE id = :id")
    void setLoanId(int id, String loanId, long now);

    /** Xoa sach de nap lai tu ban sao luu. Chi dung trong luong khoi phuc. */
    @Query("DELETE FROM transactions")
    void wipe();

    // =====================================================================
    // Dong bo
    // =====================================================================

    /**
     * Tat ca cac dong KEM TEN danh muc va ten doi tac.
     *
     * <p>Ban sao luu phai mang TEN chu khong mang khoa so. Khoa so chi co y nghia
     * trong may nay: may thu hai cua cung mot nguoi se danh so danh muc khac di, nen
     * chep khoa sang la moi giao dich tro nham danh muc.</p>
     */
    @Query(ROW_SELECT + FROM_JOIN + "WHERE t.deleted = 0 " + NEWEST_FIRST)
    List<TxRow> allRows();

    /** Lay tat ca, KE CA dong da xoa mem, de day dau xoa len may khac. */
    @Query("SELECT * FROM transactions")
    List<TransactionEntity> getAllForSync();

    /** Chi lay phan doi sau lan day cuoi - nen tang cua dong bo tang dan. */
    @Query("SELECT * FROM transactions WHERE updatedAt > :since")
    List<TransactionEntity> changedSince(long since);

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM transactions")
    long maxUpdatedAt();
}
