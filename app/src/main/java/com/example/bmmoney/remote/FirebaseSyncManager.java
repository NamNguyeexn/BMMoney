package com.example.bmmoney.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Prefs;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p><b>Ban va 01/08:</b> ba loi khien man Tuy chon "treo" khi bam Sao luu /
 * Dong bo da duoc xu ly o day:</p>
 * <ol>
 *   <li><b>Khong bao gio bao ket qua.</b> Khi may mat mang hoac Firestore chua
 *       ket noi duoc, {@code WriteBatch.commit()} chi ghi vao hang doi duoi may;
 *       Task cua no KHONG bao gio hoan tat nen callback khong chay -> khong co
 *       thong bao thanh cong va ngay sao luu khong duoc cap nhat. Nay moi thao
 *       tac deu co dong ho canh (watchdog) va callback chi chay dung mot lan.</li>
 *   <li><b>"Failed to get document because the client is offline".</b> Do doc
 *       bang Source mac dinh trong khi cache chi nam trong RAM. Nay doc uu tien
 *       may chu, that bai moi lui ve cache, va bat mang lai truoc khi doc.</li>
 *   <li><b>Loi khong doc duoc.</b> Ma loi Firestore duoc dich sang tieng Viet
 *       de biet ngay la mat mang, het han dang nhap hay bi Rules chan.</li>
 * </ol>
 */
public class FirebaseSyncManager {

    /** Gioi han an toan cho moi manh du lieu (document Firestore toi da 1MB). */
    private static final int PART_SIZE = 300000;

    /** So document toi da duoc xoa trong mot lan don cloud kieu cu. */
    private static final int CLEAN_LIMIT = 400;

    /** Qua khoang nay ma may chu chua tra loi thi coi nhu that bai va bao nguoi dung. */
    private static final long TIMEOUT_MS = 20000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

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

    // ------------------------------------------------- ket qua chi bao dung mot lan
    /**
     * Boc callback cua man hinh lai: dam bao chi chay dung MOT lan va luon chay
     * tren luong giao dien. Truoc day mot lan sao luu that bai co the vua khong
     * bao gi, vua co nguy co bao hai lan khi vua timeout vua co phan hoi muon.
     */
    private static final class Once {
        private final AtomicBoolean fired = new AtomicBoolean(false);
        @Nullable
        private final Result target;
        @Nullable
        private Runnable watchdog;

        Once(@Nullable Result target) {
            this.target = target;
        }

        void arm(final String timeoutMessage) {
            watchdog = new Runnable() {
                @Override
                public void run() {
                    finish(false, 0, timeoutMessage);
                }
            };
            MAIN.postDelayed(watchdog, TIMEOUT_MS);
        }

        void finish(boolean ok, int count, @Nullable String error) {
            if (fired.getAndSet(true)) return;
            if (watchdog != null) MAIN.removeCallbacks(watchdog);
            if (target == null) return;
            Db.ui(new Runnable() {
                @Override
                public void run() {
                    target.onDone(ok, count, error);
                }
            });
        }

        boolean isDone() {
            return fired.get();
        }
    }

