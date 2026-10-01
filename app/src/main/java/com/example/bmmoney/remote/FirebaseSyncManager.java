package com.example.bmmoney.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryEntity;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.LoanEntity;
import com.example.bmmoney.data.PartnerEntity;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.PaymentNote;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Reminders;
import com.example.bmmoney.util.Stats;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DONG BO v6 - Firestore theo khuon SQL.
 *
 * <pre>
 * accounts/{uid}                      ho so
 * accounts/{uid}/settings/app         cai dat
 * accounts/{uid}/meta/sync            moc dong bo (ghi SAU CUNG)
 * accounts/{uid}/categories/{uuid}    = bang categories
 * accounts/{uid}/people/{uuid}        = bang partners
 * accounts/{uid}/loans/{loanId}       = bang loans
 * accounts/{uid}/transactions/{uuid}  = bang transactions
 * </pre>
 *
 * <h3>Khac ban v5 o dau</h3>
 * <ul>
 *   <li><b>Lien ket bang ID</b> (categoryId / personId / loanId), khong con bang TEN.</li>
 *   <li><b>ID document co dinh</b>: UUIDv5(uid/bang/id cuc bo) - trung khop voi file
 *       nap bang script (convert.py), nen nap truoc hay day tu app deu ra cung document.
 *       Doc id tren cloud khong phu thuoc may nao ghi.</li>
 *   <li><b>Khong bao gio xoa sach bang duoi may.</b> Keo ve = GOP tung dong: dong nao
 *       co updatedAt moi hon thi thang. Xoa la xoa mem (deleted = true) nen lan truyen
 *       duoc qua dong bo.</li>
 *   <li><b>Moc day len nam DUOI MAY</b> (khong doc moc tu cloud), nen dong ho may va
 *       moc cloud lech nhau khong lam bo sot ban ghi.</li>
 *   <li>Loi may chu duoc bao NGUYEN VAN, khong con lui ve cache roi bao chung chung.</li>
 * </ul>
 *
 * <p><b>Gioi han:</b> ID dua tren id cuc bo nen thiet ke cho MOT may. Dung hai may
 * cung luc thi phai them cot cloudId vao Room (xem SCHEMA_V6.md).</p>
 *
 * <p>Log: {@code adb logcat -s BmmSync}</p>
 */
public class FirebaseSyncManager {

    private static final String TAG = "BmmSync";
    private static final int SCHEMA_VERSION = 6;
    private static final int BATCH_LIMIT = 400;
    private static final long STEP_TIMEOUT_S = 30L;

    /** Namespace UUIDv5 - PHAI giong convert.py. */
    private static final UUID NS = UUID.fromString("6f1c2d4e-8b7a-4c1e-9d3f-b3a0c5e7f901");

    private static final String ROOT = "accounts";
    private static final String C_CATS = "categories";
    private static final String C_PEOPLE = "people";
    private static final String C_LOANS = "loans";
    private static final String C_TX = "transactions";
    private static final String[] TABLES = {C_CATS, C_PEOPLE, C_LOANS, C_TX};

    private static final String SP = "bmm_sync_v6";
    private static final String K_LAST_PUSH = "lastPush";   // luu rieng theo uid: lastPush:{uid}

    // ------------------------------------------------------------- giao dien cu (giu nguyen)
    public interface Result {
        void onDone(boolean ok, int count, @Nullable String error);
    }

    public interface SyncResult {
        void onDone(boolean ok, int count, boolean pushed, @Nullable String error);
    }

    public interface InfoResult {
        void onDone(Info info);
    }

    public static class Info {
        public final boolean exists;
        public final long updatedAt;
        public final int count;
        public final String device;
        /** true = KHONG doc duoc may chu. Ten giu nguyen de man Cai dat khong phai sua. */
        public final boolean fromCache;
        /** Loi that tu may chu (null neu doc duoc). */
        @Nullable public final String error;

