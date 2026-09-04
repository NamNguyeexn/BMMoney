package com.example.bmmoney.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * CUA NHAN ANH CHIA SE TU APP NGAN HANG.
 *
 * <p>Man nay khong co giao dien va lam duy nhat mot viec, NGAY LAP TUC: chuyen anh
 * duoc chia se sang man doc anh giao dich, roi tu ket thuc. Viec doc anh dien ra o
 * man ben kia, noi co thanh tien trinh va cho bao loi.
 *
 * <h3>Vi sao khong doc anh ngay tai day (ban truoc lam vay va no khong chay)</h3>
 *
 * <p>Ban truoc doc anh ngay trong man nay roi moi mo man goi y. Ba dieu cung sai:</p>
 *
 * <ol>
 *   <li>Man nay trong suot, khong ve gi, va khai bao {@code noHistory} - he thong ket
 *       thuc no ngay khi no thoi hien dien. Doc anh mat vai giay, nen den luc doc xong
 *       thi Activity da chet: quyen doc Uri mat theo, va lenh mo man goi y bi bo qua
 *       vi {@code isFinishing()} da true.</li>
 *   <li>Ke ca khi con song, lenh mo Activity phat ra sau vai giay cho doi bi Android 10
 *       tro len chan lai nhu mot lenh mo tu duoi nen.</li>
 *   <li>Nguoi dung khong thay bat cu dau hieu nao ve viec doc thanh cong hay that bai,
 *       vi khong co man hinh nao dang mo de bao.</li>
 * </ol>
 *
 * <p>Sua lai theo huong nguoc lai: mo man goi y truoc - viec nay dien ra ngay trong
 * {@code onCreate}, luc app con la ung dung dang o truoc mat nguoi dung nen khong bi
 * chan - va de man do doc anh.
 *
 * <h3>Chuyen quyen doc anh sang man kia the nao</h3>
 *
 * <p>Uri chia se khong the doc boi mot Activity chua duoc cap quyen. Cach chinh thong
 * de chuyen quyen la dat Uri vao {@code ClipData} cua Intent kem co
 * {@code FLAG_GRANT_READ_URI_PERMISSION}: he thong se cap quyen doc cho Activity duoc
 * mo, va quyen do song cung Activity ay. Nho vay man goi y doc duoc anh ma app van
 * khong phai sao anh ra bat cu dau.
 *
 * <p>Ke thua {@code Activity} tran chu khong phai {@code AppCompatActivity}: man nay
 * dung giao dien trong suot cua he thong, ma AppCompat thi doi mot theme AppCompat va
 * se nem loi khi khong co.
 */
public class ShareReceiptActivity extends Activity {

    private static final String FAILED =
            "\u0110\u1ecdc \u1ea3nh giao d\u1ecbch th\u1ea5t b\u1ea1i";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<Uri> images = collect(getIntent());
        if (images.isEmpty()) {
            Toast.makeText(this, FAILED, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            startActivity(SuggestionsActivity.withImages(this, images));
        } catch (Throwable error) {
            Toast.makeText(this, FAILED, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    /** Gom anh tu ca hai kieu chia se: mot anh va nhieu anh. */
    private ArrayList<Uri> collect(@Nullable Intent intent) {
        ArrayList<Uri> out = new ArrayList<>();
        if (intent == null) return out;

        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri one = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (one != null) out.add(one);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) {
                for (Uri uri : many) {
                    if (uri != null) out.add(uri);
                }
            }
        }

        // Mot so app chia se dat anh vao ClipData thay vi EXTRA_STREAM.
        if (out.isEmpty()) {
            ClipData clip = intent.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) out.add(uri);
                }
            }
        }
        return out;
    }
}
