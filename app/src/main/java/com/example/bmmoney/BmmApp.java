package com.example.bmmoney;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;

/**
 * Cau hinh toan app, dong thoi giam bo nho Firestore giu lai
 * (khong dung cache tren dia, chi cache trong bo nho va giai phong khi can).
 */
public class BmmApp extends Application {

    /** Kenh thong bao cho loi nhac ghi chi tieu. */
    public static final String CHANNEL_REMINDER = "bmm_reminder";

    @Override
    public void onCreate() {
        super.onCreate();
        createReminderChannel();
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
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