        Info(boolean exists, long updatedAt, int count, String device, boolean fromCache,
             @Nullable String error) {
            this.exists = exists;
            this.updatedAt = updatedAt;
            this.count = count;
            this.device = device == null ? "" : device;
            this.fromCache = fromCache;
            this.error = error;
        }

        static Info unreachable(@Nullable String error) {
            return new Info(false, 0, 0, "", true, error);
        }
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
        FirebaseUser u = currentUser();
        return u == null ? null : u.getUid();
    }

    @Nullable
    public static String email() {
        FirebaseUser u = currentUser();
        return u == null ? null : u.getEmail();
    }

    @Nullable
    public static String displayName() {
        FirebaseUser u = currentUser();
        return u == null ? null : u.getDisplayName();
    }

    // ------------------------------------------------------------- duong dan & ID
    private DocumentReference root(String uid) {
        return firestore.collection(ROOT).document(uid);
    }

    private CollectionReference col(String uid, String table) {
        return root(uid).collection(table);
    }

    private DocumentReference meta(String uid) {
        return root(uid).collection("meta").document("sync");
    }

    private DocumentReference settingsRef(String uid) {
        return root(uid).collection("settings").document("app");
    }

    /** UUIDv5 - trung tung byte voi uuid.uuid5 cua Python. */
    static String docId(String uid, String table, int localId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            ByteBuffer ns = ByteBuffer.allocate(16);
            ns.putLong(NS.getMostSignificantBits());
            ns.putLong(NS.getLeastSignificantBits());
            md.update(ns.array());
            md.update((uid + "/" + table + "/" + localId).getBytes(StandardCharsets.UTF_8));
            byte[] h = md.digest();
            h[6] &= 0x0f;
            h[6] |= 0x50;
            h[8] &= 0x3f;
            h[8] |= (byte) 0x80;
            long msb = 0, lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (h[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (h[i] & 0xff);
            return new UUID(msb, lsb).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SharedPreferences sp() {
        return context.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------- mang & loi
    private boolean hasNetwork() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Doi mot Task Firebase tren luong nen, co gioi han thoi gian. */
    private static <T> T await(Task<T> task) throws Exception {
        try {
            return Tasks.await(task, STEP_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : e;
        }
    }

    static String friendly(@Nullable Throwable t) {
        if (t == null) return "l\u1ed7i kh\u00f4ng r\u00f5";
        if (t instanceof TimeoutException) {
            return "m\u00e1y ch\u1ee7 kh\u00f4ng tr\u1ea3 l\u1eddi (qu\u00e1 " + STEP_TIMEOUT_S + " gi\u00e2y)";
        }
        String detail = t.getMessage() == null ? "" : t.getMessage();
        if (t instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) t).getCode()) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "kh\u00f4ng k\u1ebft n\u1ed1i \u0111\u01b0\u1ee3c m\u00e1y ch\u1ee7, ki\u1ec3m tra m\u1ea1ng nh\u00e9";
                case PERMISSION_DENIED:
                    if (detail.contains("has not been used") || detail.contains("SERVICE_DISABLED")
                            || detail.contains("is disabled")) {
                        return "d\u1ef1 \u00e1n Firebase ch\u01b0a b\u1eadt Cloud Firestore API";
                    }
                    return "Firestore Rules t\u1eeb ch\u1ed1i (PERMISSION_DENIED) - d\u00e1n l\u1ea1i firestore.rules v6 nh\u00e9";
                case UNAUTHENTICATED:
                    return "phi\u00ean \u0111\u0103ng nh\u1eadp \u0111\u00e3 h\u1ebft h\u1ea1n, \u0111\u0103ng nh\u1eadp l\u1ea1i nh\u00e9";
                case RESOURCE_EXHAUSTED:
                    return "d\u1ef1 \u00e1n Firebase \u0111\u00e3 h\u1ebft h\u1ea1n m\u1ee9c mi\u1ec5n ph\u00ed h\u00f4m nay";
                case FAILED_PRECONDITION:
                    return "Firestore ch\u01b0a s\u1eb5n s\u00e0ng: " + detail;
                default:
                    return ((FirebaseFirestoreException) t).getCode() + ": " + detail;
            }
        }
        return detail.isEmpty() ? t.getClass().getSimpleName() : detail;
    }

    // ------------------------------------------------------------- Room -> tai lieu
    private static Map<String, Object> doc(String id, long updatedAt, int deleted) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("updatedAt", updatedAt);
        m.put("deleted", deleted == 1);
        return m;
    }

