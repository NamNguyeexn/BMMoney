package com.example.bmmoney.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Mot ban ghi tai chinh.
 *
 * <p>Ban va 02/08 them ba cot phuc vu phan cong no. Ban va 03/08 them hai cot nua
 * de theo dung nghiep vu ke toan. Tat ca deu dung kieu boc (String / Long /
 * Integer) de cot SQLite la NULLABLE, nho vay migration chi can ALTER TABLE ADD
 * COLUMN ma khong phai khai bao default, tranh loi "Migration didn't properly
 * handle" cua Room.</p>
 *
 * <ul>
 *   <li>{@code person}     - ten doi tac cong no</li>
 *   <li>{@code dueDate}    - han doi / han phai tra, tinh bang mili giay</li>
 *   <li>{@code settled}    - 1 la da danh dau tat toan thu cong, 0 hoac null la con treo</li>
 *   <li>{@code loanId}     - ma khoan vay goc. BORROW / LEND tu sinh ma nay,
 *       REPAY / COLLECT tro ve dung ma do de biet dang tra cho khoan nao</li>
 *   <li>{@code writtenOff} - 1 la khoan no da xoa so (khong doi duoc nua)</li>
 * </ul>
 *
 * <p>Ban nay CHUA tinh lai. Hai cot {@code rate} va {@code principalOrInterest}
 * duoc them san de sau nay bat tinh lai ma khong phai nang version database
 * them lan nua; hien tai moi ban ghi deu de null.</p>
 */
@Entity(tableName = "transactions")
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true) private int id;
    private String title;
    private double amount;
    private String type;
    private String category;
    private String note;
    private long date;

    private String person;
    private Long dueDate;
    private Integer settled;

    private String loanId;
    private Integer writtenOff;

    /** De danh cho ban co tinh lai. Hien chua dung o bat ky man hinh nao. */
    private Double rate;
    /** De danh cho ban co tinh lai: "PRINCIPAL" hoac "INTEREST". Hien luon null. */
    private String principalOrInterest;

    public TransactionEntity(String title, double amount, String type, String category,
                             String note, long date) {
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.note = note;
        this.date = date;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getPerson() { return person; }
    public void setPerson(String person) { this.person = person; }

    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }

    public Integer getSettled() { return settled; }
    public void setSettled(Integer settled) { this.settled = settled; }

    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }

    public Integer getWrittenOff() { return writtenOff; }
    public void setWrittenOff(Integer writtenOff) { this.writtenOff = writtenOff; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }

    public String getPrincipalOrInterest() { return principalOrInterest; }
    public void setPrincipalOrInterest(String value) { this.principalOrInterest = value; }

    // ------------------------------------------------- tien ich doc cho de

    /** Han doi / han tra, tra ve 0 neu chua dat. */
    public long dueMillis() {
        return dueDate == null ? 0L : dueDate;
    }

    /** Da danh dau tat toan thu cong chua. */
    public boolean isSettled() {
        return settled != null && settled == 1;
    }

    /** Khoan no da xoa so chua. */
    public boolean isWrittenOff() {
        return writtenOff != null && writtenOff == 1;
    }

    /** Ten doi tac, tra ve chuoi rong neu chua dat. */
    public String personOrEmpty() {
        return person == null ? "" : person;
    }

    /** Ma khoan vay, tra ve chuoi rong neu chua dat. */
    public String loanIdOrEmpty() {
        return loanId == null ? "" : loanId;
    }
}
