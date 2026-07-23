package com.example.bmmoney.data;

import android.content.Context;
import androidx.room.*;

@Database(entities = {TransactionEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;
    public abstract TransactionDao transactionDao();
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "expense_manager_db").allowMainThreadQueries().build();
        return instance;
    }
}
