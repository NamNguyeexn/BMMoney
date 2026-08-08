package com.example.bmmoney.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.bmmoney.util.TextNorm;

/**
 * BANG SU KIEN trung tam: mot ban ghi tai chinh.
 *
 * <h3>Khac gi ban cu</h3>
 *
 * <p>Ban cu la mot bang PHANG: ten danh muc va ten doi tac duoc chep thanh CHUOI
 * tren tung dong. Doi ten danh muc thi hang tram ban ghi cu van mang ten cu. Go
 * "Anh Nam" mot lan va "anh nam" lan sau la bao cao cong no tach thanh hai nguoi,
 * so du sai ma khong he bao loi.</p>
 *
 * <p>Nay bang chi giu KHOA SO tro toi ba bang chieu. Doi ten o mot cho la moi bao
 * cao doi theo, vi khong con ban sao nao de lech.</p>
 *
 * <h3>Ba quyet dinh dang chu y</h3>
 *
 * <ol>
 *   <li><b>{@code amount} la {@code long}, don vi DONG.</b> Ban cu dung
 *       {@code double}. Voi so nguyen thi {@code double} van chinh xac tuyet doi
 *       den 2^53, nen chua tung sai - nhung tien te khong co ly do gi de mang kieu
 *       thap phan, va {@code long} thi khong bao gio phai tu hoi dieu do.</li>
 *   <li><b>Cac cot trang thai la kieu nguyen thuy NOT NULL.</b> Ban cu dung
 *       {@code Integer} / {@code Long} boc, chi de migration khoi phai khai bao gia
 *       tri mac dinh. Hau qua la moi cau truy van deu phai boc {@code IFNULL(...)},
 *       va quen mot cho la dieu kien loc im lang tra ve sai. Khong con migration
 *       thi khong con ly do do.</li>
 *   <li><b>{@code searchText} luu san ban da bo dau.</b> Xem
 *       {@link TextNorm} - day la thu cho phep day TRON phan tim kiem xuong SQL.</li>
 * </ol>
 *
 * <h3>Khoa ngoai</h3>
 *
 * <p>Ca ba deu {@code ON DELETE SET NULL}: xoa mot danh muc hay mot doi tac thi
 * giao dich KHONG bien mat theo, chi bi go lien ket. Du lieu tien bac khong bao gio
 * duoc bay hoi vi mot thao tac o bang chieu.</p>
 */