    private Map<String, Object> mapOf(String uid, CategoryEntity c) {
        Map<String, Object> m = doc(docId(uid, C_CATS, c.getId()), c.getUpdatedAt(), c.getDeleted());
        m.put("name", c.getName());
        m.put("emoji", c.getEmoji());
        m.put("kind", c.getKind() == null ? CategoryEntity.KIND_BOTH : c.getKind());
        m.put("sortOrder", c.getSortOrder());
        m.put("archived", c.getArchived() == 1);
        m.put("legacyId", String.valueOf(c.getId()));
        return m;
    }

    private Map<String, Object> mapOf(String uid, PartnerEntity p) {
        Map<String, Object> m = doc(docId(uid, C_PEOPLE, p.getId()), p.getUpdatedAt(), p.getDeleted());
        m.put("name", p.getName());
        m.put("phone", p.getPhone());
        m.put("note", p.getNote());
        m.put("legacyId", String.valueOf(p.getId()));
        return m;
    }

    private Map<String, Object> mapOf(String uid, LoanEntity l) {
        Map<String, Object> m = doc(l.getLoanId(), l.getUpdatedAt(), l.getDeleted());
        m.put("personId", l.getPartnerId() == null ? null : docId(uid, C_PEOPLE, l.getPartnerId()));
        m.put("direction", l.getDirection());
        m.put("principal", l.getPrincipal());
        m.put("rate", l.getRate());
        m.put("openedAt", l.getOpenedDate());
        m.put("dueAt", l.getDueDate() > 0 ? l.getDueDate() : null);
        m.put("settled", l.getSettled() == 1);
        m.put("writtenOff", l.getWrittenOff() == 1);
        return m;
    }

    private Map<String, Object> mapOf(String uid, TransactionEntity t) {
        Map<String, Object> m = doc(docId(uid, C_TX, t.getId()), t.getUpdatedAt(), t.getDeleted());
        PaymentNote.Parts parts = PaymentNote.split(t.getNote());
        m.put("type", t.getType());
        m.put("amount", t.getAmount());
        m.put("occurredAt", t.getDate());
        m.put("title", t.getTitle() == null ? "" : t.getTitle());
        m.put("note", parts.text.isEmpty() ? null : parts.text);
        m.put("paymentMethod", parts.code());
        m.put("categoryId", t.getCategoryId() == null ? null : docId(uid, C_CATS, t.getCategoryId()));
        m.put("loanId", t.getLoanId());
        m.put("personId", t.getPartnerId() == null ? null : docId(uid, C_PEOPLE, t.getPartnerId()));
        m.put("legacyId", String.valueOf(t.getId()));
        return m;
    }

    private Map<String, Object> settingsMap() {
        Map<String, Object> s = new HashMap<>();
        s.put("userName", Prefs.userName(context));
        s.put("budget", Math.round(Prefs.budget(context)));
        s.put("cycleDay", Prefs.cycleDay(context));
        s.put("cycleMonth", Prefs.cycleMonth(context));
        s.put("warnPercent", Prefs.warnPercent(context));
        s.put("bigPercent", Prefs.bigPercent(context));
        s.put("strongAlarm", Prefs.strongAlarm(context));
        s.put("reminders", Prefs.remindersRaw(context));
        s.put("updatedAt", System.currentTimeMillis());
        return s;
    }

