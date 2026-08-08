package com.example.bmmoney;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.example.bmmoney.util.Categories;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

/**
 * Cau hinh toan app.
 *
 * <p><b>Ban va 01/08:</b> truoc day Firestore duoc dat MemoryCacheSettings, tuc la
 * KHONG luu gi xuong dia. Hau qua:</p>
 * <ul>
 *   <li>Mat mang mot nhip la lenh doc bao thang
 *       "Failed to get document because the client is offline" vi khong con ban cache nao.</li>
 *   <li>Lenh ghi dang xep hang bi mat khi app bi tat, nen ban sao luu "nhu da gui" ma
 *       thuc te chua bao gio len toi may chu.</li>
 * </ul>
 *
 * <p>Nay dung cache tren dia (100MB) de doc con lui ve duoc ban cu va lenh ghi con
 * song qua lan mo app sau.</p>
 */
public class BmmApp extends Application {

    /** Kenh thong bao cho loi nhac ghi chi tieu. */
    public static final String CHANNEL_REMINDER = "bmm_reminder";

    /** Cache Firestore toi da giu duoi may. */
    private static final long CACHE_BYTES = 100L * 1024L * 1024L;

    @Override
    public void onCreate() {
        super.onCreate();
        createReminderChannel();
        setupFirestore();

        // Ban va 08/08: danh muc gio nam trong co so du lieu, nhung Room CAM truy van
        // tren luong giao dien. Nap san mot ban vao bo nho ngay luc khoi dong de cac
        // man hinh doc duoc ngay ma khong bi nem loi.
        Categories.preload(this);
    }

    /** Phai chay TRUOC moi thao tac Firestore dau tien, neu khong se bi nem loi. */
    private void setupFirestore() {
        try {
            FirebaseApp.initializeApp(this);
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                            .setSizeBytes(CACHE_BYTES)
                            .build())
                    .build();
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Throwable ignored) {
            // Firestore chua san sang -> bo qua, app van chay binh thuong
        }
    }

    private void createReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDER,
                "Nh\u1eafc ghi chi ti\u00eau", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Nh\u1eafc b\u1ea1n ghi l\u1ea1i chi ti\u00eau trong ng\u00e0y");
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            // Khi app xuong nen, tra lai bo nho anh/bitmap cho he thong
            System.gc();
        }
    }
}
