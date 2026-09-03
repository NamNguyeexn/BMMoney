package com.example.bmmoney.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bmmoney.R;
import com.example.bmmoney.ocr.ReceiptImporter;

/**
 * Man doc anh giao dich, mo tu nut chuong o goc tren ben phai trang chu.
 *
 * <p>De rieng thanh mot Activity chu khong them tab vao thanh dieu huong,
 * dung y muon giu thanh dieu huong gon nhu hien tai.
 */
public class SuggestionsActivity extends AppCompatActivity {

    /**
     * Ket qua mot lan doc anh chia se, chi la ba con so dem duoc.
     *
     * <p>{@link ShareReceiptActivity} da doc anh xong truoc khi chuyen sang day, va
     * KHONG chuyen anh theo - app khong luu anh, cung khong truyen anh qua lai giua
     * cac man hinh. Man nay chi can biet du de bao "da doc bao nhieu" hoac bao loi.
     */
    private static final String EXTRA_ADDED = "added";
    private static final String EXTRA_DUPLICATE = "duplicate";
    private static final String EXTRA_FAILED = "failed";

    /** Mo man goi y kem ket qua vua doc duoc tu anh chia se. */
    public static Intent withResult(Context context, ReceiptImporter.Result result) {
        Intent intent = new Intent(context, SuggestionsActivity.class);
        intent.putExtra(EXTRA_ADDED, result.added);
        intent.putExtra(EXTRA_DUPLICATE, result.duplicate);
        intent.putExtra(EXTRA_FAILED, result.failed);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggestions);

        if (savedInstanceState == null) {
            SuggestionsFragment fragment = new SuggestionsFragment();

            Intent from = getIntent();
            if (from != null && from.hasExtra(EXTRA_ADDED)) {
                Bundle args = new Bundle();
                args.putInt(SuggestionsFragment.ARG_ADDED, from.getIntExtra(EXTRA_ADDED, 0));
                args.putInt(SuggestionsFragment.ARG_DUPLICATE, from.getIntExtra(EXTRA_DUPLICATE, 0));
                args.putInt(SuggestionsFragment.ARG_FAILED, from.getIntExtra(EXTRA_FAILED, 0));
                fragment.setArguments(args);
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container_suggestion_screen, fragment)
                    .commit();
        }
    }
}
