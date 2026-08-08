package com.example.bmmoney.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.bmmoney.util.TextNorm;

/**
 * BANG DOI TAC CONG NO.
 *
 * <p>Ban cu go thang ten nguoi vao cot {@code person} cua tung giao dich. Chi can go
 * "Anh Nam" mot lan va "anh nam" lan sau la bao cao cong no tach thanh HAI nguoi
 * khac nhau - va khong co gi bao loi, chi la so du sai.</p>
 *
 * <p>Nay moi doi tac la mot dong duy nhat, UNIQUE INDEX tren {@code name} bao dam
 * dieu do. Moi duong tao doi tac deu di qua {@link PartnerDao#ensure(String)} nen
 * khong con cho nao lot.</p>
 */
@Entity(tableName = "partners",
        indices = {
                @Index(value = {"name"}, unique = true),
                @Index("updatedAt")
        })
public class PartnerEntity {

    /** Nhan hien thi khi khoan cong no chua ghi ten ai. */
    public static final String UNKNOWN_LABEL = "Ch\u01b0a ghi t\u00ean";

    @PrimaryKey(autoGenerate = true)
    private int id;

    /** Ten doi tac, duy nhat trong bang. */
    private String name;

    /** Ten da bo dau, phuc vu tim kiem. */
    private String searchName;

    private String phone;

    private String note;

    private long updatedAt;

    private int deleted;

    public PartnerEntity() {
    }

    @Ignore
    public PartnerEntity(String name) {
        setName(name);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) {
        this.name = name;
        this.searchName = TextNorm.normalize(name);
    }

    public String getSearchName() { return searchName; }
    public void setSearchName(String searchName) { this.searchName = searchName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public String nameOrEmpty() {
        return name == null ? "" : name;
    }

    public String nameOrUnknown() {
        return name == null || name.trim().isEmpty() ? UNKNOWN_LABEL : name;
    }
}
