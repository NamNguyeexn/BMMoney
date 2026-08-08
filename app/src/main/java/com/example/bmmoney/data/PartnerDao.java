package com.example.bmmoney.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** Truy van bang doi tac cong no. */
@Dao
public interface PartnerDao {

    @Query("SELECT * FROM partners WHERE deleted = 0 ORDER BY name ASC")
    List<PartnerEntity> all();

    @Query("SELECT name FROM partners WHERE deleted = 0 ORDER BY name ASC")
    List<String> allNames();

    @Query("SELECT * FROM partners WHERE name = :name AND deleted = 0 LIMIT 1")
    PartnerEntity byName(String name);

    @Query("SELECT * FROM partners WHERE id = :id LIMIT 1")
    PartnerEntity byId(int id);

    @Query("SELECT COUNT(*) FROM partners WHERE deleted = 0")
    int count();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(PartnerEntity partner);

    @Update
    void update(PartnerEntity partner);

    /**
     * Lay id doi tac theo ten, TU TAO neu chua co.
     *
     * <p>Duong duy nhat de tao doi tac. Day la thu chan loi "Anh Nam" va "anh nam"
     * thanh hai nguoi: UNIQUE INDEX chan o tang co so du lieu, con ham nay bao dam
     * moi man hinh deu di qua cung mot cua.</p>
     */
    default Integer ensure(String name) {
        if (name == null) return null;
        String clean = name.trim();
        if (clean.isEmpty()) return null;

        PartnerEntity found = byName(clean);
        if (found != null) return found.getId();

        PartnerEntity row = new PartnerEntity(clean);
        row.touch();
        long id = insertIgnore(row);
        if (id > 0) return (int) id;

        PartnerEntity again = byName(clean);
        return again == null ? null : again.getId();
    }

    @Query("UPDATE partners SET deleted = 1, updatedAt = :now WHERE id = :id")
    void softDelete(int id, long now);

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND partnerId = :id")
    int usageCount(int id);

    /**
     * SO DU CONG NO TUNG DOI TAC, tinh tron trong SQLite.
     *
     * <pre>
     * receivable = LEND   - COLLECT   (ho dang no minh)
     * payable    = BORROW - REPAY     (minh dang no ho)
     * nextDue    = han gan nhat con treo
     * </pre>
     *
     * <p>Chi tra ve doi tac con du no khac 0.</p>
     */
    @Query("SELECT p.id AS partnerId, p.name AS person, "
            + "COALESCE(SUM(CASE t.type WHEN 'LEND' THEN t.amount "
            + "                          WHEN 'COLLECT' THEN -t.amount "
            + "                          ELSE 0 END), 0) AS receivable, "
            + "COALESCE(SUM(CASE t.type WHEN 'BORROW' THEN t.amount "
            + "                          WHEN 'REPAY' THEN -t.amount "
            + "                          ELSE 0 END), 0) AS payable, "
            + "COALESCE(MIN(CASE WHEN t.dueDate > 0 AND t.settled = 0 AND t.writtenOff = 0 "
            + "                  THEN t.dueDate END), 0) AS nextDue "
            + "FROM partners p "
            + "LEFT JOIN transactions t ON t.partnerId = p.id AND t.deleted = 0 "
            + "WHERE p.deleted = 0 "
            + "GROUP BY p.id "
            + "HAVING receivable <> 0 OR payable <> 0 "
            + "ORDER BY (receivable - payable) DESC")
    List<PartnerBalance> balances();

    @Query("SELECT * FROM partners WHERE updatedAt > :since")
    List<PartnerEntity> changedSince(long since);

    @Query("SELECT * FROM partners")
    List<PartnerEntity> getAllForSync();

    @Query("DELETE FROM partners")
    void wipe();
}
