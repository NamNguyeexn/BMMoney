package com.example.bmmoney;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.adapter.TransactionAdapter;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {
    private AppDatabase db;
    private TransactionAdapter adapter;
    private EditText edtSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ThemeManager.apply(this);

        db = AppDatabase.getInstance(this);
        edtSearch = findViewById(R.id.edtSearch);

        RecyclerView recyclerView = findViewById(R.id.recyclerSearch);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        loadAll();
        setupSearch();
        setupNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        filterTransactions(edtSearch == null ? "" : edtSearch.getText().toString());
    }

    private void loadAll() {
        adapter.setTransactions(db.transactionDao().getAllTransactions());
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }
        });
    }

    private void filterTransactions(String keyword) {
        List<TransactionEntity> all = db.transactionDao().getAllTransactions();
        if (keyword == null || keyword.trim().isEmpty()) {
            adapter.setTransactions(all);
            return;
        }

        String q = keyword.toLowerCase(Locale.ROOT).trim();
        List<TransactionEntity> filtered = new ArrayList<>();

        for (TransactionEntity t : all) {
            String title = t.getTitle() == null ? "" : t.getTitle().toLowerCase(Locale.ROOT);
            String category = t.getCategory() == null ? "" : t.getCategory().toLowerCase(Locale.ROOT);
            String note = t.getNote() == null ? "" : t.getNote().toLowerCase(Locale.ROOT);
            String type = "INCOME".equals(t.getType()) ? "thu nhập income" : "chi tiêu expense";

            if (title.contains(q) || category.contains(q) || note.contains(q) || type.contains(q)) {
                filtered.add(t);
            }
        }

        adapter.setTransactions(filtered);
    }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.navCalendar).setOnClickListener(v -> {});
        findViewById(R.id.fabAdd).setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));
        findViewById(R.id.navReport).setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class)));
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
}
