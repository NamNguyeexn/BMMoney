package com.example.bmmoney.data;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Truy van bang goi y. Moi ham deu phai goi trong Db.io, khong goi tren luong chinh.
 */
@Dao
public interface SuggestionDao {

    /** Tra ve -1 khi dedupeKey da ton tai, nho vay khong can kiem tra truoc. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(SuggestionEntity item);

    @Query("SELECT * FROM suggestions WHERE status = 0 ORDER BY date DESC, id DESC LIMIT :limit")
    List<SuggestionEntity> pending(int limit);

    @Query("SELECT COUNT(id) FROM suggestions WHERE status = 0")
    int pendingCount();

    @Nullable
    @Query("SELECT * FROM suggestions WHERE id = :id LIMIT 1")
    SuggestionEntity byId(int id);

    @Query("SELECT COUNT(id) FROM suggestions WHERE dedupeKey = :key")
    int countByKey(String key);

    @Query("UPDATE suggestions SET status = :status WHERE id = :id")
    void setStatus(int id, int status);

    @Query("UPDATE suggestions SET status = 2 WHERE status = 0")
    void dismissAllPending();

    /**
     * Ghi lai ket qua Gemini. Dieu kien status = 0 tranh viec AI tra loi cham
     * roi ghi de len goi y ma nguoi dung vua xoa hoac vua dung.
     */
    @Query("UPDATE suggestions SET title = :title, amount = :amount, type = :type,"
            + " categoryName = :category, aiParsed = 1 WHERE id = :id AND status = 0")
    void refine(int id, String title, long amount, String type, @Nullable String category);

    /** Don goi y qua cu, ke ca goi y da xoa, de bang khong phinh mai. */
    @Query("DELETE FROM suggestions WHERE createdAt < :before")
    void purgeOlderThan(long before);
}