    // ============================================================= DAY LEN
    /** Day moi dong doi tu lan day truoc (ke ca dong da xoa mem). */
    public void backupNow(@Nullable final Result result) {
        final String uid = uid();
        if (uid == null) { done(result, false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp"); return; }
        if (!hasNetwork()) { done(result, false, 0, "m\u00e1y \u0111ang kh\u00f4ng c\u00f3 m\u1ea1ng"); return; }
        Db.io(() -> {
            try {
                int live = push(uid, true);
                done(result, true, live, null);
            } catch (Exception e) {
                Log.e(TAG, "backupNow that bai", e);
                done(result, false, 0, friendly(e));
            }
        });
    }

    /** Chay tren luong nen. Tra ve so giao dich chua xoa. */
    private int push(String uid) throws Exception {
        return push(uid, false);
    }

    /** full = true: đẩy lại toàn bộ (nút "Ghi đè"), bỏ qua mốc lastPush. */
    private int push(String uid, boolean full) throws Exception {
        final long startedAt = System.currentTimeMillis();
        final long since = full ? 0L : sp().getLong(K_LAST_PUSH + ":" + uid, 0L);
        Log.i(TAG, "push: uid=" + uid + (since <= 0 ? " TOAN BO" : " tu moc " + since));

        List<DocumentReference> refs = new ArrayList<>();
        List<Map<String, Object>> bodies = new ArrayList<>();

        for (CategoryEntity c : since <= 0 ? db.categoryDao().getAllForSync() : db.categoryDao().changedSince(since)) {
            Map<String, Object> m = mapOf(uid, c);
            refs.add(col(uid, C_CATS).document((String) m.get("id")));
            bodies.add(m);
        }
        for (PartnerEntity p : since <= 0 ? db.partnerDao().getAllForSync() : db.partnerDao().changedSince(since)) {
            Map<String, Object> m = mapOf(uid, p);
            refs.add(col(uid, C_PEOPLE).document((String) m.get("id")));
            bodies.add(m);
        }
        for (LoanEntity l : since <= 0 ? db.loanDao().getAllForSync() : db.loanDao().changedSince(since)) {
            if (l.getLoanId() == null || l.getLoanId().trim().isEmpty()) continue;
            refs.add(col(uid, C_LOANS).document(l.getLoanId()));
            bodies.add(mapOf(uid, l));
        }
        for (TransactionEntity t : since <= 0 ? db.transactionDao().getAllForSync() : db.transactionDao().changedSince(since)) {
            Map<String, Object> m = mapOf(uid, t);
            refs.add(col(uid, C_TX).document((String) m.get("id")));
            bodies.add(m);
        }
        int live = db.transactionDao().count();

        // Du lieu truoc, meta sau cung: co meta moi nghia la du lieu da len du.
        for (int i = 0; i < refs.size(); i += BATCH_LIMIT) {
            WriteBatch batch = firestore.batch();
            for (int j = i; j < Math.min(i + BATCH_LIMIT, refs.size()); j++) {
                batch.set(refs.get(j), bodies.get(j));
            }
            await(batch.commit());
        }

        Map<String, Object> account = new HashMap<>();
        account.put("email", email());
        account.put("displayName", displayName());
        account.put("lastSeenAt", System.currentTimeMillis());
        WriteBatch tail = firestore.batch();
        tail.set(root(uid), account, SetOptions.merge());
        tail.set(settingsRef(uid), settingsMap());
        long now = System.currentTimeMillis();
        Map<String, Object> head = new HashMap<>();
        head.put("schemaVersion", SCHEMA_VERSION);
        head.put("updatedAt", now);
        head.put("count", live);
        head.put("device", Build.MODEL);
        tail.set(meta(uid), head, SetOptions.merge());
        await(tail.commit());

        sp().edit().putLong(K_LAST_PUSH + ":" + uid, startedAt).apply();
        Prefs.setLastBackup(context, now);
        Prefs.setLocalChangedAt(context, now);
        Log.i(TAG, "push: xong " + refs.size() + " dong, cloud giu " + live + " giao dich");
        return live;
    }

    // ============================================================= KEO VE (gop)
    /** Keo toan bo cloud ve va GOP vao may. Khong xoa gi duoi may. */
    public void restoreLatest(@Nullable final Result result) {
        final String uid = uid();
        if (uid == null) { done(result, false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp"); return; }
        if (!hasNetwork()) { done(result, false, 0, "m\u00e1y \u0111ang kh\u00f4ng c\u00f3 m\u1ea1ng"); return; }
        Db.io(() -> {
            try {
                int applied = pull(uid);
                done(result, true, applied, null);
            } catch (Exception e) {
                Log.e(TAG, "restoreLatest that bai", e);
                done(result, false, 0, friendly(e));
            }
        });
    }

    private List<DocumentSnapshot> readAll(String uid, String table) throws Exception {
        QuerySnapshot snap = await(col(uid, table).get(Source.SERVER));
        return snap == null ? new ArrayList<DocumentSnapshot>() : snap.getDocuments();
    }

    /** Chay tren luong nen. Tra ve so dong da ghi vao may. */
    private int pull(final String uid) throws Exception {
        DocumentSnapshot head = await(meta(uid).get(Source.SERVER));
        if (head == null || !head.exists()) {
            Log.i(TAG, "pull: cloud v6 chua co du lieu");
            return 0;
        }
        final DocumentSnapshot settings = await(settingsRef(uid).get(Source.SERVER));
        final List<DocumentSnapshot> cats = readAll(uid, C_CATS);
        final List<DocumentSnapshot> people = readAll(uid, C_PEOPLE);
        final List<DocumentSnapshot> loans = readAll(uid, C_LOANS);
        final List<DocumentSnapshot> txs = readAll(uid, C_TX);
        Log.i(TAG, "pull: " + cats.size() + " danh muc, " + people.size() + " nguoi, "
                + loans.size() + " khoan vay, " + txs.size() + " giao dich");

        final int[] applied = {0};
        db.runInTransaction(() -> {
            Map<String, Integer> catMap = new HashMap<>();
            Map<String, Integer> personMap = new HashMap<>();

            for (DocumentSnapshot d : cats) {
                int localId = legacyId(d);
                CategoryEntity c = localId > 0 ? db.categoryDao().byId(localId) : null;
                String name = d.getString("name");
                if (name == null) continue;
                if (c != null && c.getUpdatedAt() >= lng(d, "updatedAt")) {
                    catMap.put(d.getId(), c.getId());
                    continue;
                }
                boolean isNew = c == null;
                if (isNew) {
                    CategoryEntity same = db.categoryDao().byName(name);
                    if (same != null && localId <= 0) { catMap.put(d.getId(), same.getId()); continue; }
                    c = new CategoryEntity();
                    if (localId > 0) c.setId(localId);
                }
                c.setName(name);
                c.setEmoji(d.getString("emoji") == null ? CategoryEntity.FALLBACK_EMOJI : d.getString("emoji"));
                c.setKind(d.getString("kind") == null ? CategoryEntity.KIND_BOTH : d.getString("kind"));
                c.setSortOrder((int) lng(d, "sortOrder"));
                c.setArchived(bool(d, "archived") ? 1 : 0);
                c.setDeleted(bool(d, "deleted") ? 1 : 0);
                c.setUpdatedAt(lng(d, "updatedAt"));
                if (isNew) {
                    long id = db.categoryDao().insertIgnore(c);
                    if (id <= 0) {                       // trung ten voi mot danh muc khac
                        CategoryEntity same = db.categoryDao().byName(name);
                        if (same != null) catMap.put(d.getId(), same.getId());
                        continue;
                    }
                    catMap.put(d.getId(), (int) id);
                } else {
                    db.categoryDao().update(c);
                    catMap.put(d.getId(), c.getId());
                }
                applied[0]++;
            }

            for (DocumentSnapshot d : people) {
                int localId = legacyId(d);
                PartnerEntity p = localId > 0 ? db.partnerDao().byId(localId) : null;
                String name = d.getString("name");
                if (name == null) continue;
                if (p != null && p.getUpdatedAt() >= lng(d, "updatedAt")) {
                    personMap.put(d.getId(), p.getId());
                    continue;
                }
                boolean isNew = p == null;
                if (isNew) {
                    PartnerEntity same = db.partnerDao().byName(name);
                    if (same != null && localId <= 0) { personMap.put(d.getId(), same.getId()); continue; }
                    p = new PartnerEntity();
                    if (localId > 0) p.setId(localId);
                }
                p.setName(name);
                p.setPhone(d.getString("phone"));
                p.setNote(d.getString("note"));
                p.setDeleted(bool(d, "deleted") ? 1 : 0);
                p.setUpdatedAt(lng(d, "updatedAt"));
                if (isNew) {
                    long id = db.partnerDao().insertIgnore(p);
                    if (id <= 0) {
                        PartnerEntity same = db.partnerDao().byName(name);
                        if (same != null) personMap.put(d.getId(), same.getId());
                        continue;
                    }
                    personMap.put(d.getId(), (int) id);
                } else {
                    db.partnerDao().update(p);
                    personMap.put(d.getId(), p.getId());
                }
                applied[0]++;
            }

            for (DocumentSnapshot d : loans) {
                String loanId = d.getId();
                LoanEntity l = db.loanDao().byId(loanId);
                if (l != null && l.getUpdatedAt() >= lng(d, "updatedAt")) continue;
                boolean isNew = l == null;
                if (isNew) {
                    l = new LoanEntity();
                    l.setLoanId(loanId);
                }
                String personId = d.getString("personId");
                l.setPartnerId(personId == null ? null : personMap.get(personId));
                l.setDirection(d.getString("direction") == null ? LoanEntity.LEND : d.getString("direction"));
                l.setPrincipal(lng(d, "principal"));
                l.setRate(dbl(d, "rate"));
                l.setOpenedDate(lng(d, "openedAt"));
                l.setDueDate(lng(d, "dueAt"));
                l.setSettled(bool(d, "settled") ? 1 : 0);
                l.setWrittenOff(bool(d, "writtenOff") ? 1 : 0);
                l.setDeleted(bool(d, "deleted") ? 1 : 0);
                l.setUpdatedAt(lng(d, "updatedAt"));
                if (isNew) db.loanDao().insert(l); else db.loanDao().update(l);
                applied[0]++;
            }

            for (DocumentSnapshot d : txs) {
                int localId = legacyId(d);
                TransactionEntity t = localId > 0 ? db.transactionDao().rawById(localId) : null;
                if (t != null && t.getUpdatedAt() >= lng(d, "updatedAt")) continue;
                boolean isNew = t == null;
                if (isNew) {
                    t = new TransactionEntity();
                    if (localId > 0) t.setId(localId);
                }
                String type = d.getString("type");
                String loanId = d.getString("loanId");
                if (loanId != null && db.loanDao().byId(loanId) == null) {
                    Log.w(TAG, "pull: tx " + d.getId() + " tro toi khoan vay khong co " + loanId + " -> bo lien ket");
                    loanId = null;
                }
                String catId = d.getString("categoryId");
                String personId = d.getString("personId");
                t.setType(type);
                t.setAmount(lng(d, "amount"));
                t.setDate(lng(d, "occurredAt"));
                t.setTitle(d.getString("title") == null ? "" : d.getString("title"));
                t.setNote(PaymentNote.join(Stats.isDebtKind(Stats.normalize(type)),
                        PaymentNote.labelOf(d.getString("paymentMethod")), d.getString("note")));
                t.setCategoryId(catId == null ? null : catMap.get(catId));
                t.setPartnerId(personId == null ? null : personMap.get(personId));
                t.setLoanId(loanId);
                if (loanId != null) {                    // cot phu cua giao dich vay: chep tu khoan goc
                    LoanEntity l = db.loanDao().byId(loanId);
                    t.setDueDate(l.getDueDate());
                    t.setSettled(l.getSettled());
                    t.setWrittenOff(l.getWrittenOff());
                    t.setRate(l.getRate());
                }
                t.setDeleted(bool(d, "deleted") ? 1 : 0);
                t.setUpdatedAt(lng(d, "updatedAt"));
                if (isNew) db.transactionDao().insert(t); else db.transactionDao().update(t);
                applied[0]++;
            }
        });

        if (settings != null && settings.exists()) applySettings(settings);
        long stamp = lng(head, "updatedAt");
        Prefs.setLastBackup(context, stamp);
        if (Prefs.localChangedAt(context) < stamp) Prefs.setLocalChangedAt(context, stamp);
        Categories.refresh(context);
        Log.i(TAG, "pull: gop xong " + applied[0] + " dong");
        return applied[0];
    }

    private void applySettings(DocumentSnapshot s) {
        String name = s.getString("userName");
        if (name != null && !name.trim().isEmpty()) Prefs.setUserName(context, name);
        Double budget = dbl(s, "budget");
        if (budget != null && budget > 0) Prefs.setBudget(context, budget);
        Double day = dbl(s, "cycleDay"), month = dbl(s, "cycleMonth");
        if (day != null && month != null) Prefs.setCycle(context, day.intValue(), month.intValue());
        Double warn = dbl(s, "warnPercent");
        if (warn != null) Prefs.setWarnPercent(context, warn.intValue());
        Double big = dbl(s, "bigPercent");
        if (big != null) Prefs.setBigPercent(context, big.intValue());
        Boolean strong = s.getBoolean("strongAlarm");
        if (strong != null) Prefs.setStrongAlarm(context, strong);
        String reminders = s.getString("reminders");
        if (reminders != null) {
            Prefs.setRemindersRaw(context, reminders);
            Reminders.rescheduleAll(context);
        }
        Prefs.setOnboarded(context, true);
    }

    // ============================================================= NUT "DONG BO"
    /**
     * Gop cloud vao may (dong nao moi hon thi thang) roi day phan thay doi len.
     * Hai chieu, khong bao gio xoa sach may - bam bao nhieu lan cung an toan.
     */
    public void syncNow(@Nullable final SyncResult result) {
        final String uid = uid();
        if (uid == null) { report(result, false, 0, true, "ch\u01b0a \u0111\u0103ng nh\u1eadp"); return; }
        if (!hasNetwork()) { report(result, false, 0, true, "m\u00e1y \u0111ang kh\u00f4ng c\u00f3 m\u1ea1ng"); return; }
        Db.io(() -> {
            try {
                Log.i(TAG, "syncNow: bat dau");
                pull(uid);
                int live = push(uid);
                report(result, true, live, true, null);
            } catch (Exception e) {
                Log.e(TAG, "syncNow that bai", e);
                report(result, false, 0, true, friendly(e));
            }
        });
    }

    // ============================================================= XEM CLOUD
    public void loadInfo(final InfoResult result) {
        final String uid = uid();
        if (uid == null || !hasNetwork()) {
            Db.ui(() -> result.onDone(Info.unreachable(uid == null ? "ch\u01b0a \u0111\u0103ng nh\u1eadp" : "kh\u00f4ng c\u00f3 m\u1ea1ng")));
            return;
        }
        Db.io(() -> {
            Info info;
            try {
                DocumentSnapshot d = await(meta(uid).get(Source.SERVER));
                info = d == null || !d.exists()
                        ? new Info(false, 0, 0, "", false, null)
                        : new Info(true, lng(d, "updatedAt"), (int) lng(d, "count"), d.getString("device"), false, null);
            } catch (Exception e) {
                Log.w(TAG, "loadInfo: khong doc duoc may chu", e);
                info = Info.unreachable(friendly(e));
            }
            final Info out = info;
            Db.ui(() -> result.onDone(out));
        });
    }

    public String describeStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("\u0110\u0103ng nh\u1eadp: ").append(isSignedIn() ? email() : "ch\u01b0a").append("\n");
        sb.append("UID: ").append(uid() == null ? "\u2014" : uid()).append("\n");
        sb.append("C\u1ea5u tr\u00fac: v").append(SCHEMA_VERSION).append(" (").append(ROOT).append("/{uid})\n");
        sb.append("M\u1ea1ng: ").append(hasNetwork() ? "c\u00f3" : "kh\u00f4ng").append("\n");
        sb.append("M\u00e1y \u0111\u1ed5i l\u1ea7n cu\u1ed1i: ").append(stamp(Prefs.localChangedAt(context))).append("\n");
        sb.append("\u0110\u1ea9y l\u00ean l\u1ea7n cu\u1ed1i: ").append(stamp(Prefs.lastBackup(context)));
        return sb.toString();
    }

    private static String stamp(long time) {
        if (time <= 0) return "ch\u01b0a c\u00f3";
        return android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", time).toString();
    }

    public void saveAccountProfile() {
        String uid = uid();
        if (uid == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("email", email());
        data.put("displayName", displayName());
        data.put("lastSeenAt", System.currentTimeMillis());
        root(uid).set(data, SetOptions.merge());
    }

    // ============================================================= XOA CLOUD
    /** Xoa du lieu v6 tren cloud. Du lieu duoi may va users/{uid} (ban cu) giu nguyen. */
    public void deleteBackup(@Nullable final Result result) {
        final String uid = uid();
        if (uid == null) { done(result, false, 0, "ch\u01b0a \u0111\u0103ng nh\u1eadp"); return; }
        if (!hasNetwork()) { done(result, false, 0, "m\u00e1y \u0111ang kh\u00f4ng c\u00f3 m\u1ea1ng"); return; }
        Db.io(() -> {
            try {
                for (String table : TABLES) {
                    List<DocumentSnapshot> docs = readAll(uid, table);
                    for (int i = 0; i < docs.size(); i += BATCH_LIMIT) {
                        WriteBatch b = firestore.batch();
                        for (int j = i; j < Math.min(i + BATCH_LIMIT, docs.size()); j++) b.delete(docs.get(j).getReference());
                        await(b.commit());
                    }
                }
                WriteBatch tail = firestore.batch();
                tail.delete(settingsRef(uid));
                tail.delete(meta(uid));
                await(tail.commit());
                sp().edit().putLong(K_LAST_PUSH + ":" + uid, 0L).apply();   // lan sau day lai toan bo
                Prefs.setLastBackup(context, 0L);
                done(result, true, 0, null);
            } catch (Exception e) {
                Log.e(TAG, "deleteBackup that bai", e);
                done(result, false, 0, friendly(e));
            }
        });
    }

    /** Ban v6 KHONG tu xoa users/{uid} (ban cu) - giu lai de con duong lui. */
    public void cleanupLegacy() {
        Log.i(TAG, "cleanupLegacy: bo qua, du lieu cu o users/{uid} duoc giu nguyen");
    }

    // ------------------------------------------------------------- tien ich
    private static void done(@Nullable final Result r, final boolean ok, final int count, @Nullable final String err) {
        if (r != null) Db.ui(() -> r.onDone(ok, count, err));
    }

    private static void report(@Nullable final SyncResult r, final boolean ok, final int count,
                               final boolean pushed, @Nullable final String err) {
        if (r != null) Db.ui(() -> r.onDone(ok, count, pushed, err));
    }

    private static int legacyId(DocumentSnapshot d) {
        Object v = d.get("legacyId");
        if (v == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long lng(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    @Nullable
    private static Double dbl(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    private static boolean bool(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return v instanceof Number && ((Number) v).intValue() != 0;
    }
}