    // ------------------------------------------------------------- mang & loi
    /** Con mang hay khong. Bao som cho nguoi dung thay vi de Firestore treo im lang. */
    private boolean hasNetwork() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) return false;
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Firestore co the da tu tat mang sau mot lan loi; bat lai truoc moi thao tac. */
    private void wakeNetwork() {
        try {
            firestore.enableNetwork();
        } catch (Throwable ignored) {
        }
    }

    /** Dich loi cua Firestore sang cau tieng Viet de con biet duong xu ly. */
    private static String friendly(@Nullable Throwable t) {
        if (t == null) return "l\u1ed7i kh\u00f4ng r\u00f5";
        if (t instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) t).getCode();
            switch (code) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "kh\u00f4ng k\u1ebft n\u1ed1i \u0111\u01b0\u1ee3c m\u00e1y ch\u1ee7, ki\u1ec3m tra m\u1ea1ng nh\u00e9";
                case PERMISSION_DENIED:
                    return "t\u00e0i kho\u1ea3n ch\u01b0a c\u00f3 quy\u1ec1n ghi (xem Firestore Rules)";
                case UNAUTHENTICATED:
                    return "phi\u00ean \u0111\u0103ng nh\u1eadp \u0111\u00e3 h\u1ebft h\u1ea1n, \u0111\u0103ng nh\u1eadp l\u1ea1i nh\u00e9";
                case NOT_FOUND:
                    return "cloud ch\u01b0a c\u00f3 b\u1ea3n sao l\u01b0u n\u00e0o";
                case RESOURCE_EXHAUSTED:
                    return "d\u1ef1 \u00e1n Firebase \u0111\u00e3 h\u1ebft h\u1ea1n m\u1ee9c mi\u1ec5n ph\u00ed h\u00f4m nay";
                default:
                    break;
            }
        }
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) return "l\u1ed7i kh\u00f4ng r\u00f5";
        if (message.toLowerCase().contains("offline")) {
            return "m\u00e1y \u0111ang ngo\u1ea1i tuy\u1ebfn v\u1edbi Firestore, ki\u1ec3m tra m\u1ea1ng nh\u00e9";
        }
        return message;
    }

    /**
     * Doc mot document: uu tien ban tren may chu, that bai moi lui ve cache.
     * Nho vay khi may chu khong voi toi duoc ta van con du lieu de lam viec,
     * thay vi bao thang "failed to get document because the client is offline".
     */
    private void readDoc(final DocumentReference ref, final DocRead callback) {
        ref.get(Source.SERVER)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        callback.onRead(snapshot, null);
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(final Exception serverError) {
                        ref.get(Source.CACHE)
                                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(DocumentSnapshot snapshot) {
                                        if (snapshot != null && snapshot.exists()) {
                                            callback.onRead(snapshot, null);
                                        } else {
                                            callback.onRead(null, serverError);
                                        }
                                    }
                                })
                                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                                    @Override
                                    public void onFailure(Exception cacheError) {
                                        callback.onRead(null, serverError);
                                    }
                                });
                    }
                });
    }

    private interface DocRead {
        void onRead(@Nullable DocumentSnapshot snapshot, @Nullable Exception error);
    }

    // ------------------------------------------------------------- sao luu
    /** Ghi de ban sao luu tren cloud bang toan bo du lieu hien tai cua may. */
    public void backupNow(@Nullable Result result) {
        final Once once = new Once(result);
        final CollectionReference backup = backupRef();
        if (backup == null) {
            once.finish(false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp");
            return;
        }
        if (!hasNetwork()) {
            once.finish(false, 0, "m\u00e1y \u0111ang kh\u00f4ng c\u00f3 m\u1ea1ng");
            return;
        }
        wakeNetwork();
        once.arm("m\u00e1y ch\u1ee7 kh\u00f4ng ph\u1ea3n h\u1ed3i. D\u1eef li\u1ec7u \u0111\u00e3 \u0111\u01b0\u1ee3c x\u1ebfp h\u00e0ng "
                + "v\u00e0 s\u1ebd t\u1ef1 g\u1eedi l\u00ean khi m\u1ea1ng \u1ed5n \u0111\u1ecbnh");

        Db.io(new Runnable() {
            @Override
            public void run() {
                final List<TransactionEntity> all;
                final String payload;
                try {
                    List<TransactionEntity> list = db.transactionDao().getAllTransactions();
                    all = list == null ? new ArrayList<TransactionEntity>() : list;
                    payload = toJson(all);
                } catch (Throwable t) {
                    once.finish(false, 0, "kh\u00f4ng \u0111\u1ecdc \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u m\u00e1y");
                    return;
                }
                writeSnapshot(backup, all.size(), payload, once);
            }
        });
    }

    /** Doc so manh cua ban sao luu cu de xoa phan du, roi ghi ban moi trong mot batch. */
    private void writeSnapshot(final CollectionReference backup, final int count,
                               final String payload, final Once once) {
        readDoc(backup.document("latest"), new DocRead() {
            @Override
            public void onRead(@Nullable DocumentSnapshot old, @Nullable Exception error) {
                int oldParts = 0;
                if (old != null && old.exists()) {
                    Long value = old.getLong("parts");
                    if (value != null) oldParts = value.intValue();
                }
                commitSnapshot(backup, count, payload, oldParts, once);
            }
        });
    }

    private void commitSnapshot(CollectionReference backup, final int count, String payload,
                                int oldParts, final Once once) {
        List<String> parts = split(payload);
        final long now = System.currentTimeMillis();

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
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        // Chi khi may chu da nhan that su moi ghi nhan moc thoi gian
                        Prefs.setLastBackup(context, now);
                        saveAccountProfile();
                        cleanupLegacy();
                        once.finish(true, count, null);
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        once.finish(false, 0, friendly(e));
                    }
                });
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
            Db.ui(new Runnable() {
                @Override
                public void run() {
                    result.onDone(new Info(false, 0, 0, ""));
                }
            });
            return;
        }
        wakeNetwork();
        readDoc(backup.document("latest"), new DocRead() {
            @Override
            public void onRead(@Nullable final DocumentSnapshot d, @Nullable Exception error) {
                final Info info;
                if (d == null || !d.exists()) {
                    info = new Info(false, 0, 0, "");
                } else {
                    Long updatedAt = d.getLong("updatedAt");
                    Long count = d.getLong("count");
                    info = new Info(true,
                            updatedAt == null ? 0 : updatedAt,
                            count == null ? 0 : count.intValue(),
                            d.getString("device"));
                }
                Db.ui(new Runnable() {
                    @Override
                    public void run() {
                        result.onDone(info);
                    }
                });
            }
        });
    }

    /**
     * Xoa toan bo du lieu duoi may va thay bang ban sao luu cuoi cung tren cloud.
     * Day la ban duy nhat duoc coi la dung, ke ca khi no rong.
     */
    public void restoreLatest(@Nullable Result result) {
        final Once once = new Once(result);
        final CollectionReference backup = backupRef();
        if (backup == null) {
            once.finish(false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp");
            return;
        }
        if (!hasNetwork()) {
            once.finish(false, 0, "m\u00e1y \u0111ang kh\u00f4ng c\u00f3 m\u1ea1ng");
            return;
        }
        wakeNetwork();
        once.arm("m\u00e1y ch\u1ee7 kh\u00f4ng ph\u1ea3n h\u1ed3i, th\u1eed l\u1ea1i sau nh\u00e9");

        readDoc(backup.document("latest"), new DocRead() {
            @Override
            public void onRead(@Nullable DocumentSnapshot meta, @Nullable Exception error) {
                if (meta == null || !meta.exists()) {
                    once.finish(false, 0, error == null
                            ? "cloud ch\u01b0a c\u00f3 b\u1ea3n sao l\u01b0u n\u00e0o"
                            : friendly(error));
                    return;
                }
                applyRemoteSettings(meta);
                readParts(backup, meta, once);
            }
        });
    }

    /**
     * Truoc day ham nay tai CA collection backup roi do tim tung manh. Chi can
     * mot manh khong doc duoc la ca lan khoi phuc hong. Nay doc thang tung
     * document part_i theo dung so manh ghi trong "latest": it luot doc hon,
     * co the lui ve cache va bao ro manh nao thieu.
     */
    private void readParts(final CollectionReference backup, DocumentSnapshot meta,
                           final Once once) {
        Long parts = meta.getLong("parts");
        final int total = parts == null ? 0 : parts.intValue();
        if (total <= 0) {
            replaceLocal("[]", once);
            return;
        }

        final List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            tasks.add(backup.document("part_" + i).get());
        }

        Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<List<Task<?>>>() {
                    @Override
                    public void onSuccess(List<Task<?>> finished) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < tasks.size(); i++) {
                            Task<DocumentSnapshot> task = tasks.get(i);
                            if (!task.isSuccessful() || task.getResult() == null) {
                                once.finish(false, 0, "thi\u1ebfu m\u1ea3nh d\u1eef li\u1ec7u part_" + i
                                        + " tr\u00ean cloud (" + friendly(task.getException()) + ")");
                                return;
                            }
                            String data = task.getResult().getString("data");
                            if (data != null) sb.append(data);
                        }
                        replaceLocal(sb.toString(), once);
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        once.finish(false, 0, friendly(e));
                    }
                });
    }

    /** Thay toan bo bang giao dich duoi may bang du lieu vua tai ve. */
    private void replaceLocal(final String payload, final Once once) {
        Db.io(new Runnable() {
            @Override
            public void run() {
                try {
                    List<TransactionEntity> list = fromJson(payload);
                    db.transactionDao().deleteAll();
                    if (!list.isEmpty()) db.transactionDao().insertAll(list);
                    once.finish(true, list.size(), null);
                } catch (Throwable t) {
                    once.finish(false, 0, "d\u1eef li\u1ec7u sao l\u01b0u kh\u00f4ng \u0111\u1ecdc \u0111\u01b0\u1ee3c");
                }
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
