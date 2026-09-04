package com.example.bmmoney.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bmmoney.R;

import java.util.ArrayList;

/**
 * Man doc anh giao dich, mo tu nut chuong o goc tren ben phai trang chu, hoac tu
 * bang chia se cua he thong khi nguoi dung chia se anh tu app ngan hang.
 */
public class SuggestionsActivity extends AppCompatActivity {

    /** Danh sach anh can doc ngay khi man mo ra. La Uri cua nguoi dung, khong phai ban sao. */
    private static final String EXTRA_IMAGES = "images";

    /**
     * Mo man doc anh voi nhung anh vua duoc chia se.
     *
     * <p>Uri duoc dat vao CA extra va ClipData. Extra la de doc danh sach; ClipData
     * kem co {@code FLAG_GRANT_READ_URI_PERMISSION} moi la thu thuc su cap cho man nay
     * quyen doc anh - khong co no thi mo anh se bao thieu quyen.
     */
    public static Intent withImages(Context context, ArrayList<Uri> images) {
        Intent intent = new Intent(context, SuggestionsActivity.class);
        intent.putParcelableArrayListExtra(EXTRA_IMAGES, images);

        if (!images.isEmpty()) {
            ClipData clip = new ClipData("images", new String[]{"image/*"},
                    new ClipData.Item(images.get(0)));
            for (int i = 1; i < images.size(); i++) {
                clip.addItem(new ClipData.Item(images.get(i)));
            }
            intent.setClipData(clip);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggestions);

        if (savedInstanceState == null) {
            SuggestionsFragment fragment = new SuggestionsFragment();

            ArrayList<Uri> images = images(getIntent());
            if (!images.isEmpty()) {
                Bundle args = new Bundle();
                args.putParcelableArrayList(SuggestionsFragment.ARG_IMAGES, images);
                fragment.setArguments(args);
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container_suggestion_screen, fragment)
                    .commit();
        }
    }

    private ArrayList<Uri> images(@Nullable Intent intent) {
        ArrayList<Uri> out = new ArrayList<>();
        if (intent == null) return out;
        ArrayList<Uri> extra = intent.getParcelableArrayListExtra(EXTRA_IMAGES);
        if (extra != null) {
            for (Uri uri : extra) {
                if (uri != null) out.add(uri);
            }
        }
        return out;
    }
}
