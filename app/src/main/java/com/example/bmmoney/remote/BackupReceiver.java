package com.example.bmmoney.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Reminders;

/**
 * Nhan bao thuc sao luu cua he thong.
 *
 * <p>Khong co tien trinh nao chay nen: app chi thuc day dung luc bao thuc no,
 * ghi mot ban sao luu roi dat lich cho ngay hom sau.</p>
 */
public class BackupReceiver extends BroadcastReceiver {

    public static final String ACTION_DAILY = "com.example.bmmoney.BACKUP_DAILY";
    public static final String ACTION_SOON = "com.example.bmmoney.BACKUP_SOON";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final String action = intent == null ? null : intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Khoi dong lai may thi moi bao thuc deu bi xoa -> dat lai
            AutoBackup.scheduleDaily(app);
            Reminders.rescheduleAll(app);
            return;
        }

        final PendingResult result = goAsync();
        AutoBackup.run(app, () -> {
            if (ACTION_SOON.equals(action)) {
                // Sao luu gom thay doi: khong can dat lai lich
                result.finish();
                return;
            }
            AutoBackup.scheduleDaily(app);
            // Ban va 04/08: bao thuc sao luu 7h sang la dip tu chua loi nhac moi ngay.
            // Neu vi ly do nao do chuoi bao thuc nhac nho bi dut, den sang hom sau no
            // duoc dat lai, khong can nguoi dung mo app.
            Reminders.rescheduleAll(app);
            result.finish();
        });
    }
}
