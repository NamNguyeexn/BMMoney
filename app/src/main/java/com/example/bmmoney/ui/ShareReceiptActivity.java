package com.example.bmmoney.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bmmoney.data.Db;
import com.example.bmmoney.ocr.ReceiptImporter;

import java.util.ArrayList;
import java.util.List;

/**
 * CUA NHAN ANH CHIA SE TU APP NGAN HANG.
 *
 * <p>Man nay khong co giao dien. No doc anh ngay tai day, roi mo man goi y va bao
 * ket qua. Anh khong duoc sao lai o bat cu dau.
 *
 * <h3>Vi sao viec doc phai xay ra ngay tai day</h3>
 *
 * <p>Quyen doc Uri chia se duoc cap cho DUNG Activity nay va chi song den khi no ket
 * thuc. Vi app khong luu ban sao anh, khong con cach nao doc tre: chuyen Uri sang man
 * khac roi moi mo thi quyen do co the da het. Vi vay day la noi duy nhat co the doc,
 * va man goi y chi nhan lai ba con so dem duoc.
 *
 * <p>Doc anh mat khoang mot phan tu giay moi anh nen phai o luong nen. Man hinh trong
 * suot va {@code noHistory} de nguoi dung khong thay man trang nhay len giua duong;
 * mot Toast ngan cho biet may dang lam viec.
 */
public class ShareReceiptActivity extends AppCompatActivity {

    private static final String FAILED =
            "\u0110\u1ecdc \u1ea3nh giao d\u1ecbch th\u1ea5t b\u1ea1i";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final List<Uri> images = collect(getIntent());
        if (images.isEmpty()) {
            fail();
            return;
        }

        Toast.makeText(this,
                "\u0110ang \u0111\u1ecdc \u1ea3nh giao d\u1ecbch\u2026",
                Toast.LENGTH_SHORT).show();

        Db.io(() -> {
            ReceiptImporter.Result read;
            try {
                read = ReceiptImporter.ingest(this, images);
            } catch (Throwable error) {
                read = new ReceiptImporter.Result();
                read.failed = images.size();
            }
            final ReceiptImporter.Result result = read;

            Db.ui(() -> {
                if (isFinishing()) return;
                Intent go = SuggestionsActivity.withResult(this, result);
                go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(go);
                finish();
            });
        });
    }

    /** Gom anh tu ca hai kieu chia se: mot anh va nhieu anh. */
    private List<Uri> collect(@Nullable Intent intent) {
        List<Uri> out = new ArrayList<>();
        if (intent == null) return out;

        String action = intent.getAction();
        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) return out;

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
        return out;
    }

    private void fail() {
        Toast.makeText(this, FAILED, Toast.LENGTH_LONG).show();
        finish();
    }
}
