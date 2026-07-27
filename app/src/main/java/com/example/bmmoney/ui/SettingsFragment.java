package com.example.bmmoney.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.remote.FirebaseSyncManager;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Man Cai dat: ho so, ngan sach thang, sao luu / dong bo va xuat du lieu. */
public class SettingsFragment extends Fragment {

    private final SimpleDateFormat stamp = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);

        final EditText name = root.findViewById(R.id.edt_name);
        final EditText budget = root.findViewById(R.id.edt_budget);
        name.setText(Prefs.userName(requireContext()));
        budget.setText(Money.plain(Prefs.budget(requireContext())));
        updateAvatar(Prefs.userName(requireContext()));

        name.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void onChanged(String value) {
                Prefs.setUserName(requireContext(), value.isEmpty() ? "b\u1ea1n" : value);
                updateAvatar(value);
            }
        });
        budget.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void onChanged(String value) {
                String digits = value.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    Prefs.setBudget(requireContext(), Double.parseDouble(digits));
                }
            }
        });

        showLastBackup();

        root.findViewById(R.id.btn_backup_now).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backup();
            }
        });
        root.findViewById(R.id.btn_sync_now).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sync();
            }
        });
        root.findViewById(R.id.btn_export_csv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportCsv();
            }
        });
        root.findViewById(R.id.btn_export_pdf).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Xu\u1ea5t PDF s\u1ebd \u0111\u01b0\u1ee3c b\u1ed5 sung, t\u1ea1m th\u1eddi d\u00f9ng CSV", Toast.LENGTH_SHORT).show();
            }
        });

        return root;
    }

    private void updateAvatar(String name) {
        TextView avatar = root.findViewById(R.id.tv_avatar);
        if (avatar == null) return;
        String initials = "";
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty()) initials += part.substring(0, 1).toUpperCase(Locale.getDefault());
        }
        if (initials.length() > 2) initials = initials.substring(initials.length() - 2);
        avatar.setText(initials.isEmpty() ? "B" : initials);
    }

    private void showLastBackup() {
        long last = Prefs.lastBackup(requireContext());
        TextView view = root.findViewById(R.id.tv_last_backup);
        if (view == null) return;
        view.setText(last == 0
                ? "Ch\u01b0a sao l\u01b0u l\u1ea7n n\u00e0o"
                : "Sao l\u01b0u l\u1ea7n cu\u1ed1i: " + stamp.format(new Date(last)));
    }

    private void backup() {
        try {
            new FirebaseSyncManager(requireContext()).uploadAllLocal();
            Prefs.setLastBackup(requireContext(), System.currentTimeMillis());
            showLastBackup();
            Toast.makeText(getContext(), "\u0110ang sao l\u01b0u l\u00ean cloud", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(getContext(), "Ch\u01b0a c\u1ea5u h\u00ecnh Firebase", Toast.LENGTH_SHORT).show();
        }
    }

    private void sync() {
        try {
            new FirebaseSyncManager(requireContext()).downloadToLocal(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), "\u0110\u00e3 \u0111\u1ed3ng b\u1ed9 d\u1eef li\u1ec7u", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Throwable e) {
            Toast.makeText(getContext(), "Ch\u01b0a c\u1ea5u h\u00ecnh Firebase", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCsv() {
        try {
            List<TransactionEntity> all = AppDatabase.getInstance(requireContext())
                    .transactionDao().getAllTransactions();
            File file = new File(requireContext().getExternalFilesDir(null), "bmmoney_export.csv");
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write("id,tieu_de,so_tien,loai,danh_muc,ghi_chu,ngay\n");
            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            if (all != null) {
                for (TransactionEntity t : all) {
                    writer.write(t.getId() + ",\"" + safe(t.getTitle()) + "\"," + (long) t.getAmount() + ","
                            + safe(t.getType()) + ",\"" + safe(t.getCategory()) + "\",\"" + safe(t.getNote())
                            + "\"," + df.format(new Date(t.getDate())) + "\n");
                }
            }
            writer.close();
            Toast.makeText(getContext(), "\u0110\u00e3 xu\u1ea5t: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Kh\u00f4ng xu\u1ea5t \u0111\u01b0\u1ee3c CSV", Toast.LENGTH_SHORT).show();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }

    /** TextWatcher gon nhe. */
    private abstract static class SimpleWatcher implements TextWatcher {
        public abstract void onChanged(String value);

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
