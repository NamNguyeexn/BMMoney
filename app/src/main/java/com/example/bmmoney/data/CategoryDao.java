package com.example.bmmoney.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** Truy van bang danh muc. */
@Dao
public interface CategoryDao {

    /** Danh muc con hien trong o chon. */
    @Query("SELECT * FROM categories WHERE deleted = 0 AND archived = 0 "
            + "ORDER BY sortOrder ASC, name ASC")
    List<CategoryEntity> active();

    /** Ke ca danh muc da an, dung cho man Cai dat. */
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sortOrder ASC, name ASC")
    List<CategoryEntity> all();

    @Query("SELECT * FROM categories WHERE name = :name AND deleted = 0 LIMIT 1")
    CategoryEntity byName(String name);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    CategoryEntity byId(int id);

    @Query("SELECT name FROM categories WHERE deleted = 0 AND archived = 0 "
            + "ORDER BY sortOrder ASC, name ASC")
    List<String> activeNames();

    @Query("SELECT COUNT(*) FROM categories WHERE deleted = 0")
    int count();

    /** Trung ten thi BO QUA - UNIQUE INDEX chan lai, khong nem loi ra ngoai. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(CategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllIgnore(List<CategoryEntity> categories);

    @Update
    void update(CategoryEntity category);

    /**
     * Lay id cua danh muc theo ten, TU TAO neu chua co.
     *
     * <p>Day la duong DUY NHAT ma cac man hinh duoc phep tao danh muc. Nho vay
     * khong con cho nao lot ra mot ban ghi trung ten - va cung khong con canh mot
     * giao dich mang ten danh muc khong ton tai trong bang.</p>
     *
     * <p>Tra ve {@code null} khi ten rong, de giao dich do khong gan danh muc nao.</p>
     */
    default Integer ensure(String name, String emoji) {
        if (name == null) return null;
        String clean = name.trim();
        if (clean.isEmpty()) return null;

        CategoryEntity found = byName(clean);
        if (found != null) return found.getId();

        CategoryEntity row = new CategoryEntity(clean, emoji, 100);
        row.touch();
        long id = insertIgnore(row);
        if (id > 0) return (int) id;

        // Chen truot vi ai do vua tao cung ten - doc lai la co.
        CategoryEntity again = byName(clean);
        return again == null ? null : again.getId();
    }

    /** An khoi o chon nhung GIU nguyen lich su giao dich. */
    @Query("UPDATE categories SET archived = :archived, updatedAt = :now WHERE id = :id")
    void setArchived(int id, int archived, long now);

    @Query("UPDATE categories SET sortOrder = :order, updatedAt = :now WHERE id = :id")
    void setOrder(int id, int order, long now);

    /**
     * Xoa mem.
     *
     * <p>Khoa ngoai dat {@code ON DELETE SET NULL}, nhung o day ta khong xoa that nen
     * giao dich cu VAN giu duoc lien ket va bao cao lich su khong bi thung.</p>
     */
    @Query("UPDATE categories SET deleted = 1, updatedAt = :now WHERE id = :id")
    void softDelete(int id, long now);

    /** Danh muc nay dang duoc bao nhieu giao dich dung - hoi truoc khi xoa. */
    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND categoryId = :id")
    int usageCount(int id);

    @Query("SELECT * FROM categories WHERE updatedAt > :since")
    List<CategoryEntity> changedSince(long since);

    @Query("SELECT * FROM categories")
    List<CategoryEntity> getAllForSync();

    @Query("DELETE FROM categories")
    void wipe();
}
