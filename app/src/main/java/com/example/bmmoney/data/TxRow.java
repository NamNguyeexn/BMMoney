package com.example.bmmoney.data;

import androidx.room.Embedded;

/**
 * BAN DOC cua mot giao dich: ban ghi goc kem ten danh muc va ten doi tac.
 *
 * <h3>Vi sao can lop nay</h3>
 *
 * <p>Sau khi chuan hoa, {@link TransactionEntity} chi con giu {@code categoryId} va
 * {@code partnerId} - la nhung con so. Nhung giao dien can TEN de hien thi.</p>
 *
 * <p>Neu de tung man hinh tu tra cuu ten cho moi dong thi mot danh sach 200 dong se
 * sinh ra 401 luot truy van (1 lay danh sach + 200 lay danh muc + 200 lay doi tac).
 * Do la loi N+1 kinh dien, va no am tham lam cham dan theo so ban ghi.</p>
 *
 * <p>Lop nay giai quyet bang cach de SQLite noi bang san trong CUNG mot cau truy van:
 * {@code @Embedded} nhan phan {@code t.*} vao {@link #tx}, con ba cot alias nhan ba
 * ten. Mot luot truy van, khong phu thuoc so dong.</p>
 *
 * <h3>Ten ham co y giu giong ban cu</h3>
 *
 * <p>{@code getAmount()}, {@code getCategory()}, {@code personOrEmpty()}... deu trung
 * ten voi ban cu cua {@code TransactionEntity}. Nho vay cac man hinh chi can doi kieu
 * khai bao tu {@code TransactionEntity} sang {@code TxRow}, con than ham giu nguyen.</p>
 */
public class TxRow {

    /** Ban ghi goc. Room do {@code t.*} vao day. */
    @Embedded
    public TransactionEntity tx;

    /** Lay tu {@code categories.name} bang LEFT JOIN. Null neu chua gan danh muc. */
    public String categoryName;

    /** Lay tu {@code categories.emoji}. */
    public String categoryEmoji;

    /** Lay tu {@code partners.name}. Null neu khong lien quan doi tac nao. */
    public String partnerName;

    // --------------------------------------------------- chuyen tiep sang ban ghi goc

    public int getId() { return tx == null ? 0 : tx.getId(); }

    public String getTitle() { return tx == null ? null : tx.getTitle(); }

    public long getAmount() { return tx == null ? 0L : tx.getAmount(); }

    public String getType() { return tx == null ? null : tx.getType(); }

    public String getNote() { return tx == null ? null : tx.getNote(); }

    public long getDate() { return tx == null ? 0L : tx.getDate(); }

    public Integer getCategoryId() { return tx == null ? null : tx.getCategoryId(); }

    public Integer getPartnerId() { return tx == null ? null : tx.getPartnerId(); }

    public Double getRate() { return tx == null ? null : tx.getRate(); }

    public long dueMillis() { return tx == null ? 0L : tx.getDueDate(); }

    public boolean isSettled() { return tx != null && tx.isSettled(); }

    public boolean isWrittenOff() { return tx != null && tx.isWrittenOff(); }

    public String loanIdOrEmpty() { return tx == null ? "" : tx.loanIdOrEmpty(); }

    // ------------------------------------------------------------- ten da noi san

    /** Ten danh muc. Giu ten ham cu de cac man hinh khong phai sua than ham. */
    public String getCategory() {
        return categoryName;
    }

    /** Ten danh muc, chuoi rong neu chua gan. */
    public String categoryOrEmpty() {
        return categoryName == null ? "" : categoryName;
    }

    /** Emoji cua danh muc, tra ve nhan mac dinh neu chua dat. */
    public String emojiOrTag() {
        return categoryEmoji == null || categoryEmoji.isEmpty()
                ? "\uD83C\uDFF7" : categoryEmoji;
    }

    /** Ten doi tac. Giu ten ham cu de cac man hinh khong phai sua than ham. */
    public String getPerson() {
        return partnerName;
    }

    /** Ten doi tac, chuoi rong neu khong co. */
    public String personOrEmpty() {
        return partnerName == null ? "" : partnerName;
    }
}
