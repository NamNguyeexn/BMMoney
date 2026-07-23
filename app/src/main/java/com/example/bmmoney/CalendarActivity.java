package com.example.bmmoney;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bmmoney.adapter.TransactionAdapter;
import com.example.bmmoney.data.AppDatabase;

import java.util.Calendar;

public class CalendarActivity extends AppCompatActivity {

    private AppDatabase db;
    private TransactionAdapter adapter;
    private CalendarView calendarView;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_calendar); ThemeManager.apply(this);

        db = AppDatabase.getInstance(this);
        tvTitle = findViewById(R.id.tvCalendarTitle);
        calendarView = findViewById(R.id.calendarView);

        RecyclerView rv = findViewById(R.id.recyclerCalendar);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        rv.setAdapter(adapter);

        calendarView.setOnDateChangeListener((v, y, m, d) -> loadDay(y, m, d));

        Calendar c = Calendar.getInstance();
        loadDay(
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );

        setupNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Calendar c = Calendar.getInstance();
        loadDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    private void loadDay(int y, int m, int d) {
        Calendar start = Calendar.getInstance();
        start.set(y, m, d, 0, 0, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        tvTitle.setText("Giao dịch ngày " + d + "/" + (m + 1) + "/" + y);
        adapter.setTransactions(
                db.transactionDao().getTransactionsByDateRange(
                        start.getTimeInMillis(),
                        end.getTimeInMillis()
                )
        );
    }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(CalendarActivity.this, MainActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(CalendarActivity.this, AddTransactionActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.navCalendar).setOnClickListener(v -> {
            // Current screen
        });

        findViewById(R.id.navReport).setOnClickListener(v -> {
            Intent intent = new Intent(CalendarActivity.this, ReportActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
}
