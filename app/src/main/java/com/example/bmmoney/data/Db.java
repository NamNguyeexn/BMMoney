package com.example.bmmoney.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mot luong nen dung chung cho moi truy van Room, tranh chan luong giao dien.
 * Dung Db.load(...) de doc du lieu roi cap nhat UI tren luong chinh.
 */
public final class Db {

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

    public static void io(final Runnable task) {
        IO.execute(task);
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
