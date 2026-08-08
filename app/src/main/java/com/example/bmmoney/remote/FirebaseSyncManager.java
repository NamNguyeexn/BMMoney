package com.example.bmmoney.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryEntity;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.LoanEntity;
import com.example.bmmoney.data.PartnerEntity;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Prefs;
import com.example.bmmoney.util.Reminders;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dong bo Room &lt;-&gt; Firestore theo mo hinh TUNG TAI LIEU (per-document).
 *
 * <p>Cau truc tren Firestore:</p>
 * <pre>
 * users/{uid}                 ho so tai khoan (email, ten, lan cuoi dung)
 * users/{uid}/meta/sync       moc dong bo: updatedAt, count, device, schemaVersion, settings
 * users/{uid}/cats/{id}       danh muc:  name, emoji, kind, sortOrder, archived, updatedAt, deleted
 * users/{uid}/people/{id}     doi tac:   name, phone, note, updatedAt, deleted
 * users/{uid}/loans/{loanId}  khoan vay: partnerName, direction, principal, rate,
 *                                        openedDate, dueDate, settled, writtenOff, updatedAt, deleted
 * users/{uid}/tx/{id}         giao dich: type, amount, date, title, note, categoryName,
 *                                        partnerName, loanId, dueDate, settled, writtenOff,
 *                                        rate, updatedAt, deleted
 * </pre>
 *
 * <p><b>Vi sao doi khoi kieu cu.</b> Ban truoc gom TOAN BO giao dich thanh mot chuoi
 * JSON roi cat thanh cac manh part_0..N nhet vao users/{uid}/backup. Kieu do co ba
 * cai gia phai tra: sua mot giao dich cung phai day lai ca khoi; khong query duoc gi
 * tren cloud; va chi can mot manh hong la mat tat ca. Nay moi ban ghi la mot document
 * rieng nen day duoc PHAN THAY DOI, doc duoc bang query, hong mot cai thi chi mat cai do.</p>
 *
 * <p><b>Ten collection co chu y.</b> Cloud dung <code>tx</code> / <code>cats</code> /
 * <code>people</code> chu khong phai <code>transactions</code>, vi
 * {@link #cleanupLegacy()} van dang phai don sach collection <code>transactions</code>
 * cua ban rat cu. Trung ten la app se tu xoa dung du lieu vua day len.</p>
 *
 * <p><b>Khong bao gio day id cuc bo len cloud.</b> categoryId / partnerId la so
 * tu tang cua RIENG tung may, may khac danh so khac han. Cloud chi luu TEN, luc
 * keo ve moi tra ten thanh id cua may nay. Doc id cua cats/people van la id cuc bo,
 * nhung chi dung lam khoa tai lieu cho on dinh, khong dung de lien ket.</p>
 */
public class FirebaseSyncManager {

    /** Mot WriteBatch cua Firestore toi da 500 thao tac; chua 400 cho con bien. */
    private static final int BATCH_LIMIT = 400;

    /** So document toi da duoc xoa trong mot vong. */
    private static final int CLEAN_LIMIT = 400;

    /** Qua khoang nay ma may chu chua tra loi thi coi nhu that bai va bao nguoi dung. */
    private static final long TIMEOUT_MS = 20000L;

    /** Phien ban cau truc du lieu tren cloud, ghi vao meta/sync de sau nay con biet duong doc. */
    private static final int SCHEMA_VERSION = 5;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * The log de soi bang lenh: {@code adb logcat -s BmmSync}.
     * Moi buoc cua sao luu / dong bo deu ghi lai, khong con phai doan.
     */
    private static final String TAG = "BmmSync";

    private static final String C_TX = "tx";
    private static final String C_CATS = "cats";
    private static final String C_PEOPLE = "people";
    private static final String C_LOANS = "loans";
    private static final String C_META = "meta";
    private static final String D_SYNC = "sync";

    /** Cac collection cua BAN NAY, xoa het khi nguoi dung bam Xoa ban sao luu. */
    private static final String[] OWNED = { C_TX, C_LOANS, C_CATS, C_PEOPLE };

    /**
     * Cac collection cua BAN CU, chi don di.
     * <code>transactions</code> la kieu per-document doi dau, <code>backup</code> la
     * kieu JSON cat manh. Ca hai deu khong con duoc doc nua.
     */
    private static final String[] LEGACY = { "transactions", "backup" };

    /** Ket qua cua mot lan sao luu hoac khoi phuc. */
    public interface Result {
        void onDone(boolean ok, int count, @Nullable String error);
    }

    /**
     * Ket qua cua nut "Dong bo", co kem HUONG da chay.
     *
     * pushed true = day du lieu may len cloud, false = tai cloud ve may
     */
    public interface SyncResult {
        void onDone(boolean ok, int count, boolean pushed, @Nullable String error);
    }

    /** Thong tin ban sao luu dang nam tren cloud. */
    public static class Info {
        public final boolean exists;
        public final long updatedAt;
        public final int count;
        public final String device;
        /**
         * true nghia la KHONG doc duoc may chu, so lieu nay chi la ban nam trong bo nho
         * dem duoi may (co the la chinh lenh ghi dang xep hang). Tin vao no ma dong bo
         * thi rat de day / keo nham.
         */
        public final boolean fromCache;

        Info(boolean exists, long updatedAt, int count, String device) {
            this(exists, updatedAt, count, device, true);
        }

        Info(boolean exists, long updatedAt, int count, String device, boolean fromCache) {
            this.exists = exists;
            this.updatedAt = updatedAt;
            this.count = count;
            this.device = device == null ? "" : device;
            this.fromCache = fromCache;
        }

        /** KHONG xac nhan duoc voi may chu. Dong bo phai dung lai o truong hop nay. */
        static Info unreachable() {
            return new Info(false, 0, 0, "", true);
        }

        /** May chu TRA LOI RO RANG la tai khoan nay chua co ban sao luu nao. */
        static Info emptyOnServer() {
            return new Info(false, 0, 0, "", false);
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

    // ------------------------------------------------------------- duong dan
    @Nullable
    private DocumentReference rootRef() {
        String id = uid();
        if (id == null) return null;
        return firestore.collection("users").document(id);
    }

    @Nullable
    private CollectionReference col(String name) {
        DocumentReference root = rootRef();
        return root == null ? null : root.collection(name);
    }

    @Nullable
    private DocumentReference metaRef() {
        CollectionReference meta = col(C_META);
        return meta == null ? null : meta.document(D_SYNC);
    }

    // ------------------------------------------------- ket qua chi bao dung mot lan
    /**
     * Boc callback cua man hinh lai: dam bao chi chay dung MOT lan va luon chay
     * tren luong giao dien.
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
        if (t == null) return "l\\u1ed7i kh\\u00f4ng r\\u00f5";
        if (t instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) t).getCode();
            switch (code) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "kh\\u00f4ng k\\u1ebft n\\u1ed1i \\u0111\\u01b0\\u1ee3c m\\u00e1y ch\\u1ee7, ki\\u1ec3m tra m\\u1ea1ng nh\\u00e9";
                case PERMISSION_DENIED: {
                    String detail = t.getMessage() == null ? "" : t.getMessage();
                    if (detail.contains("has not been used in project")
                            || detail.contains("SERVICE_DISABLED")
                            || detail.contains("is disabled")) {
                        return "d\\u1ef1 \\u00e1n Firebase ch\\u01b0a b\\u1eadt Cloud Firestore API, "
                                + "v\\u00e0o Firebase Console t\\u1ea1o Firestore Database tr\\u01b0\\u1edbc nh\\u00e9";
                    }
                    return "t\\u00e0i kho\\u1ea3n ch\\u01b0a c\\u00f3 quy\\u1ec1n ghi (xem Firestore Rules)";
                }
                case UNAUTHENTICATED:
                    return "phi\\u00ean \\u0111\\u0103ng nh\\u1eadp \\u0111\\u00e3 h\\u1ebft h\\u1ea1n, \\u0111\\u0103ng nh\\u1eadp l\\u1ea1i nh\\u00e9";
                case NOT_FOUND:
                    return "cloud ch\\u01b0a c\\u00f3 b\\u1ea3n sao l\\u01b0u n\\u00e0o";
                case RESOURCE_EXHAUSTED:
                    return "d\\u1ef1 \\u00e1n Firebase \\u0111\\u00e3 h\\u1ebft h\\u1ea1n m\\u1ee9c mi\\u1ec5n ph\\u00ed h\\u00f4m nay";
                default:
                    break;
            }
        }
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) return "l\\u1ed7i kh\\u00f4ng r\\u00f5";
        if (message.contains("has not been used in project") || message.contains("SERVICE_DISABLED")) {
            return "d\\u1ef1 \\u00e1n Firebase ch\\u01b0a b\\u1eadt Cloud Firestore API";
        }
        if (message.toLowerCase().contains("offline")) {
            return "m\\u00e1y \\u0111ang ngo\\u1ea1i tuy\\u1ebfn v\\u1edbi Firestore, ki\\u1ec3m tra m\\u1ea1ng nh\\u00e9";
        }
        return message;
    }

    // ------------------------------------------------------------- doc cloud
    /**
     * Doc mot document: uu tien ban tren may chu, that bai moi lui ve cache.
     * Nho vay khi may chu khong voi toi duoc ta van con du lieu de lam viec.
     */
    private void readDoc(final DocumentReference ref, final DocRead callback) {
        ref.get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        callback.onRead(snapshot, null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull final Exception serverError) {
                        ref.get(Source.CACHE)
                                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(DocumentSnapshot snapshot) {
                                        if (snapshot != null && snapshot.exists()) {
                                            callback.onRead(snapshot, null);
                                        } else {
                                            callback.onRead(null, serverError);
                                        }
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception cacheError) {
                                        callback.onRead(null, serverError);
                                    }
                                });
                    }
                });
    }

    private interface DocRead {
        void onRead(@Nullable DocumentSnapshot snapshot, @Nullable Exception error);
    }

    /**
     * Doc ca mot collection, cung kieu uu tien may chu roi lui ve cache.
     * Tra ve danh sach RONG nghia la may chu noi that su khong co gi;
     * tra ve null nghia la khong doc duoc - hai chuyen hoan toan khac nhau.
     */
    private void readCol(final CollectionReference ref, final ColRead callback) {
        ref.get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshot) {
                        callback.onRead(snapshot == null
                                ? new ArrayList<DocumentSnapshot>()
                                : snapshot.getDocuments(), null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull final Exception serverError) {
                        ref.get(Source.CACHE)
                                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                    @Override
                                    public void onSuccess(QuerySnapshot snapshot) {
                                        if (snapshot != null && !snapshot.isEmpty()) {
                                            callback.onRead(snapshot.getDocuments(), null);
                                        } else {
                                            callback.onRead(null, serverError);
                                        }
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception cacheError) {
                                        callback.onRead(null, serverError);
                                    }
                                });
                    }
                });
    }

    private interface ColRead {
        void onRead(@Nullable List<DocumentSnapshot> docs, @Nullable Exception error);
    }

    // ------------------------------------------------------------- doc gia tri
    private static String str(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    @Nullable
    private static String strOrNull(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static long lng(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static int itg(DocumentSnapshot d, String key) {
        return (int) lng(d, key);
    }

    @Nullable
    private static Double dbl(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    /** Khoa tai lieu la id cuc bo; doc khong ra so thi de Room tu danh so moi. */
    private static int idOf(DocumentSnapshot d) {
        try {
            return Integer.parseInt(d.getId());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ------------------------------------------------------------- dung tai lieu
    private static Map<String, Object> mapOf(CategoryEntity c) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", c.getName());
        m.put("emoji", c.getEmoji());
        m.put("kind", c.getKind());
        m.put("sortOrder", c.getSortOrder());
        m.put("archived", c.getArchived());
        m.put("updatedAt", c.getUpdatedAt());
        m.put("deleted", c.getDeleted());
        return m;
    }

    private static Map<String, Object> mapOf(PartnerEntity p) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", p.getName());
        m.put("phone", p.getPhone());
        m.put("note", p.getNote());
        m.put("updatedAt", p.getUpdatedAt());
        m.put("deleted", p.getDeleted());
        return m;
    }

    private static Map<String, Object> mapOf(LoanEntity l, @Nullable String partnerName) {
        Map<String, Object> m = new HashMap<>();
        m.put("partnerName", partnerName);
        m.put("direction", l.getDirection());
        m.put("principal", l.getPrincipal());
        m.put("rate", l.getRate());
        m.put("openedDate", l.getOpenedDate());
        m.put("dueDate", l.getDueDate());
        m.put("settled", l.getSettled());
        m.put("writtenOff", l.getWrittenOff());
        m.put("updatedAt", l.getUpdatedAt());
        m.put("deleted", l.getDeleted());
        return m;
    }

    private static Map<String, Object> mapOf(TransactionEntity t,
                                             @Nullable String categoryName,
                                             @Nullable String partnerName) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", t.getType());
        m.put("amount", t.getAmount());
        m.put("date", t.getDate());
        m.put("title", t.getTitle());
        m.put("note", t.getNote());
        m.put("categoryName", categoryName);
        m.put("partnerName", partnerName);
        m.put("loanId", t.getLoanId());
        m.put("dueDate", t.getDueDate());
        m.put("settled", t.getSettled());
        m.put("writtenOff", t.getWrittenOff());
        m.put("rate", t.getRate());
        m.put("updatedAt", t.getUpdatedAt());
        m.put("deleted", t.getDeleted());
        // dayKey / monthKey / yearKey deu suy ra tu date nen khong day len,
        // luc keo ve setDate() se tu tinh lai.
        return m;
    }

    // ------------------------------------------------------------- sao luu
    /**
     * Day du lieu duoi may len cloud.
     *
     * <p>Doc <code>meta/sync</code> truoc de biet lan day cuoi la luc nao. Con moc do
     * thi chi day nhung ban ghi co <code>updatedAt</code> moi hon - thuong la vai cai.
     * Chua co moc (cloud trong, hoac doc rot ve cache nen khong dam chac) thi day
     * toan bo cho an toan.</p>
     */
    public void backupNow(@Nullable Result result) {
        final Once once = new Once(result);
        final DocumentReference meta = metaRef();
        if (meta == null) {
            once.finish(false, 0, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
            return;
        }
        if (!hasNetwork()) {
            once.finish(false, 0, "m\\u00e1y \\u0111ang kh\\u00f4ng c\\u00f3 m\\u1ea1ng");
            return;
        }
        once.arm("m\\u00e1y ch\\u1ee7 kh\\u00f4ng tr\\u1ea3 l\\u1eddi, th\\u1eed l\\u1ea1i nh\\u00e9");
        wakeNetwork();
        Log.i(TAG, "backupNow: bat dau, uid=" + uid());

        readDoc(meta, new DocRead() {
            @Override
            public void onRead(@Nullable DocumentSnapshot head, @Nullable Exception error) {
                if (once.isDone()) return;
                boolean live = head != null
                        && (head.getMetadata() == null || !head.getMetadata().isFromCache());
                final long since = (live && head.exists()) ? lng(head, "updatedAt") : 0L;
                Log.i(TAG, since <= 0
                        ? "backupNow: day TOAN BO"
                        : "backupNow: day PHAN THAY DOI tu moc " + since);
                Db.io(new Runnable() {
                    @Override
                    public void run() {
                        pushData(once, since);
                    }
                });
            }
        });
    }

    /** Gom du lieu can day. Chay tren luong nen vi doc Room. */
    private void pushData(final Once once, final long since) {
        final List<DocumentReference> refs = new ArrayList<>();
        final List<Map<String, Object>> bodies = new ArrayList<>();
        int liveCount = 0;
        try {
            CollectionReference cats = col(C_CATS);
            CollectionReference people = col(C_PEOPLE);
            CollectionReference loans = col(C_LOANS);
            CollectionReference tx = col(C_TX);
            if (cats == null || people == null || loans == null || tx == null) {
                once.finish(false, 0, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
                return;
            }

            // Ban do id -> ten, dung cho ca ban ghi cu lan moi.
            Map<Integer, String> catName = new HashMap<>();
            for (CategoryEntity c : db.categoryDao().getAllForSync()) {
                catName.put(c.getId(), c.getName());
            }
            Map<Integer, String> personName = new HashMap<>();
            for (PartnerEntity p : db.partnerDao().getAllForSync()) {
                personName.put(p.getId(), p.getName());
            }

            List<CategoryEntity> catRows = since <= 0
                    ? db.categoryDao().getAllForSync()
                    : db.categoryDao().changedSince(since);
            for (CategoryEntity c : catRows) {
                refs.add(cats.document(String.valueOf(c.getId())));
                bodies.add(mapOf(c));
            }

            List<PartnerEntity> peopleRows = since <= 0
                    ? db.partnerDao().getAllForSync()
                    : db.partnerDao().changedSince(since);
            for (PartnerEntity p : peopleRows) {
                refs.add(people.document(String.valueOf(p.getId())));
                bodies.add(mapOf(p));
            }

            List<LoanEntity> loanRows = since <= 0
                    ? db.loanDao().getAllForSync()
                    : db.loanDao().changedSince(since);
            for (LoanEntity l : loanRows) {
                String loanId = l.getLoanId();
                if (loanId == null || loanId.trim().isEmpty()) continue;
                refs.add(loans.document(loanId));
                bodies.add(mapOf(l, personName.get(l.getPartnerId())));
            }

            List<TransactionEntity> txRows = since <= 0
                    ? db.transactionDao().getAllForSync()
                    : db.transactionDao().changedSince(since);
            for (TransactionEntity t : txRows) {
                refs.add(tx.document(String.valueOf(t.getId())));
                bodies.add(mapOf(t, catName.get(t.getCategoryId()), personName.get(t.getPartnerId())));
            }

            liveCount = db.transactionDao().count();
        } catch (Throwable t) {
            Log.e(TAG, "backupNow: doc du lieu duoi may that bai", t);
            once.finish(false, 0, friendly(t));
            return;
        }
        Log.i(TAG, "backupNow: co " + refs.size() + " ban ghi can day");
        commitAll(once, refs, bodies, liveCount);
    }

    /**
     * Ghi theo lo roi moi dong dau.
     *
     * <p>Phai ghi du lieu XONG HAN moi duoc ghi <code>meta/sync</code>: moc trong do la
     * can cu cho lan day sau. Ghi dau truoc ma du lieu hong giua chung thi lan sau app
     * tuong da day het roi va bo qua dung nhung ban ghi con thieu.</p>
     */
    private void commitAll(final Once once, final List<DocumentReference> refs,
                           final List<Map<String, Object>> bodies, final int liveCount) {
        final List<Task<Void>> jobs = new ArrayList<>();
        WriteBatch batch = firestore.batch();
        int inBatch = 0;
        for (int i = 0; i < refs.size(); i++) {
            batch.set(refs.get(i), bodies.get(i), SetOptions.merge());
            inBatch++;
            if (inBatch >= BATCH_LIMIT) {
                jobs.add(batch.commit());
                batch = firestore.batch();
                inBatch = 0;
            }
        }
        if (inBatch > 0) jobs.add(batch.commit());

        final int written = refs.size();
        Tasks.whenAll(jobs)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        writeHead(once, liveCount, written);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "backupNow: ghi du lieu that bai", e);
                        once.finish(false, 0, friendly(e));
                    }
                });
    }

    /** Ghi moc dong bo. Buoc cuoi cung, chi chay khi du lieu da len het. */
    private void writeHead(final Once once, final int liveCount, final int written) {
        DocumentReference meta = metaRef();
        if (meta == null) {
            once.finish(false, 0, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
            return;
        }
        final long now = System.currentTimeMillis();
        Map<String, Object> head = new HashMap<>();
        head.put("updatedAt", now);
        head.put("count", liveCount);
        head.put("device", Build.MODEL);
        head.put("schemaVersion", SCHEMA_VERSION);
        head.put("settings", settingsMap());

        meta.set(head, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Prefs.setLastBackup(context, now);
                        Log.i(TAG, "backupNow: xong, day " + written + " ban ghi, cloud giu "
                                + liveCount + " giao dich");
                        cleanupLegacy();
                        once.finish(true, liveCount, null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "backupNow: ghi moc dong bo that bai", e);
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
        data.put("strongAlarm", Prefs.strongAlarm(context));
        return data;
    }

    // ------------------------------------------------------------- xem cloud
    /** Doc thong tin ban sao luu tren cloud (khong thay doi gi duoi may). */
    public void loadInfo(final InfoResult result) {
        DocumentReference meta = metaRef();
        if (meta == null) {
            Db.ui(new Runnable() {
                @Override
                public void run() {
                    result.onDone(Info.unreachable());
                }
            });
            return;
        }

        // Chi bao dung MOT lan va co dong ho canh. Neu Firestore khong tra loi (mat mang,
        // rules chan) thi man Cai dat khong con ket o trang thai "Dang kiem tra...".
        final AtomicBoolean fired = new AtomicBoolean(false);
        final Runnable[] watchdog = new Runnable[1];
        final InfoResult once = new InfoResult() {
            @Override
            public void onDone(final Info info) {
                if (fired.getAndSet(true)) return;
                if (watchdog[0] != null) MAIN.removeCallbacks(watchdog[0]);
                Db.ui(new Runnable() {
                    @Override
                    public void run() {
                        result.onDone(info);
                    }
                });
            }
        };
        watchdog[0] = new Runnable() {
            @Override
            public void run() {
                once.onDone(Info.unreachable());
            }
        };
        MAIN.postDelayed(watchdog[0], TIMEOUT_MS);

        if (!hasNetwork()) {
            once.onDone(Info.unreachable());
            return;
        }
        wakeNetwork();
        readDoc(meta, new DocRead() {
            @Override
            public void onRead(@Nullable final DocumentSnapshot d, @Nullable Exception error) {
                final Info info;
                if (d == null) {
                    // Khong doc noi may chu (va cache cung khong co) -> trang thai mo ho
                    info = Info.unreachable();
                } else if (!d.exists()) {
                    // Doc thanh cong tu SERVER ma khong co document nghia la cloud dang rong.
                    // Phai phan biet ro voi unreachable(), neu khong tai khoan chua tung sao
                    // luu se KHONG BAO GIO dong bo duoc.
                    boolean cached = d.getMetadata() != null && d.getMetadata().isFromCache();
                    info = cached ? Info.unreachable() : Info.emptyOnServer();
                } else {
                    boolean cached = d.getMetadata() != null && d.getMetadata().isFromCache();
                    info = new Info(true, lng(d, "updatedAt"), itg(d, "count"),
                            d.getString("device"), cached);
                }
                once.onDone(info);
            }
        });
    }

    /** Mo ta nhanh trang thai dong bo, dung cho hop thoai Chi tiet dong bo. */
    public String describeStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("\\u0110\\u0103ng nh\\u1eadp: ").append(isSignedIn() ? email() : "ch\\u01b0a").append("\\n");
        sb.append("UID: ").append(uid() == null ? "\\u2014" : uid()).append("\\n");
        sb.append("M\\u1ea1ng: ").append(hasNetwork() ? "c\\u00f3" : "kh\\u00f4ng").append("\\n");
        sb.append("M\\u00e1y \\u0111\\u1ed5i l\\u1ea7n cu\\u1ed1i: ").append(stampText(Prefs.localChangedAt(context))).append("\\n");
        sb.append("Sao l\\u01b0u l\\u1ea7n cu\\u1ed1i: ").append(stampText(Prefs.lastBackup(context)));
        return sb.toString();
    }

    private static String stampText(long time) {
        if (time <= 0) return "ch\\u01b0a c\\u00f3";
        return android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", time).toString();
    }

    // ------------------------------------------------------------- dong bo
    /**
     * DONG BO THONG MINH cho nut "Dong bo": so sanh hai moc thoi gian roi tu quyet dinh.
     *
     * <ul>
     *   <li>Duoi may moi hon cloud, hoac cloud chua co gi: DAY LEN.</li>
     *   <li>Cloud moi hon: KEO VE.</li>
     *   <li>Hai ben bang nhau: day len cho chac, khong xoa gi duoi may.</li>
     * </ul>
     */
    public void syncNow(@Nullable final SyncResult result) {
        if (!isSignedIn()) {
            report(result, false, 0, true, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
            return;
        }
        if (!hasNetwork()) {
            report(result, false, 0, true, "m\\u00e1y \\u0111ang kh\\u00f4ng c\\u00f3 m\\u1ea1ng");
            return;
        }
        Log.i(TAG, "syncNow: bat dau, uid=" + uid());

        loadInfo(new InfoResult() {
            @Override
            public void onDone(final Info info) {
                final long local = Prefs.localChangedAt(context);
                Log.i(TAG, "syncNow: cloud exists=" + info.exists + " count=" + info.count
                        + " updatedAt=" + info.updatedAt + " fromCache=" + info.fromCache
                        + " | local=" + local);
                if (info.fromCache) {
                    // Doc rot ve cache nghia la may chu dang tu choi hoac khong voi toi.
                    Log.w(TAG, "syncNow: chua doc duoc may chu -> dung lai");
                    report(result, false, 0, true,
                            "ch\\u01b0a \\u0111\\u1ecdc \\u0111\\u01b0\\u1ee3c m\\u00e1y ch\\u1ee7 Firestore. "
                                    + "Ki\\u1ec3m tra \\u0111\\u00e3 b\\u1eadt Cloud Firestore API "
                                    + "v\\u00e0 Rules cho ph\\u00e9p t\\u00e0i kho\\u1ea3n n\\u00e0y ch\\u01b0a");
                    return;
                }

                // Cloud chua co gi, hoac co ban sao luu RONG: luon day len.
                // Day la chot an toan quan trong nhat.
                if (!info.exists || info.updatedAt <= 0 || info.count <= 0) {
                    Log.i(TAG, "syncNow: chon DAY LEN (cloud rong hoac chua co)");
                    pushUp(result);
                    return;
                }
                if (local > 0 && local >= info.updatedAt) {
                    Log.i(TAG, "syncNow: chon DAY LEN (may moi hon cloud)");
                    pushUp(result);
                    return;
                }
                Log.i(TAG, "syncNow: chon TAI VE (cloud moi hon may)");
                restoreLatest(new Result() {
                    @Override
                    public void onDone(boolean ok, int count, @Nullable String error) {
                        Log.i(TAG, "syncNow: tai ve xong ok=" + ok + " count=" + count
                                + " error=" + error);
                        report(result, ok, count, false, error);
                    }
                });
            }
        });
    }

    private void pushUp(@Nullable final SyncResult result) {
        backupNow(new Result() {
            @Override
            public void onDone(boolean ok, int count, @Nullable String error) {
                Log.i(TAG, "syncNow: day len xong ok=" + ok + " count=" + count
                        + " error=" + error);
                report(result, ok, count, true, error);
            }
        });
    }

    private void report(@Nullable final SyncResult result, final boolean ok, final int count,
                        final boolean pushed, @Nullable final String error) {
        if (result == null) return;
        Db.ui(new Runnable() {
            @Override
            public void run() {
                result.onDone(ok, count, pushed, error);
            }
        });
    }

    // ------------------------------------------------------------- khoi phuc
    /**
     * Doc ca bon collection tren cloud roi dung lai du lieu duoi may.
     *
     * <p>Doc theo dung THU TU khoa ngoai: danh muc va doi tac truoc, roi khoan vay,
     * cuoi cung moi den giao dich. Room bat <code>PRAGMA foreign_keys = ON</code> nen
     * chen mot giao dich tro toi khoan vay chua ton tai la loi ngay.</p>
     */
    public void restoreLatest(@Nullable Result result) {
        final Once once = new Once(result);
        final DocumentReference meta = metaRef();
        final CollectionReference cats = col(C_CATS);
        final CollectionReference people = col(C_PEOPLE);
        final CollectionReference loans = col(C_LOANS);
        final CollectionReference tx = col(C_TX);
        if (meta == null || cats == null || people == null || loans == null || tx == null) {
            once.finish(false, 0, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
            return;
        }
        if (!hasNetwork()) {
            once.finish(false, 0, "m\\u00e1y \\u0111ang kh\\u00f4ng c\\u00f3 m\\u1ea1ng");
            return;
        }
        once.arm("t\\u1ea3i d\\u1eef li\\u1ec7u qu\\u00e1 l\\u00e2u, th\\u1eed l\\u1ea1i nh\\u00e9");
        wakeNetwork();
        Log.i(TAG, "restoreLatest: bat dau, uid=" + uid());

        readDoc(meta, new DocRead() {
            @Override
            public void onRead(@Nullable final DocumentSnapshot head, @Nullable Exception headError) {
                if (once.isDone()) return;
                if (head == null) {
                    once.finish(false, 0, friendly(headError));
                    return;
                }
                if (!head.exists()) {
                    once.finish(false, 0, "cloud ch\\u01b0a c\\u00f3 b\\u1ea3n sao l\\u01b0u n\\u00e0o");
                    return;
                }
                readCol(cats, new ColRead() {
                    @Override
                    public void onRead(@Nullable final List<DocumentSnapshot> catDocs,
                                       @Nullable Exception e1) {
                        if (once.isDone()) return;
                        if (catDocs == null) {
                            once.finish(false, 0, friendly(e1));
                            return;
                        }
                        readCol(people, new ColRead() {
                            @Override
                            public void onRead(@Nullable final List<DocumentSnapshot> peopleDocs,
                                               @Nullable Exception e2) {
                                if (once.isDone()) return;
                                if (peopleDocs == null) {
                                    once.finish(false, 0, friendly(e2));
                                    return;
                                }
                                readCol(loans, new ColRead() {
                                    @Override
                                    public void onRead(@Nullable final List<DocumentSnapshot> loanDocs,
                                                       @Nullable Exception e3) {
                                        if (once.isDone()) return;
                                        if (loanDocs == null) {
                                            once.finish(false, 0, friendly(e3));
                                            return;
                                        }
                                        readCol(tx, new ColRead() {
                                            @Override
                                            public void onRead(@Nullable List<DocumentSnapshot> txDocs,
                                                               @Nullable Exception e4) {
                                                if (once.isDone()) return;
                                                if (txDocs == null) {
                                                    once.finish(false, 0, friendly(e4));
                                                    return;
                                                }
                                                Log.i(TAG, "restoreLatest: doc duoc "
                                                        + catDocs.size() + " danh muc, "
                                                        + peopleDocs.size() + " doi tac, "
                                                        + loanDocs.size() + " khoan vay, "
                                                        + txDocs.size() + " giao dich");
                                                applyRestore(once, head, catDocs, peopleDocs,
                                                        loanDocs, txDocs);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    /** Ghi du lieu vua tai ve vao Room. Chay tren luong nen. */
    private void applyRestore(final Once once,
                              final DocumentSnapshot head,
                              final List<DocumentSnapshot> catDocs,
                              final List<DocumentSnapshot> peopleDocs,
                              final List<DocumentSnapshot> loanDocs,
                              final List<DocumentSnapshot> txDocs) {
        Db.io(new Runnable() {
            @Override
            public void run() {
                try {
                    int live = 0;
                    for (DocumentSnapshot d : txDocs) {
                        if (itg(d, "deleted") == 0) live++;
                    }

                    // Chot an toan: cloud rong ma may dang co du lieu thi DUNG LAI.
                    // Mot ban sao luu rong khong duoc phep xoa sach du lieu that.
                    if (live == 0 && db.transactionDao().count() > 0) {
                        Log.w(TAG, "restoreLatest: cloud rong ma may con du lieu -> tu choi");
                        once.finish(false, 0,
                                "b\\u1ea3n tr\\u00ean cloud \\u0111ang r\\u1ed7ng, "
                                        + "kh\\u00f4ng l\\u1ea5y v\\u1ec1 \\u0111\\u1ec3 kh\\u1ecfi "
                                        + "m\\u1ea5t d\\u1eef li\\u1ec7u tr\\u00ean m\\u00e1y");
                        return;
                    }

                    // Xoa theo thu tu nguoc voi khoa ngoai: con truoc, cha sau.
                    db.transactionDao().wipe();
                    db.loanDao().wipe();

                    // Danh muc va doi tac chi thay the khi cloud that su co du lieu.
                    // Cloud rong ma van wipe la mat luon bo danh muc mac dinh cua may.
                    if (!catDocs.isEmpty()) {
                        db.categoryDao().wipe();
                        for (DocumentSnapshot d : catDocs) {
                            if (itg(d, "deleted") == 1) continue;
                            String name = strOrNull(d, "name");
                            if (name == null) continue;
                            CategoryEntity c = new CategoryEntity();
                            c.setId(idOf(d));
                            c.setName(name);
                            String emoji = strOrNull(d, "emoji");
                            c.setEmoji(emoji == null ? CategoryEntity.FALLBACK_EMOJI : emoji);
                            String kind = strOrNull(d, "kind");
                            c.setKind(kind == null ? CategoryEntity.KIND_BOTH : kind);
                            c.setSortOrder(itg(d, "sortOrder"));
                            c.setArchived(itg(d, "archived"));
                            c.setUpdatedAt(lng(d, "updatedAt"));
                            c.setDeleted(0);
                            db.categoryDao().insertIgnore(c);
                        }
                    }
                    if (!peopleDocs.isEmpty()) {
                        db.partnerDao().wipe();
                        for (DocumentSnapshot d : peopleDocs) {
                            if (itg(d, "deleted") == 1) continue;
                            String name = strOrNull(d, "name");
                            if (name == null) continue;
                            PartnerEntity p = new PartnerEntity();
                            p.setId(idOf(d));
                            p.setName(name);
                            p.setPhone(strOrNull(d, "phone"));
                            p.setNote(strOrNull(d, "note"));
                            p.setUpdatedAt(lng(d, "updatedAt"));
                            p.setDeleted(0);
                            db.partnerDao().insertIgnore(p);
                        }
                    }

                    // Ten -> id cua RIENG may nay. Cloud khong he biet nhung so nay.
                    Map<String, Integer> catId = new HashMap<>();
                    for (CategoryEntity c : db.categoryDao().getAllForSync()) {
                        catId.put(c.getName(), c.getId());
                    }
                    Map<String, Integer> personId = new HashMap<>();
                    for (PartnerEntity p : db.partnerDao().getAllForSync()) {
                        personId.put(p.getName(), p.getId());
                    }

                    // VONG 1: dung dau cac khoan vay truoc.
                    Set<String> headers = new HashSet<>();
                    for (DocumentSnapshot d : loanDocs) {
                        if (itg(d, "deleted") == 1) continue;
                        String loanId = d.getId();
                        if (loanId == null || loanId.trim().isEmpty()) continue;
                        LoanEntity l = new LoanEntity();
                        l.setLoanId(loanId);
                        l.setPartnerId(personKey(personId, strOrNull(d, "partnerName")));
                        String direction = strOrNull(d, "direction");
                        l.setDirection(direction == null ? LoanEntity.LEND : direction);
                        l.setPrincipal(lng(d, "principal"));
                        l.setRate(dbl(d, "rate"));
                        l.setOpenedDate(lng(d, "openedDate"));
                        l.setDueDate(lng(d, "dueDate"));
                        l.setSettled(itg(d, "settled"));
                        l.setWrittenOff(itg(d, "writtenOff"));
                        l.setUpdatedAt(lng(d, "updatedAt"));
                        l.setDeleted(0);
                        db.loanDao().insert(l);
                        headers.add(loanId);
                    }

                    // VONG 2 + 3: giao dich. Gap loanId chua co dau thi dung tam mot cai,
                    // neu khong khoa ngoai se chan ca me chen.
                    List<TransactionEntity> rows = new ArrayList<>();
                    for (DocumentSnapshot d : txDocs) {
                        if (itg(d, "deleted") == 1) continue;
                        String loanId = strOrNull(d, "loanId");
                        if (loanId != null && !headers.contains(loanId)) {
                            String type = str(d, "type");
                            LoanEntity ghost = new LoanEntity();
                            ghost.setLoanId(loanId);
                            ghost.setPartnerId(personKey(personId, strOrNull(d, "partnerName")));
                            ghost.setDirection("REPAY".equals(type) || "BORROW".equals(type)
                                    ? LoanEntity.BORROW : LoanEntity.LEND);
                            ghost.setPrincipal(0L);
                            ghost.setOpenedDate(lng(d, "date"));
                            ghost.setDueDate(0L);
                            ghost.setSettled(0);
                            ghost.setWrittenOff(0);
                            ghost.setUpdatedAt(lng(d, "updatedAt"));
                            ghost.setDeleted(0);
                            db.loanDao().insert(ghost);
                            headers.add(loanId);
                            Log.w(TAG, "restoreLatest: thieu dau khoan vay " + loanId + ", da dung tam");
                        }

                        TransactionEntity t = new TransactionEntity();
                        t.setId(idOf(d));
                        t.setType(str(d, "type"));
                        t.setAmount(lng(d, "amount"));
                        t.setDate(lng(d, "date"));
                        t.setTitle(str(d, "title"));
                        t.setNote(str(d, "note"));
                        t.setCategoryId(categoryKey(catId, strOrNull(d, "categoryName")));
                        t.setPartnerId(personKey(personId, strOrNull(d, "partnerName")));
                        t.setLoanId(loanId);
                        t.setDueDate(lng(d, "dueDate"));
                        t.setSettled(itg(d, "settled"));
                        t.setWrittenOff(itg(d, "writtenOff"));
                        t.setRate(dbl(d, "rate"));
                        t.setUpdatedAt(lng(d, "updatedAt"));
                        t.setDeleted(0);
                        rows.add(t);
                    }
                    db.transactionDao().insertAll(rows);

                    applyRemoteSettings(head);
                    long stamp = lng(head, "updatedAt");
                    Prefs.setLastBackup(context, stamp);
                    Prefs.setLocalChangedAt(context, stamp);
                    Categories.refresh(context);

                    Log.i(TAG, "restoreLatest: xong, dat lai " + rows.size() + " giao dich");
                    once.finish(true, rows.size(), null);
                } catch (Throwable t) {
                    Log.e(TAG, "restoreLatest: ghi vao Room that bai", t);
                    once.finish(false, 0, friendly(t));
                }
            }
        });
    }

    @Nullable
    private Integer categoryKey(Map<String, Integer> cache, @Nullable String name) {
        if (name == null) return null;
        Integer found = cache.get(name);
        if (found != null) return found;
        Integer made = db.categoryDao().ensure(name, CategoryEntity.FALLBACK_EMOJI);
        if (made != null) cache.put(name, made);
        return made;
    }

    @Nullable
    private Integer personKey(Map<String, Integer> cache, @Nullable String name) {
        if (name == null) return null;
        Integer found = cache.get(name);
        if (found != null) return found;
        Integer made = db.partnerDao().ensure(name);
        if (made != null) cache.put(name, made);
        return made;
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

        Object strong = data.get("strongAlarm");
        if (strong instanceof Boolean) Prefs.setStrongAlarm(context, (Boolean) strong);

        String reminders = string(data.get("reminders"));
        if (reminders != null) {
            Prefs.setRemindersRaw(context, reminders);
            // Keo gio nhac ve thoi la CHUA du - phai dat lai bao thuc that, neu khong
            // man Cai dat hien day cac moc gio nhung khong moc nao no.
            Reminders.rescheduleAll(context);
        }

        Prefs.setOnboarded(context, true);
    }

    @Nullable
    private static String string(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Nullable
    private static Double number(@Nullable Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------- xoa cloud
    /** Xoa toan bo du lieu tren cloud. Du lieu duoi may van con nguyen. */
    public void deleteBackup(@Nullable Result result) {
        final Once once = new Once(result);
        final DocumentReference meta = metaRef();
        if (meta == null) {
            once.finish(false, 0, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
            return;
        }
        if (!hasNetwork()) {
            once.finish(false, 0, "m\\u00e1y \\u0111ang kh\\u00f4ng c\\u00f3 m\\u1ea1ng");
            return;
        }
        once.arm("x\\u00f3a qu\\u00e1 l\\u00e2u, th\\u1eed l\\u1ea1i nh\\u00e9");
        wakeNetwork();
        purgeNext(once, 0, meta);
    }

    private void purgeNext(final Once once, final int index, final DocumentReference meta) {
        if (once.isDone()) return;
        if (index >= OWNED.length) {
            meta.delete()
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Prefs.setLastBackup(context, 0L);
                            Log.i(TAG, "deleteBackup: da xoa sach cloud");
                            once.finish(true, 0, null);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            once.finish(false, 0, friendly(e));
                        }
                    });
            return;
        }
        final CollectionReference ref = col(OWNED[index]);
        if (ref == null) {
            once.finish(false, 0, "ch\\u01b0a \\u0111\\u0103ng nh\\u1eadp");
            return;
        }
        readCol(ref, new ColRead() {
            @Override
            public void onRead(@Nullable List<DocumentSnapshot> docs, @Nullable Exception error) {
                if (once.isDone()) return;
                if (docs == null) {
                    // Khong doc duoc collection nay thi bo qua, con lai van xoa.
                    purgeNext(once, index + 1, meta);
                    return;
                }
                deleteDocs(docs, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        purgeNext(once, index + 1, meta);
                    }
                }, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        once.finish(false, 0, friendly(e));
                    }
                });
            }
        });
    }

    private void deleteDocs(List<DocumentSnapshot> docs,
                            OnSuccessListener<Void> ok, OnFailureListener fail) {
        List<Task<Void>> jobs = new ArrayList<>();
        WriteBatch batch = firestore.batch();
        int inBatch = 0;
        for (DocumentSnapshot d : docs) {
            batch.delete(d.getReference());
            inBatch++;
            if (inBatch >= CLEAN_LIMIT) {
                jobs.add(batch.commit());
                batch = firestore.batch();
                inBatch = 0;
            }
        }
        if (inBatch > 0) jobs.add(batch.commit());
        Tasks.whenAll(jobs).addOnSuccessListener(ok).addOnFailureListener(fail);
    }

    /**
     * Don sach cau truc cu tren cloud: collection <code>transactions</code> cua ban
     * per-document doi dau va collection <code>backup</code> cua ban JSON cat manh.
     *
     * <p>Chay ngam sau moi lan sao luu thanh cong, chi mot lan cho moi may.</p>
     */
    public void cleanupLegacy() {
        if (Prefs.legacyCleaned(context)) return;
        DocumentReference root = rootRef();
        if (root == null) return;
        cleanLegacy(root, 0);
    }

    private void cleanLegacy(final DocumentReference root, final int index) {
        if (index >= LEGACY.length) {
            Prefs.setLegacyCleaned(context, true);
            Log.i(TAG, "cleanupLegacy: da don xong cau truc cu");
            return;
        }
        final CollectionReference legacy = root.collection(LEGACY[index]);
        legacy.limit(CLEAN_LIMIT).get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshot) {
                        if (snapshot == null || snapshot.isEmpty()) {
                            cleanLegacy(root, index + 1);
                            return;
                        }
                        final int size = snapshot.size();
                        WriteBatch batch = firestore.batch();
                        for (DocumentSnapshot d : snapshot.getDocuments()) {
                            batch.delete(d.getReference());
                        }
                        batch.commit().addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                // Con day mot vong thi collection nay co the con nua.
                                cleanLegacy(root, size < CLEAN_LIMIT ? index + 1 : index);
                            }
                        });
                    }
                });
    }
}
