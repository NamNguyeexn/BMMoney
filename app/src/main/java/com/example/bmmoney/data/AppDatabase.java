package com.example.bmmoney.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {TransactionEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract TransactionDao transactionDao();

    /**
     * Ban va 02/08: them ba cot cho phan Vay no / Tra no.
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

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "expense_manager_db")
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()
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
