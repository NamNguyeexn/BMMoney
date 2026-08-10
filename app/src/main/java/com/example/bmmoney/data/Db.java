package com.example.bmmoney.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mot luong nen dung chung cho moi truy van Room, tranh chan luong giao dien.
 * Dung Db.load(...) de doc du lieu roi cap nhat UI tren luong chinh.
 */
public final class Db {

    private static final String TAG = "BmmDb";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Db() {
    }

    public interface Work<T> {
        T run();
    }

    public interface Done<T> {
        void run(T result);
    }

    /**
     * Chay mot tac vu tren luong nen.
     *
     * <p>Bat het ngoai le ngay tai day. Ca app chi dung MOT luong nen: neu mot tac vu
     * nem ngoai le ra ngoai thi luong do bi giet, moi tac vu xep hang phia sau khong
     * bao gio chay nua, va app chet kem theo mot vet loi khong chi ra duoc man hinh nao
     * gay ra. Ghi lai roi nuot o day de mot cho hong khong keo sap toan bo.</p>
     */
    public static void io(final Runnable task) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    Log.e(TAG, "Db.io: tac vu nen that bai", t);
                }
            }
        });
    }

    public static void ui(final Runnable task) {
        MAIN.post(task);
    }

    /** Doc du lieu tren luong nen, tra ket qua ve luong giao dien. */
    public static <T> void load(final Work<T> work, final Done<T> done) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                T result;
                try {
                    result = work.run();
                } catch (Throwable t) {
                    // Nuot im lang bien mot loi truy van thanh "man hinh trong" khong ro
                    // nguyen nhan. Van tra null de giao dien tu xu ly, nhung phai de lai
                    // vet trong logcat.
                    Log.e(TAG, "Db.load: doc du lieu that bai", t);
                    result = null;
                }
                final T value = result;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        done.run(value);
                    }
                });
            }
        });
    }
}
