package com.example.bmmoney.remote;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Prefs;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dong bo du lieu len Firestore theo tai khoan Google dang dang nhap.
 *
 * <p>Cau truc: users/{googleUid}
 * - transactions/{date_localId}: tung ban ghi thu chi
 * - settings/preferences: cac tham so trong man Cai dat (khong gom mau sac / giao dien)</p>
 *
 * <p>QUY TAC QUAN TRONG: moi truy van Room deu phai chay trong Db.io(...).
 * Cac callback cua Firestore chay tren luong giao dien, neu goi thang Room
 * o do thi Room se nem IllegalStateException va app tat ngay.
 * Moi callback "done" tra ve cho ben goi luon chay tren luong giao dien.</p>
 */
public class FirebaseSyncManager {

    private final Context context;
    private final AppDatabase db;
    private final FirebaseFirestore firestore;

    public FirebaseSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    // ------------------------------------------------------------- tai khoan
    @Nullable
    public static FirebaseUser currentUser() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isSignedIn() {
        return currentUser() != null;
    }

    @Nullable
    public static String uid() {
        FirebaseUser user = currentUser();
        return user == null ? null : user.getUid();
    }

    @Nullable
    public static String email() {
        FirebaseUser user = currentUser();
        return user == null ? null : user.getEmail();
    }

    @Nullable
    public static String displayName() {
        FirebaseUser user = currentUser();
        return user == null ? null : user.getDisplayName();
    }

    @Nullable
    private DocumentReference rootRef() {
        String id = uid();
        if (id == null) return null;
        return firestore.collection("users").document(id);
    }

    @Nullable
    private CollectionReference transactionsRef() {
        DocumentReference root = rootRef();
        return root == null ? null : root.collection("transactions");
    }

    @Nullable
    private DocumentReference settingsRef() {
        DocumentReference root = rootRef();
        return root == null ? null : root.collection("settings").document("preferences");
    }

    private static String docId(TransactionEntity t) {
        return t.getDate() + "_" + t.getId();
    }

    /** Chay callback tren luong giao dien, bo qua neu khong co. */
    private static void finish(@Nullable Runnable done) {
        if (done != null) Db.ui(done);
    }

