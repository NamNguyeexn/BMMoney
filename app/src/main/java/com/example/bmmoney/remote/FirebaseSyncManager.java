package com.example.bmmoney.remote;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseSyncManager {
    private static final String OWNER_ID = "nam_personal_wallet";

    private final Context context;
    private final AppDatabase db;
    private final FirebaseFirestore firestore;

    public FirebaseSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    private com.google.firebase.firestore.CollectionReference transactionsRef() {
        return firestore.collection("personal_wallets")
                .document(OWNER_ID)
                .collection("transactions");
    }

    private com.google.firebase.firestore.DocumentReference themeRef() {
        return firestore.collection("personal_wallets")
                .document(OWNER_ID)
                .collection("settings")
                .document("theme");
    }

    public void uploadTransaction(TransactionEntity t) {
        Map<String, Object> data = new HashMap<>();
        data.put("localId", t.getId());
        data.put("title", t.getTitle());
        data.put("amount", t.getAmount());
        data.put("type", t.getType());
        data.put("category", t.getCategory());
        data.put("note", t.getNote());
        data.put("date", t.getDate());
        data.put("createdAt", System.currentTimeMillis());

        // Dùng date + localId làm document id để hạn chế trùng khi sync lại.
        String docId = t.getDate() + "_" + t.getId();
        transactionsRef().document(docId).set(data);
    }

    public void uploadAllLocal() {
        List<TransactionEntity> all = db.transactionDao().getAllTransactions();
        for (TransactionEntity t : all) uploadTransaction(t);
    }

    public void downloadToLocal(@Nullable Runnable done) {
        transactionsRef()
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        String title = d.getString("title");
                        Double amount = d.getDouble("amount");
                        String type = d.getString("type");
                        String category = d.getString("category");
                        String note = d.getString("note");
                        Long date = d.getLong("date");
                        if (title == null || amount == null || type == null || category == null || date == null) continue;

                        // Bản cá nhân tối giản: chỉ tải về khi máy local trống để tránh trùng dữ liệu.
                        // Khi cần restore máy mới, mở app lần đầu sẽ kéo dữ liệu cloud về.
                        if (db.transactionDao().getAllTransactions().isEmpty()) {
                            db.transactionDao().insert(new TransactionEntity(title, amount, type, category, note == null ? "" : note, date));
                        }
                    }
                    if (done != null) done.run();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Không tải được dữ liệu online", Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                });
    }

    public void saveTheme(Map<String, Object> theme) {
        themeRef().set(theme);
    }
}
