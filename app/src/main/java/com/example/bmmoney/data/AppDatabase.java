package com.example.bmmoney.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {TransactionEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract TransactionDao transactionDao();

    /**
     * Ban va 02/08: them ba cot cho phan cong no.
     *
     * <p>RAT QUAN TRONG: phai co migration that su. Neu chi tang version va de
     * fallbackToDestructiveMigration() lo, Room se XOA TRANG bang giao dich cua
     * nguoi dung khi mo app lan dau sau khi cap nhat.</p>
     *
     * <p>Ba cot deu de NULLABLE (khong NOT NULL, khong DEFAULT) de khop chinh xac
     * voi kieu String / Long / Integer trong TransactionEntity.</p>
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN person TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN dueDate INTEGER");
            db.execSQL("ALTER TABLE transactions ADD COLUMN settled INTEGER");
        }
    };

    /**
     * Ban va 03/08: chuyen sang nghiep vu ke toan day du.
     *
     * <ol>
     *   <li>Them cot loanId de noi khoan tra no / thu no ve dung khoan vay goc.</li>
     *   <li>Them cot writtenOff cho khoan no xoa so.</li>
     *   <li>Them san rate va principalOrInterest cho ban co tinh lai sau nay,
     *       hien de null hoan toan.</li>
     *   <li>Doi loai DEBT (ten cu, nghia la "no phai tra") thanh BORROW.</li>
     *   <li>Sinh loanId cho moi khoan BORROW / LEND cu de bao cao cong no chay dung.</li>
     * </ol>
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN loanId TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN writtenOff INTEGER");
            db.execSQL("ALTER TABLE transactions ADD COLUMN rate REAL");
            db.execSQL("ALTER TABLE transactions ADD COLUMN principalOrInterest TEXT");
            db.execSQL("UPDATE transactions SET type = 'BORROW' WHERE type = 'DEBT'");
            db.execSQL("UPDATE transactions SET loanId = 'L' || id "
                    + "WHERE type IN ('BORROW', 'LEND') AND loanId IS NULL");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    // Khong con fallbackToDestructiveMigration(): moi buoc nang cap
                    // deu co migration that su nen du lieu nguoi dung khong bi xoa.
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "expense_manager_db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }

    public static TransactionDao dao(Context context) {
        return getInstance(context).transactionDao();
    }
}