    /** Luu ho so tai khoan de de nhan dien khi xem tren Firebase Console. */
    public void saveAccountProfile() {
        DocumentReference root = rootRef();
        if (root == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("email", email());
        data.put("displayName", displayName());
        data.put("lastSeenAt", System.currentTimeMillis());
        root.set(data, SetOptions.merge());
    }

    // ------------------------------------------------------------- giao dich
    public void uploadTransaction(TransactionEntity t) {
        CollectionReference ref = transactionsRef();
        if (ref == null || t == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("localId", t.getId());
        data.put("title", t.getTitle());
        data.put("amount", t.getAmount());
        data.put("type", t.getType());
        data.put("category", t.getCategory());
        data.put("note", t.getNote());
        data.put("date", t.getDate());
        data.put("updatedAt", System.currentTimeMillis());
        ref.document(docId(t)).set(data);
    }

    /** Xoa ban ghi tren cloud khi nguoi dung xoa o man Tim kiem. */
    public void deleteTransaction(TransactionEntity t) {
        CollectionReference ref = transactionsRef();
        if (ref == null || t == null) return;
        ref.document(docId(t)).delete();
    }

    /** Day toan bo du lieu may len cloud. Goi tu bat ky luong nao cung an toan. */
    public void uploadAllLocal() {
        uploadAllLocal(null);
    }

    public void uploadAllLocal(@Nullable Runnable done) {
        if (!isSignedIn()) {
            finish(done);
            return;
        }
        Db.io(() -> {
            uploadAllLocalNow();
            finish(done);
        });
    }

    /** Phai duoc goi tren luong nen (co doc Room). */
    private void uploadAllLocalNow() {
        if (!isSignedIn()) return;
        try {
            saveAccountProfile();
            List<TransactionEntity> all = db.transactionDao().getAllTransactions();
            if (all != null) {
                for (TransactionEntity t : all) uploadTransaction(t);
            }
            uploadSettings();
        } catch (Throwable ignored) {
            // mat mang hoac cloud tu choi -> giu nguyen du lieu duoi may
        }
    }

    // ------------------------------------------------------------- thiet lap app
    /** Day cac tham so trong man Cai dat len cloud (khong gom mau sac / giao dien). */
    public void uploadSettings() {
        DocumentReference ref = settingsRef();
        if (ref == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("userName", Prefs.userName(context));
        data.put("budget", Prefs.budget(context));
        data.put("cycleDay", Prefs.cycleDay(context));
        data.put("cycleMonth", Prefs.cycleMonth(context));
        data.put("warnPercent", Prefs.warnPercent(context));
        data.put("bigPercent", Prefs.bigPercent(context));
        data.put("categories", Prefs.categoriesRaw(context));
        data.put("reminders", Prefs.remindersRaw(context));
        data.put("updatedAt", System.currentTimeMillis());
        ref.set(data);
    }

    /** Keo thiet lap tu cloud ve may (dung khi doi may hoac cai lai app). */
    public void downloadSettings(@Nullable Runnable done) {
        DocumentReference ref = settingsRef();
        if (ref == null) {
            finish(done);
            return;
        }
        ref.get()
                .addOnSuccessListener(d -> {
                    try {
                        if (d != null && d.exists()) applySettings(d);
                    } catch (Throwable ignored) {
                    }
                    finish(done);
                })
                .addOnFailureListener(e -> finish(done));
    }

    private void applySettings(DocumentSnapshot d) {
        String name = d.getString("userName");
        if (name != null && !name.trim().isEmpty()) Prefs.setUserName(context, name);

        Double budget = d.getDouble("budget");
        if (budget != null && budget > 0) Prefs.setBudget(context, budget);

        Long cycleDay = d.getLong("cycleDay");
        Long cycleMonth = d.getLong("cycleMonth");
        if (cycleDay != null && cycleMonth != null) {
            Prefs.setCycle(context, cycleDay.intValue(), cycleMonth.intValue());
        }

        Long warn = d.getLong("warnPercent");
        if (warn != null) Prefs.setWarnPercent(context, warn.intValue());

        Long big = d.getLong("bigPercent");
        if (big != null) Prefs.setBigPercent(context, big.intValue());

        String categories = d.getString("categories");
        if (categories != null && !categories.isEmpty()) Prefs.setCategoriesRaw(context, categories);

        String reminders = d.getString("reminders");
        if (reminders != null) Prefs.setRemindersRaw(context, reminders);

        Prefs.setOnboarded(context, true);
    }

    // ------------------------------------------------------------- tai giao dich ve may
    public void downloadToLocal(@Nullable Runnable done) {
        CollectionReference ref = transactionsRef();
        if (ref == null) {
            finish(done);
            return;
        }
        ref.orderBy("date", Query.Direction.DESCENDING)
                .get()
                // Callback nay chay tren luong giao dien -> day toan bo phan doc/ghi Room sang Db.io
                .addOnSuccessListener(snapshot -> Db.io(() -> {
                    try {
                        mergeIntoLocal(snapshot.getDocuments());
                    } catch (Throwable ignored) {
                    }
                    finish(done);
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(context,
                            "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u online",
                            Toast.LENGTH_SHORT).show();
                    finish(done);
                });
    }

    /**
     * Gop du lieu cloud vao may: chi them ban ghi chua co (khoa = ngay|so tien|ten).
     * Phai chay tren luong nen.
     */
    private void mergeIntoLocal(List<DocumentSnapshot> documents) {
        Set<String> existing = new HashSet<>();
        List<TransactionEntity> local = db.transactionDao().getAllTransactions();
        if (local != null) {
            for (TransactionEntity t : local) {
                existing.add(key(t.getDate(), t.getAmount(), t.getTitle()));
            }
        }

        for (DocumentSnapshot d : documents) {
            String title = d.getString("title");
            Double amount = d.getDouble("amount");
            String type = d.getString("type");
            String category = d.getString("category");
            String note = d.getString("note");
            Long date = d.getLong("date");
            if (title == null || amount == null || type == null
                    || category == null || date == null) continue;

            String k = key(date, amount, title);
            if (existing.contains(k)) continue;
            existing.add(k);
            db.transactionDao().insert(new TransactionEntity(
                    title, amount, type, category, note == null ? "" : note, date));
        }
    }

    private static String key(long date, double amount, String title) {
        return date + "|" + Math.round(amount) + "|" + (title == null ? "" : title.trim());
    }

    /** Day het len roi keo het ve, dung cho nut "\u0110\u1ed3ng b\u1ed9 ngay". */
    public void syncAll(@Nullable Runnable done) {
        if (!isSignedIn()) {
            finish(done);
            return;
        }
        Db.io(() -> {
            uploadAllLocalNow();
            // Firestore phai duoc goi tu luong giao dien
            Db.ui(() -> downloadSettings(() -> downloadToLocal(done)));
        });
    }
}
