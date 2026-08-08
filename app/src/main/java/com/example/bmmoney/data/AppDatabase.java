package com.example.bmmoney.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * CO SO DU LIEU.
 *
 * <h3>Vi sao lai la phien ban 1</h3>
 *
 * <p>Ban truoc mang ba buoc chuyen doi (1-&gt;2, 2-&gt;3, 3-&gt;4) va mot lop don dep
 * chay ngam moi lan mo app, chi de vet lai nhung du lieu di duong vong. Toan bo
 * gánh nang do ton tai vi mot gia dinh: da co nguoi dung that voi du lieu that
 * trong may.</p>
 *
 * <p>Gia dinh do khong dung o day - app chua phat hanh. Bo no di thi ba buoc chuyen
 * doi khong bao gio chay, hai cot ten dang chuoi khong con ly do ton tai, va lop
 * don dep cung bien mat theo. Day khong phai la cat bot cho gon, ma la bo di nhung
 * thu von chi sinh ra tu mot rang buoc khong co that.</p>
 *
 * <p><b>Doi lai:</b> ten tep da doi sang {@code bmmoney.db}. Du lieu trong ban cu
 * ({@code expense_manager_db}) se KHONG duoc doc. Neu can giu, hay khoi phuc tu ban
 * sao luu tren cloud sau khi cai ban moi.</p>
 *
 * <h3>Khoa ngoai duoc bat that</h3>
 *
 * <p>Room tu bat {@code PRAGMA foreign_keys = ON}. Nghia la khong the chen mot giao
 * dich tro toi khoan vay chua ton tai - loi se nem ra ngay luc ghi, thay vi am tham
 * tro thanh mot dong mo coi ma nhieu thang sau moi phat hien.</p>
 */
@Database(
        entities = {
                TransactionEntity.class,
                CategoryEntity.class,
                PartnerEntity.class,
                LoanEntity.class
        },
        version = 1,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** Danh muc rieng cho khoan thu / chi can bang so du. */
    public static final String CATEGORY_BALANCE = "C\u00e2n b\u1eb1ng s\u1ed1 d\u01b0";

    private static final String DB_NAME = "bmmoney.db";

    private static volatile AppDatabase instance;

    public abstract TransactionDao transactionDao();

    public abstract CategoryDao categoryDao();

    public abstract PartnerDao partnerDao();

    public abstract LoanDao loanDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            .addCallback(SEED)
                            .build();
                }
            }
        }
        return instance;
    }

    // ---------------------------------------------------------- loi goi tat

    public static TransactionDao dao(Context context) {
        return getInstance(context).transactionDao();
    }

    public static CategoryDao categories(Context context) {
        return getInstance(context).categoryDao();
    }

    public static PartnerDao partners(Context context) {
        return getInstance(context).partnerDao();
    }

    public static LoanDao loans(Context context) {
        return getInstance(context).loanDao();
    }

    // ------------------------------------------------------------- gieo mam

    /**
     * Nap danh muc mac dinh NGAY luc tao bang.
     *
     * <p>Chay dung mot lan trong doi cua tep co so du lieu, truoc khi bat ky man
     * hinh nao kip doc. Ban cu gieo mam tu SharedPreferences luc mo app, nen co mot
     * khoang thoi gian ngan man hinh nhin thay bang danh muc rong.</p>
     *
     * <p>Dung {@code execSQL} truc tiep vi cac DAO chua san sang trong lan goi nay.</p>
     */
    private static final Callback SEED = new Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            long now = System.currentTimeMillis();

            seed(db, "\u0102n u\u1ed1ng", "\uD83C\uDF5C", 1, 0, now);
            seed(db, "Di chuy\u1ec3n", "\uD83D\uDE97", 2, 0, now);
            seed(db, "H\u00f3a \u0111\u01a1n", "\uD83E\uDDFE", 3, 0, now);
            seed(db, "Mua s\u1eafm", "\uD83D\uDECD", 4, 0, now);
            seed(db, "Y t\u1ebf", "\uD83D\uDC8A", 5, 0, now);
            seed(db, "Gi\u1ea3i tr\u00ed", "\uD83C\uDFAC", 6, 0, now);

            // Danh muc ky thuat: an khoi o chon nhung phai co san, vi khoan can bang
            // so du duoc gan vao day de no khong bop meo bieu do chi tieu.
            seed(db, CATEGORY_BALANCE, "\u2696\uFE0F", 99, 1, now);
        }
    };

    /**
     * Chen mot danh muc mac dinh.
     *
     * <p>Cot {@code searchName} phai bo dau ngay tai day cho khop voi
     * {@code TextNorm}, neu khong thi go "an uong" se khong tim ra "\u0102n u\u1ed1ng".</p>
     */
    private static void seed(SupportSQLiteDatabase db, String name, String emoji,
                             int order, int archived, long now) {
        db.execSQL("INSERT OR IGNORE INTO categories "
                        + "(name, searchName, emoji, kind, sortOrder, archived, updatedAt, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                new Object[]{
                        name,
                        com.example.bmmoney.util.TextNorm.normalize(name),
                        emoji,
                        CategoryEntity.KIND_BOTH,
                        order,
                        archived,
                        now
                });
    }
}
