package com.example.bmmoney.remote;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Prefs;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

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
 * <p>Neu chua dang nhap thi moi thao tac cloud deu bi bo qua.</p>
 */
public class FirebaseSyncManager {

    private final Context context;
    private final AppDatabase db;
    private final FirebaseFirestore firestore;

    public FirebaseSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
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

    private DocumentReference rootRef() {
        String id = uid();
        if (id == null) return null;
        return firestore.collection("users").document(id);
    }

    private CollectionReference transactionsRef() {
        DocumentReference root = rootRef();
        return root == null ? null : root.collection("transactions");
    }

    private DocumentReference settingsRef() {
        DocumentReference root = rootRef();
        return root == null ? null : root.collection("settings").document("preferences");
    }

    private static String docId(TransactionEntity t) {
        return t.getDate() + "_" + t.getId();
    }

    /** Luu ho so tai khoan de de nhan dien khi xem tren Firebase Console. */
    public void saveAccountProfile() {
        DocumentReference root = rootRef();
        if (root == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("email", email());
        data.put("displayName", displayName());
        data.put("lastSeenAt", System.currentTimeMillis());
        root.set(data, com.google.firebase.firestore.SetOptions.merge());
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

    public void uploadAllLocal() {
        if (!isSignedIn()) return;
        saveAccountProfile();
        List<TransactionEntity> all = db.transactionDao().getAllTransactions();
        for (TransactionEntity t : all) uploadTransaction(t);
        uploadSettings();
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
            if (done != null) done.run();
            return;
        }
        ref.get()
                .addOnSuccessListener(d -> {
                    if (d != null && d.exists()) applySettings(d);
                    if (done != null) done.run();
                })
                .addOnFailureListener(e -> {
                    if (done != null) done.run();
                });
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
            if (done != null) done.run();
            return;
        }
        ref.orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Gop du lieu: chi them ban ghi cloud chua co duoi may (khoa = ngay|so tien|ten)
                    Set<String> existing = new HashSet<>();
                    for (TransactionEntity t : db.transactionDao().getAllTransactions()) {
                        existing.add(key(t.getDate(), t.getAmount(), t.getTitle()));
                    }

                    for (DocumentSnapshot d : snapshot.getDocuments()) {
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
                    if (done != null) done.run();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u online", Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                });
    }

    private static String key(long date, double amount, String title) {
        return date + "|" + Math.round(amount) + "|" + (title == null ? "" : title.trim());
    }

    /** Day het len roi keo het ve, dung cho nut "\u0110\u1ed3ng b\u1ed9 ngay". */
    public void syncAll(@Nullable Runnable done) {
        if (!isSignedIn()) {
            if (done != null) done.run();
            return;
        }
        uploadAllLocal();
        downloadSettings(() -> downloadToLocal(done));
    }
}
