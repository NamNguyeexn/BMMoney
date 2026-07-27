package com.example.bmmoney;

import android.app.Application;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;

/**
 * Cau hinh toan app, dong thoi giam bo nho Firestore giu lai
 * (khong dung cache tren dia, chi cache trong bo nho va giai phong khi can).
 */
public class BmmApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build();
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Throwable ignored) {
            // Firestore chua san sang -> bo qua, app van chay binh thuong
        }
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
