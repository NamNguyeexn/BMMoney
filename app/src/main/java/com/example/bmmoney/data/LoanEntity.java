package com.example.bmmoney.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * BANG KHOAN VAY - dong tieu de cua mot quan he cong no.
 *
 * <p>Ban cu khong co thuc the nay: mot khoan vay chi la mot dong giao dich loai
 * LEND / BORROW, con cac lan tra bot la nhung dong REPAY / COLLECT tro ve bang
 * chuoi {@code loanId}. Muon biet con no bao nhieu thi phai quet ca bang roi cong
 * tay trong Java.</p>
 *
 * <p>Nay du no tinh bang mot cau JOIN - xem {@link LoanDao#BALANCE_COLUMNS}.</p>
 *
 * <h3>Ma khoan vay: vi sao khong dung so thu tu</h3>
 *
 * <p>Ban cu sinh ma bang {@code "L" + id} voi {@code id} la so thu tu dong vua chen.
 * Hai may cung tao khoan vay luc dang ngoai mang deu ra {@code L7}, va lan dong bo
 * sau se de khoan nay chong len khoan kia - mat du lieu that su, khong bao loi.</p>
 *
 * <p>{@link LoanDao#newLoanId()} ghep moc thoi gian voi phan ngau nhien nen hai may
 * khong the sinh trung.</p>
 */
@Entity(tableName = "loans",
        foreignKeys = @ForeignKey(entity = PartnerEntity.class,
                parentColumns = "id", childColumns = "partnerId",
                onDelete = ForeignKey.SET_NULL),
        indices = {
                @Index("partnerId"),
                @Index("direction"),
                @Index("dueDate"),
                @Index("updatedAt")
        })
public class LoanEntity {

    /** Minh cho nguoi ta vay, phat sinh phai thu. */
    public static final String LEND = "LEND";

    /** Minh di vay, phat sinh phai tra. */
    public static final String BORROW = "BORROW";

    @PrimaryKey
    @NonNull
    private String loanId = "";

    /** Tro toi {@code partners.id}. Null la chua gan doi tac. */
    private Integer partnerId;

    /** LEND hoac BORROW. */
    private String direction;

    /** So tien goc ban dau, don vi DONG. */
    private long principal;

    /** Lai suat, de danh cho ban co tinh lai. Hien luon null. */
    private Double rate;

    private long openedDate;

    /** Han tra / han doi, 0 la chua dat. */
    private long dueDate;

    private int settled;

    private int writtenOff;

    private long updatedAt;

    private int deleted;

    public LoanEntity() {
    }

    @Ignore
    public LoanEntity(@NonNull String loanId, String direction, long principal,
                      Integer partnerId, long openedDate, long dueDate) {
        this.loanId = loanId;
        this.direction = direction;
        this.principal = principal;
        this.partnerId = partnerId;
        this.openedDate = openedDate;
        this.dueDate = dueDate;
    }

    @NonNull
    public String getLoanId() { return loanId; }
    public void setLoanId(@NonNull String loanId) { this.loanId = loanId; }

    public Integer getPartnerId() { return partnerId; }
    public void setPartnerId(Integer partnerId) { this.partnerId = partnerId; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public long getPrincipal() { return principal; }
    public void setPrincipal(long principal) { this.principal = principal; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }

    public long getOpenedDate() { return openedDate; }
    public void setOpenedDate(long openedDate) { this.openedDate = openedDate; }

    public long getDueDate() { return dueDate; }
    public void setDueDate(long dueDate) { this.dueDate = dueDate; }

    public int getSettled() { return settled; }
    public void setSettled(int settled) { this.settled = settled; }

    public int getWrittenOff() { return writtenOff; }
    public void setWrittenOff(int writtenOff) { this.writtenOff = writtenOff; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public long dueMillis() {
        return dueDate;
    }

    public boolean isSettled() {
        return settled == 1;
    }

    public boolean isWrittenOff() {
        return writtenOff == 1;
    }

    /** Khoan nay lam phat sinh phai thu (minh cho vay). */
    public boolean isReceivable() {
        return LEND.equals(direction);
    }
}
