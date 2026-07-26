package com.example.bmmoney;

import android.content.Intent; import android.os.Bundle; import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity; import androidx.recyclerview.widget.LinearLayoutManager; import androidx.recyclerview.widget.RecyclerView;
import com.example.bmmoney.adapter.TransactionAdapter; import com.example.bmmoney.data.AppDatabase; import com.example.bmmoney.data.TransactionEntity; import com.google.android.material.floatingactionbutton.FloatingActionButton; import com.example.bmmoney.remote.FirebaseSyncManager;
import java.text.NumberFormat; import java.util.List; import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView tvBalance, tvIncome, tvExpense; private RecyclerView recyclerView; private FloatingActionButton fabAdd; private AppDatabase database; private TransactionAdapter adapter;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); ThemeManager.apply(this); database = AppDatabase.getInstance(this); initViews(); setupRecyclerView(); setupNavigation(); }
    private void setupNavigation() { fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class))); findViewById(R.id.navHome).setOnClickListener(v -> {}); findViewById(R.id.navCalendar).setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class))); findViewById(R.id.navReport).setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class))); findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class))); }
    @Override protected void onResume() { super.onResume(); loadData(); new FirebaseSyncManager(this).downloadToLocal(this::loadData); }
    private void initViews() { tvBalance=findViewById(R.id.tvBalance); tvIncome=findViewById(R.id.tvIncome); tvExpense=findViewById(R.id.tvExpense); recyclerView=findViewById(R.id.recyclerTransactions); fabAdd=findViewById(R.id.fabAdd); }
    private void setupRecyclerView() { recyclerView.setLayoutManager(new LinearLayoutManager(this)); adapter = new TransactionAdapter(); recyclerView.setAdapter(adapter); }
    private void loadData() { List<TransactionEntity> transactions = database.transactionDao().getAllTransactions(); Double incomeValue=database.transactionDao().getTotalIncome(); Double expenseValue=database.transactionDao().getTotalExpense(); double income=incomeValue==null?0:incomeValue; double expense=expenseValue==null?0:expenseValue; tvIncome.setText(currencyFormat.format(income)); tvExpense.setText(currencyFormat.format(expense)); tvBalance.setText(currencyFormat.format(income-expense)); adapter.setTransactions(transactions); }
}