@Entity(tableName = "transactions",
        foreignKeys = {
                @ForeignKey(entity = CategoryEntity.class,
                        parentColumns = "id", childColumns = "categoryId",
                        onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = PartnerEntity.class,
                        parentColumns = "id", childColumns = "partnerId",
                        onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = LoanEntity.class,
                        parentColumns = "loanId", childColumns = "loanId",
                        onDelete = ForeignKey.SET_NULL)
        },
        indices = {
                @Index("date"),
                @Index("type"),
                @Index("categoryId"),
                @Index("partnerId"),
                @Index("loanId"),
                @Index("dayKey"),
                @Index("monthKey"),
                @Index("yearKey"),
                @Index("updatedAt"),
                @Index("deleted"),
                @Index(value = {"type", "date"}),
                @Index(value = {"type", "monthKey"}),
                @Index(value = {"deleted", "date"})
        })
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;

    /** So tien, don vi DONG, luon khong am. */
    private long amount;

    /** EXPENSE / INCOME / LEND / BORROW / REPAY / COLLECT. */
    private String type;

    private String note;

    /** Moc thoi gian giao dich, mili giay. */
    private long date;

    // ------------------------------------------------------------ khoa ngoai

    /** Tro toi {@code categories.id}. Null la chua gan danh muc. */
    private Integer categoryId;

    /** Tro toi {@code partners.id}. Null la khong lien quan doi tac nao. */
    private Integer partnerId;

    /** Tro toi {@code loans.loanId}. Null la khong thuoc khoan vay nao. */
    private String loanId;

    // ------------------------------------------------------------- khoa ngay

    /** yyyyMMdd, vi du 20260807. Luon dung bo voi {@link #date}. */
    private int dayKey;

    /** yyyyMM, vi du 202608. */
    private int monthKey;

    /** yyyy, vi du 2026. */
    private int yearKey;

    // ----------------------------------------------------------- cong no

    /** Han doi / han tra, 0 la chua dat. */
    private long dueDate;

    /** 1 la da danh dau tat toan thu cong. */
    private int settled;

    /** 1 la da xoa so, khong doi duoc nua. */
    private int writtenOff;

    /** Lai suat, de danh cho ban co tinh lai. Hien luon null. */
    private Double rate;

    // ------------------------------------------------------ tim kiem & dong bo

    /** Tieu de + ghi chu, da bo dau va viet thuong. Xem {@link TextNorm}. */
    private String searchText;

    /** Moc doi lan cuoi, phuc vu dong bo tang dan. */
    private long updatedAt;

    /** 1 la da xoa. Xoa mem de may khac biet ma xoa theo khi dong bo. */
    private int deleted;

    public TransactionEntity() {
    }

    /** Ham dung goi tat cho cac man hinh: tu dong dong khoa ngay va chuoi tim kiem. */
    @Ignore
    public TransactionEntity(String title, long amount, String type,
                             Integer categoryId, String note, long date) {
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.note = note;
        setDate(date);
        stampSearch();
    }

    // ------------------------------------------------------------------ doc / ghi

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }

    public void setTitle(String title) {
        this.title = title;
        stampSearch();
    }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getNote() { return note; }

    public void setNote(String note) {
        this.note = note;
        stampSearch();
    }

    public long getDate() { return date; }

    /**
     * Doi ngay giao dich.
     *
     * <p>Ba khoa ngay duoc tinh lai NGAY tai day. Day la duong DUY NHAT ghi duoc cot
     * {@code date}, nen khong ton tai ban ghi nao co khoa ngay lech voi ngay that -
     * ban cu phai co mot ham don dep chay luc mo app chi de vet nhung dong bi lech.</p>
     */
    public void setDate(long date) {
        this.date = date;
        if (date > 0) {
            this.dayKey = DateKeys.day(date);
            this.monthKey = DateKeys.month(date);
            this.yearKey = DateKeys.year(date);
        } else {
            this.dayKey = 0;
            this.monthKey = 0;
            this.yearKey = 0;
        }
    }

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public Integer getPartnerId() { return partnerId; }
    public void setPartnerId(Integer partnerId) { this.partnerId = partnerId; }

    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }

    public int getDayKey() { return dayKey; }
    public void setDayKey(int dayKey) { this.dayKey = dayKey; }

    public int getMonthKey() { return monthKey; }
    public void setMonthKey(int monthKey) { this.monthKey = monthKey; }

    public int getYearKey() { return yearKey; }
    public void setYearKey(int yearKey) { this.yearKey = yearKey; }

    public long getDueDate() { return dueDate; }
    public void setDueDate(long dueDate) { this.dueDate = dueDate; }

    public int getSettled() { return settled; }
    public void setSettled(int settled) { this.settled = settled; }

    public int getWrittenOff() { return writtenOff; }
    public void setWrittenOff(int writtenOff) { this.writtenOff = writtenOff; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }

    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }

    // ------------------------------------------------------------ tien ich

    /** Tinh lai chuoi tim kiem tu tieu de va ghi chu. */
    public void stampSearch() {
        this.searchText = TextNorm.join(title, note);
    }

    /** Dong dau moc sua doi. Goi truoc moi lan ghi de dong bo tang dan bat duoc. */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /** Han doi / han tra, 0 la chua dat. Giu ten cu de cac man hinh khong phai sua. */
    public long dueMillis() {
        return dueDate;
    }

    public boolean isSettled() {
        return settled == 1;
    }

    public boolean isWrittenOff() {
        return writtenOff == 1;
    }

    public boolean isDeleted() {
        return deleted == 1;
    }

    /** Ma khoan vay, tra ve chuoi rong neu chua dat. */
    public String loanIdOrEmpty() {
        return loanId == null ? "" : loanId;
    }
}
