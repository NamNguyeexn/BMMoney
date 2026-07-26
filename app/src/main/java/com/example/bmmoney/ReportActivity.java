package com.example.bmmoney;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryTotal;
import com.example.bmmoney.data.TypeTotal;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {
    private AppDatabase db;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_report);
        ThemeManager.apply(this);
        db = AppDatabase.getInstance(this);
        setupNav();
        findViewById(R.id.fabCreateReport).setOnClickListener(v -> showCreateReportPopup());
        loadReport();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) loadReport();
    }

    private void loadReport() {
        setupSummary();
        setupPie();
        setupBar();
    }

    private void setupSummary() {
        double income = 0, expense = 0;
        for (TypeTotal t : db.transactionDao().getTotalByType()) {
            if ("INCOME".equals(t.type)) income = t.total;
            if ("EXPENSE".equals(t.type)) expense = t.total;
        }
        ((TextView) findViewById(R.id.tvReportBalance)).setText(money.format(income - expense));
        ((TextView) findViewById(R.id.tvReportIncome)).setText("Thu\n" + money.format(income));
        ((TextView) findViewById(R.id.tvReportExpense)).setText("Chi\n" + money.format(expense));
    }

    private void setupPie() {
        PieChart chart = findViewById(R.id.pieChart);
        ArrayList<PieEntry> entries = new ArrayList<>();
        for (CategoryTotal c : db.transactionDao().getExpenseByCategory()) {
            if (c.total > 0) entries.add(new PieEntry((float) c.total, c.category));
        }
        if (entries.isEmpty()) entries.add(new PieEntry(1, "Chưa có dữ liệu"));

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(new int[]{Color.rgb(18,183,106), Color.rgb(127,86,217), Color.rgb(255,107,107), Color.rgb(255,209,102), Color.rgb(6,214,160)});
        set.setSliceSpace(3f);
        set.setSelectionShift(8f);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(11f);
        set.setValueFormatter(new MoneyShortFormatter());

        chart.setData(new PieData(set));
        chart.setUsePercentValues(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(58f);
        chart.setTransparentCircleRadius(63f);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setCenterText("Chi tiêu\ntheo danh mục");
        chart.setCenterTextColor(Color.WHITE);
        chart.setCenterTextSize(14f);
        chart.setEntryLabelColor(Color.WHITE);
        chart.setEntryLabelTextSize(11f);
        chart.getDescription().setEnabled(false);
        Legend l = chart.getLegend();
        l.setTextColor(Color.WHITE);
        l.setTextSize(11f);
        l.setForm(Legend.LegendForm.CIRCLE);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        chart.animateY(900, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    private void setupBar() {
        BarChart chart = findViewById(R.id.barChart);
        double income = 0, expense = 0;
        for (TypeTotal t : db.transactionDao().getTotalByType()) {
            if ("INCOME".equals(t.type)) income = t.total;
            else if ("EXPENSE".equals(t.type)) expense = t.total;
        }
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, (float) income));
        entries.add(new BarEntry(1, (float) expense));

        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(new int[]{Color.rgb(18,183,106), Color.rgb(240,68,56)});
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(12f);
        set.setValueFormatter(new MoneyShortFormatter());
        BarData data = new BarData(set);
        data.setBarWidth(0.42f);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBarShadow(false);
        chart.setFitBars(true);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisLeft().setGridColor(Color.argb(60,255,255,255));
        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Thu", "Chi"}));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(Color.WHITE);
        x.setDrawGridLines(false);
        chart.animateY(900, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    private void showCreateReportPopup() {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_create_report, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        view.findViewById(R.id.btnReportOverview).setOnClickListener(v -> { dialog.dismiss(); Toast.makeText(this, "Đã tạo báo cáo tổng quan", Toast.LENGTH_SHORT).show(); loadReport(); });
        view.findViewById(R.id.btnReportExpense).setOnClickListener(v -> { dialog.dismiss(); Toast.makeText(this, "Đã tạo báo cáo chi tiêu", Toast.LENGTH_SHORT).show(); setupPie(); });
        view.findViewById(R.id.btnReportCashflow).setOnClickListener(v -> { dialog.dismiss(); Toast.makeText(this, "Đã tạo báo cáo thu - chi", Toast.LENGTH_SHORT).show(); setupBar(); });
        dialog.show();
    }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.navCalendar).setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.fabAdd).setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));
        findViewById(R.id.navReport).setOnClickListener(v -> loadReport());
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    static class MoneyShortFormatter extends ValueFormatter {
        @Override public String getFormattedValue(float value) {
            if (value >= 1000000) return String.format(Locale.US, "%.1ftr", value / 1000000f);
            if (value >= 1000) return String.format(Locale.US, "%.0fk", value / 1000f);
            return String.format(Locale.US, "%.0f", value);
        }
    }
}
