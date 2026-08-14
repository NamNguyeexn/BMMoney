package com.example.bmmoney.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bmmoney.R;

/**
 * Man hinh goi y, mo tu nut chuong o trang chu hoac tu man cai dat.
 *
 * <p>De rieng thanh mot Activity chu khong them tab vao thanh dieu huong,
 * dung y muon giu thanh dieu huong gon nhu hien tai.
 */
public class SuggestionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggestions);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container_suggestion_screen, new SuggestionsFragment())
                    .commit();
        }
    }
}
