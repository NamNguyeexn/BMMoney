package com.example.bmmoney.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Mot goi y giao dich doc ra tu anh bien lai / anh lich su giao dich.
 *
 * <p>Bang nay chi nam tren may. FirebaseSyncManager chi day bon nhom
 * tx / cats / people / loans, nen goi y khong bao gio roi thiet bi.
 *
 * <p><b>Khong co cot nao tro toi anh.</b> App doc anh roi tra anh lai cho nguoi dung
 * quan ly; no khong sao, khong luu, khong tham chieu. Sau khi doc xong thi ba con so
 * ben duoi la tat ca nhung gi con lai.
 *
 * <p><b>Chi ba truong duoc may doc:</b> {@link #amount}, {@link #date} va
 * {@link #hasTime}. Ten khoan va danh muc chi la phong doan de nguoi dung do mat
 * cho de - ho se tu dien lai o man them giao dich.
 */
@Entity(tableName = "suggestions",
        indices = {@Index(value = {"dedupeKey"}, unique = true), @Index(value = {"status"})})
public class SuggestionEntity {

    /** Dang cho nguoi dung quyet dinh. */
    public static final int PENDING = 0;
    /** Nguoi dung da tao giao dich tu goi y nay. */
    public static final int CREATED = 1;
    /** Nguoi dung da bo goi y nay. */
    public static final int DISMISSED = 2;

    @PrimaryKey(autoGenerate = true)
    public int id;

    /**
     * Khoa chong trung. Khi anh co ma giao dich thi khoa chinh la ma do, nho vay
     * chia se cung mot bien lai hai lan chi sinh ra mot dong duy nhat - ke ca khi
     * hai lan chia se cach nhau nhieu ngay.
     */
    public String dedupeKey = "";

    /** Ma giao dich in tren bien lai, neu doc duoc. Vi du FT26246102383800. */
    @Nullable
    public String refCode;

    /** Nguon anh, hien tai la "Anh giao dich" hoac ten app da chia se. */
    public String sourceLabel = "";

    /**
     * Chu doc duoc tu anh, da che so tai khoan truoc khi luu.
     *
     * <p>Day la chu, khong phai anh. No duoc giu de nguoi dung doi chieu con so o man
     * them giao dich ma khong phai mo lai anh - va cung vi vay app khong can den anh.
     */
    public String rawText = "";

    public String title = "";

    public long amount;

    /** Luon la mot gia tri cua Stats, hien tai chi sinh ra EXPENSE hoac INCOME. */
    public String type = "EXPENSE";

    @Nullable
    public String categoryName;

    /** Thoi diem giao dich doc tu anh. */
    public long date;

    /**
     * 1 khi anh co ca gio, 0 khi chi doc duoc ngay.
     *
     * <p>Man "Hoat dong gan day" cua ngan hang khong in gio cho tung dong, chi co
     * tieu de ngay. Khi do {@link #date} la 00:00 cua ngay do va co nay bang 0, de
     * giao dien noi ro "chua co gio" thay vi khang dinh mot con so may tu bay ra.
     */
    public int hasTime;

    public int status = PENDING;

    public long createdAt;

    public SuggestionEntity() {
    }

    @Ignore
    public SuggestionEntity(String dedupeKey, @Nullable String refCode, String sourceLabel,
                            String rawText, String title, long amount,
                            String type, @Nullable String categoryName, long date, boolean hasTime,
                            long createdAt) {
        this.dedupeKey = dedupeKey;
        this.refCode = refCode;
        this.sourceLabel = sourceLabel;
        this.rawText = rawText;
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.categoryName = categoryName;
        this.date = date;
        this.hasTime = hasTime ? 1 : 0;
        this.status = PENDING;
        this.createdAt = createdAt;
    }
}
