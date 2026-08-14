package com.example.bmmoney.notify;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Dich vu lang nghe thong bao he thong.
 *
 * <p>He thong tu goi dich vu nay sau khi nguoi dung cap quyen trong cai dat.
 * Dich vu khong doc duoc thong bao cu truoc luc duoc cap quyen: chi co thong bao
 * moi den, cong voi cac thong bao con dang nam tren man hinh luc vua ket noi.
 */
public class NotifyListener extends NotificationListenerService {

    private static final String TAG = "BmmNotify";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn);
    }

    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;
            for (StatusBarNotification sbn : active) {
                handle(sbn);
            }
        } catch (Throwable error) {
            Log.w(TAG, "onListenerConnected: khong quet duoc thong bao dang co", error);
        }
    }

    private void handle(@Nullable StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;

            Notification notification = sbn.getNotification();
            // Bo thong bao gop nhom, vi noi dung that nam o cac thong bao con.
            if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

            Bundle extras = notification.extras;
            if (extras == null) return;

            String title = text(extras, Notification.EXTRA_TITLE);
            String body = text(extras, Notification.EXTRA_BIG_TEXT);
            if (body.isEmpty()) body = text(extras, Notification.EXTRA_TEXT);
            if (body.isEmpty()) body = text(extras, Notification.EXTRA_SUB_TEXT);

            SuggestionEngine.consider(getApplicationContext(), sbn.getPackageName(),
                    title, body, sbn.getPostTime());
        } catch (Throwable error) {
            Log.w(TAG, "handle: khong doc duoc thong bao", error);
        }
    }

    /**
     * Doc bang getCharSequence chu khong phai getString: nhieu app ngan hang
     * gui noi dung duoi dang SpannableString, luc do getString tra ve null.
     */
    private static String text(Bundle extras, String key) {
        try {
            CharSequence value = extras.getCharSequence(key);
            return value == null ? "" : value.toString().trim();
        } catch (Throwable error) {
            return "";
        }
    }
}
