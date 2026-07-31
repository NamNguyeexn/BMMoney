package com.example.bmmoney.remote;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.util.Prefs;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sao luu / khoi phuc du lieu theo mo hinh MOT BAN GHI DUY NHAT (snapshot).
 *
 * <p>Cau truc tren Firestore:</p>
 * <pre>
 * users/{uid}                       ho so tai khoan (email, ten, lan cuoi dung)
 * users/{uid}/backup/latest         ban sao luu moi nhat: thiet lap + so ban ghi + so manh
 * users/{uid}/backup/part_0..N      du lieu giao dich dang JSON, cat thanh manh
 * </pre>
 *
 * <p>Vi sao lam vay:</p>
 * <ul>
 *   <li>Moi lan sao luu chi GHI DE len dung cac document tren, khong bao gio
 *       sinh them ban ghi moi -> so document tren cloud luon co dinh, khong the tran.</li>
 *   <li>Khoi phuc = xoa sach du lieu duoi may roi nap nguyen ban sao luu cuoi cung,
 *       nen hai ben luon giong nhau tuyet doi, khong con canh gop nhap nhang.</li>
 * </ul>
 *
 * <p>Moi truy van Room deu chay trong Db.io va moi callback tra ve deu chay tren
 * luong giao dien.</p>
 */
public class FirebaseSyncManager {

    /** Gioi han an toan cho moi manh du lieu (document Firestore toi da 1MB). */
    private static final int PART_SIZE = 300000;

    /** So document toi da duoc xoa trong mot lan don cloud kieu cu. */
    private static final int CLEAN_LIMIT = 400;

    /** Ket qua cua mot lan sao luu hoac khoi phuc. */
    public interface Result {
        void onDone(boolean ok, int count, @Nullable String error);
    }

    /** Thong tin ban sao luu dang nam tren cloud. */
    public static class Info {
        public final boolean exists;
        public final long updatedAt;
        public final int count;
        public final String device;

        Info(boolean exists, long updatedAt, int count, String device) {
            this.exists = exists;
            this.updatedAt = updatedAt;
            this.count = count;
            this.device = device == null ? "" : device;
        }
    }

    public interface InfoResult {
        void onDone(Info info);
    }

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
    private CollectionReference backupRef() {
        DocumentReference root = rootRef();
        return root == null ? null : root.collection("backup");
    }

    private static void done(@Nullable Result result, boolean ok, int count, @Nullable String error) {
        if (result != null) Db.ui(() -> result.onDone(ok, count, error));
    }

    // ------------------------------------------------------------- sao luu
    /** Ghi de ban sao luu tren cloud bang toan bo du lieu hien tai cua may. */
    public void backupNow(@Nullable Result result) {
        final CollectionReference backup = backupRef();
        if (backup == null) {
            done(result, false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp");
            return;
        }
        Db.io(() -> {
            final List<TransactionEntity> all;
            final String payload;
            try {
                List<TransactionEntity> list = db.transactionDao().getAllTransactions();
                all = list == null ? new ArrayList<>() : list;
                payload = toJson(all);
            } catch (Throwable t) {
                done(result, false, 0, "kh\u00f4ng \u0111\u1ecdc \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u m\u00e1y");
                return;
            }
            writeSnapshot(backup, all.size(), payload, result);
        });
    }

    /** Doc so manh cua ban sao luu cu de xoa phan du, roi ghi ban moi trong mot batch. */
    private void writeSnapshot(CollectionReference backup, int count, String payload,
                               @Nullable Result result) {
        backup.document("latest").get()
                .addOnSuccessListener(old -> {
                    int oldParts = 0;
                    if (old != null && old.exists()) {
                        Long value = old.getLong("parts");
                        if (value != null) oldParts = value.intValue();
                    }
                    commitSnapshot(backup, count, payload, oldParts, result);
                })
                .addOnFailureListener(e -> commitSnapshot(backup, count, payload, 0, result));
    }

    private void commitSnapshot(CollectionReference backup, int count, String payload,
                               int oldParts, @Nullable Result result) {
        List<String> parts = split(payload);
        long now = System.currentTimeMillis();

        Map<String, Object> meta = new HashMap<>();
        meta.put("version", 2);
        meta.put("updatedAt", now);
        meta.put("count", count);
        meta.put("parts", parts.size());
        meta.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        meta.put("settings", settingsMap());

        WriteBatch batch = firestore.batch();
        batch.set(backup.document("latest"), meta);
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> chunk = new HashMap<>();
            chunk.put("data", parts.get(i));
            batch.set(backup.document("part_" + i), chunk);
        }
        // Xoa cac manh du thua tu lan sao luu truoc -> cloud khong phinh ra
        for (int i = parts.size(); i < oldParts; i++) {
            batch.delete(backup.document("part_" + i));
        }

        batch.commit()
                .addOnSuccessListener(v -> {
                    Prefs.setLastBackup(context, now);
                    saveAccountProfile();
                    cleanupLegacy();
                    done(result, true, count, null);
                })
                .addOnFailureListener(e -> done(result, false, 0, e.getMessage()));
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

    private Map<String, Object> settingsMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("userName", Prefs.userName(context));
        data.put("budget", Prefs.budget(context));
        data.put("cycleDay", Prefs.cycleDay(context));
        data.put("cycleMonth", Prefs.cycleMonth(context));
        data.put("warnPercent", Prefs.warnPercent(context));
        data.put("bigPercent", Prefs.bigPercent(context));
        data.put("categories", Prefs.categoriesRaw(context));
        data.put("reminders", Prefs.remindersRaw(context));
        return data;
    }

