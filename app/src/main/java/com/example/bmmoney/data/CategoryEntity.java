package com.example.bmmoney.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.bmmoney.util.TextNorm;

/**
 * BANG DANH MUC.
 *
 * <p>Ban cu luu danh muc o HAI cho roi rac: mot chuoi trong SharedPreferences cho o
 * chon, va mot cot TEXT lap lai tren tung dong giao dich. Hai nguon nay khong co gi
 * rang buoc phai khop nhau, nen doi ten trong Cai dat thi cac ban ghi cu van mang
 * ten cu va bao cao bi tach lam hai dong.</p>
 *
 * <p>Nay danh muc la mot dong duy nhat co khoa so. Cot {@code name} co UNIQUE INDEX
 * nen khong the tao trung ten - dieu nay quan trong hon ve mat kien truc: no bien
 * "khong duoc trung ten" tu mot loi hua trong ma nguon thanh mot rang buoc ma co so
 * du lieu tu bao ve.</p>
 */
@Entity(tableName = "categories",
        indices = {
                @Index(value = {"name"}, unique = true),
                @Index("archived"),
                @Index("updatedAt")
        })
public class CategoryEntity {

    /** Dung duoc cho ca thu va chi. */
    public static final String KIND_BOTH = "BOTH";
    public static final String KIND_EXPENSE = "EXPENSE";
    public static final String KIND_INCOME = "INCOME";

    /** Nhan dung khi nguoi dung khong chon emoji. */
    public static final String FALLBACK_EMOJI = "\uD83C\uDFF7";

    @PrimaryKey(autoGenerate = true)
    private int id;

    /** Ten hien thi, duy nhat trong bang. */
    private String name;

    /** Ten da bo dau, phuc vu tim kiem. Xem {@link TextNorm}. */
    private String searchName;

    private String emoji;

    /** EXPENSE / INCOME / BOTH. */
    private String kind;

    /** Thu tu trong o chon, so nho len truoc. */
    private int sortOrder;

    /** 1 la an khoi o chon nhung VAN giu lich su giao dich cu. */
    private int archived;

    private long updatedAt;

    private int deleted;

    public CategoryEntity() {
    }

    @Ignore
    public CategoryEntity(String name, String emoji, int sortOrder) {
        setName(name);
        this.emoji = emoji;
        this.kind = KIND_BOTH;
        this.sortOrder = sortOrder;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }

    /** Doi ten va dong lai chuoi tim kiem trong cung mot buoc. */
    public void setName(String name) {
        this.name = name;
        this.searchName = TextNorm.normalize(name);
    }

    public String getSearchName() { return searchName; }
    public void setSearchName(String searchName) { this.searchName = searchName; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public int getArchived() { return archived; }
    public void setArchived(int archived) { this.archived = archived; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }

    // ------------------------------------------------------------ tien ich

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public String emojiOrTag() {
        return emoji == null || emoji.isEmpty() ? FALLBACK_EMOJI : emoji;
    }

    public String nameOrEmpty() {
        return name == null ? "" : name;
    }

    /** "emoji ten", dung cho o chon va the loc. */
    public String label() {
        return emojiOrTag() + " " + nameOrEmpty();
    }

    public boolean isArchived() {
        return archived == 1;
    }
}
