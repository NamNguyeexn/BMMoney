package com.example.bmmoney.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bmmoney.R;

/**
 * Man them ghi chu mo tu mot goi y.
 *
 * <p>Dung lai chinh AddExpenseFragment de hai duong nhap lieu khong bao gio lech nhau,
 * chi khac o cho form da dien san va nut quay lai tra ve man goi y.
 */
public class AddNoteActivity extends AppCompatActivity {

    /** Mo man them ghi chu voi du lieu dien san tu mot goi y. */
    public static Intent from(Context context, int suggestionId, String title, long amount,
                              String type, @Nullable String category, long date,
                              @Nullable String note) {
        Intent intent = new Intent(context, AddNoteActivity.class);
        intent.putExtra(AddExpenseFragment.ARG_SUGGESTION, suggestionId);
        intent.putExtra(AddExpenseFragment.ARG_TITLE, title);
        intent.putExtra(AddExpenseFragment.ARG_AMOUNT, amount);
        intent.putExtra(AddExpenseFragment.ARG_TYPE, type);
        intent.putExtra(AddExpenseFragment.ARG_CATEGORY, category == null ? "" : category);
        intent.putExtra(AddExpenseFragment.ARG_DATE, date);
        intent.putExtra(AddExpenseFragment.ARG_NOTE, note == null ? "" : note);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        if (savedInstanceState == null) {
            Bundle args = new Bundle();
            Intent intent = getIntent();
            if (intent != null && intent.getExtras() != null) {
                args.putAll(intent.getExtras());
            }
            args.putBoolean(AddExpenseFragment.ARG_STANDALONE, true);

            AddExpenseFragment fragment = new AddExpenseFragment();
            fragment.setArguments(args);

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container_add_note, fragment)
                    .commit();
        }
    }
}