    // ------------------------------------------------------------- khoi phuc
    /** Doc thong tin ban sao luu tren cloud (khong thay doi gi duoi may). */
    public void loadInfo(final InfoResult result) {
        CollectionReference backup = backupRef();
        if (backup == null) {
            Db.ui(() -> result.onDone(new Info(false, 0, 0, "")));
            return;
        }
        backup.document("latest").get()
                .addOnSuccessListener(d -> {
                    if (d == null || !d.exists()) {
                        result.onDone(new Info(false, 0, 0, ""));
                        return;
                    }
                    Long updatedAt = d.getLong("updatedAt");
                    Long count = d.getLong("count");
                    result.onDone(new Info(true,
                            updatedAt == null ? 0 : updatedAt,
                            count == null ? 0 : count.intValue(),
                            d.getString("device")));
                })
                .addOnFailureListener(e -> result.onDone(new Info(false, 0, 0, "")));
    }

    /**
     * Xoa toan bo du lieu duoi may va thay bang ban sao luu cuoi cung tren cloud.
     * Day la ban duy nhat duoc coi la dung, ke ca khi no rong.
     */
    public void restoreLatest(@Nullable Result result) {
        CollectionReference backup = backupRef();
        if (backup == null) {
            done(result, false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp");
            return;
        }
        backup.document("latest").get()
                .addOnSuccessListener(meta -> {
                    if (meta == null || !meta.exists()) {
                        done(result, false, 0, "cloud ch\u01b0a c\u00f3 b\u1ea3n sao l\u01b0u n\u00e0o");
                        return;
                    }
                    applyRemoteSettings(meta);
                    readParts(backup, meta, result);
                })
                .addOnFailureListener(e -> done(result, false, 0, e.getMessage()));
    }

    private void readParts(CollectionReference backup, DocumentSnapshot meta,
                           @Nullable Result result) {
        backup.get()
                .addOnSuccessListener(snapshot -> {
                    Long parts = meta.getLong("parts");
                    int total = parts == null ? 0 : parts.intValue();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < total; i++) {
                        String data = findPart(snapshot, "part_" + i);
                        if (data != null) sb.append(data);
                    }
                    replaceLocal(sb.toString(), result);
                })
                .addOnFailureListener(e -> done(result, false, 0, e.getMessage()));
    }

    @Nullable
    private static String findPart(QuerySnapshot snapshot, String id) {
        for (DocumentSnapshot d : snapshot.getDocuments()) {
            if (id.equals(d.getId())) return d.getString("data");
        }
        return null;
    }

    /** Thay toan bo bang giao dich duoi may bang du lieu vua tai ve. */
    private void replaceLocal(String payload, @Nullable Result result) {
        Db.io(() -> {
            try {
                List<TransactionEntity> list = fromJson(payload);
                db.transactionDao().deleteAll();
                if (!list.isEmpty()) db.transactionDao().insertAll(list);
                done(result, true, list.size(), null);
            } catch (Throwable t) {
                done(result, false, 0, "d\u1eef li\u1ec7u sao l\u01b0u kh\u00f4ng \u0111\u1ecdc \u0111\u01b0\u1ee3c");
            }
        });
    }

