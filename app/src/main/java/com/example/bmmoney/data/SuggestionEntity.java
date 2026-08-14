package com.example.bmmoney.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Mot goi y chi tieu doc ra tu thong bao cua app khac.
 *
 * <p>Bang nay chi nam tren may. FirebaseSyncManager chi day bon nhom
 * tx / cats / people / loans, nen du lieu thong bao khong bao gio roi thiet bi.
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
     * Khoa chong trung. Cung mot thong bao ngan hang ban ve nhieu lan trong hai phut
     * chi luu mot dong duy nhat.
     */
    public String dedupeKey = "";

    public String packageName = "";

    public String appLabel = "";

    /** Noi dung thong bao, da che so tai khoan truoc khi luu. */
    public String rawText = "";

    public String title = "";

    public long amount;

    /** Luon la mot gia tri cua Stats, hien tai chi sinh ra EXPENSE hoac INCOME. */
    public String type = "EXPENSE";

    @Nullable
    public String categoryName;

    /** Thoi diem thong bao den, dung lam thoi gian giao dich. */
    public long date;

    public int status = PENDING;

    public long createdAt;

    /** 1 khi Gemini da tinh chinh lai tieu de va danh muc. */
    public int aiParsed;

    public SuggestionEntity() {
    }

    @Ignore
    public SuggestionEntity(String dedupeKey, String packageName, String appLabel, String rawText,
                            String title, long amount, String type, @Nullable String categoryName,
                            long date, long createdAt) {
        this.dedupeKey = dedupeKey;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.rawText = rawText;
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.categoryName = categoryName;
        this.date = date;
        this.status = PENDING;
        this.createdAt = createdAt;
        this.aiParsed = 0;
    }
}