    private void applyRemoteSettings(DocumentSnapshot meta) {
        Object raw = meta.get("settings");
        if (!(raw instanceof Map)) return;
        Map<?, ?> data = (Map<?, ?>) raw;

        String name = string(data.get("userName"));
        if (name != null && !name.trim().isEmpty()) Prefs.setUserName(context, name);

        Double budget = number(data.get("budget"));
        if (budget != null && budget > 0) Prefs.setBudget(context, budget);

        Double cycleDay = number(data.get("cycleDay"));
        Double cycleMonth = number(data.get("cycleMonth"));
        if (cycleDay != null && cycleMonth != null) {
            Prefs.setCycle(context, cycleDay.intValue(), cycleMonth.intValue());
        }

        Double warn = number(data.get("warnPercent"));
        if (warn != null) Prefs.setWarnPercent(context, warn.intValue());

        Double big = number(data.get("bigPercent"));
        if (big != null) Prefs.setBigPercent(context, big.intValue());

        String categories = string(data.get("categories"));
        if (categories != null && !categories.isEmpty()) Prefs.setCategoriesRaw(context, categories);

        String reminders = string(data.get("reminders"));
        if (reminders != null) Prefs.setRemindersRaw(context, reminders);

        Prefs.setOnboarded(context, true);
    }

    @Nullable
    private static String string(@Nullable Object value) {
        return value instanceof String ? (String) value : null;
    }

    @Nullable
    private static Double number(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    // ------------------------------------------------------------- don cloud kieu cu
    /**
     * Ban dau moi giao dich la mot document rieng trong users/{uid}/transactions.
     * Sau khi chuyen sang snapshot, cac document do khong con dung nen se duoc xoa dan
     * de khong chiem cho tren cloud.
     */
    public void cleanupLegacy() {
        if (Prefs.legacyCleaned(context)) return;
        DocumentReference root = rootRef();
        if (root == null) return;

        final CollectionReference legacy = root.collection("transactions");
        legacy.limit(CLEAN_LIMIT).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Prefs.setLegacyCleaned(context, true);
                        return;
                    }
                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        batch.delete(d.getReference());
                    }
                    batch.commit().addOnSuccessListener(v -> {
                        if (snapshot.size() < CLEAN_LIMIT) {
                            Prefs.setLegacyCleaned(context, true);
                        } else {
                            cleanupLegacy();
                        }
                    });
                });
    }

    // ------------------------------------------------------------- chuyen doi JSON
    private static String toJson(List<TransactionEntity> list) {
        JSONArray array = new JSONArray();
        for (TransactionEntity t : list) {
            JSONObject item = new JSONObject();
            try {
                item.put("t", t.getTitle());
                item.put("a", t.getAmount());
                item.put("y", t.getType());
                item.put("c", t.getCategory());
                item.put("n", t.getNote() == null ? "" : t.getNote());
                item.put("d", t.getDate());
            } catch (Throwable ignored) {
                continue;
            }
            array.put(item);
        }
        return array.toString();
    }

    private static List<TransactionEntity> fromJson(String payload) throws Exception {
        List<TransactionEntity> list = new ArrayList<>();
        if (payload == null || payload.trim().isEmpty()) return list;

        JSONArray array = new JSONArray(payload);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("t", "");
            String type = item.optString("y", "EXPENSE");
            String category = item.optString("c", "");
            long date = item.optLong("d", 0L);
            if (date <= 0) continue;
            list.add(new TransactionEntity(title, item.optDouble("a", 0d), type, category,
                    item.optString("n", ""), date));
        }
        return list;
    }

    private static List<String> split(String payload) {
        List<String> parts = new ArrayList<>();
        if (payload == null || payload.isEmpty()) {
            parts.add("[]");
            return parts;
        }
        for (int start = 0; start < payload.length(); start += PART_SIZE) {
            parts.add(payload.substring(start, Math.min(payload.length(), start + PART_SIZE)));
        }
        return parts;
    }
}
