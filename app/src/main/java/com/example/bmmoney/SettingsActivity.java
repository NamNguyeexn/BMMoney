package com.example.bmmoney;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.bmmoney.remote.FirebaseSyncManager;
import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private LinearLayout paletteList;
    private EditText edtCoolorsUrl;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        paletteList = findViewById(R.id.paletteList);
        edtCoolorsUrl = findViewById(R.id.edtCoolorsUrl);
        buildPalettes();
        setupUrlImport();
        findViewById(R.id.btnSyncNow).setOnClickListener(v -> { new FirebaseSyncManager(this).uploadAllLocal(); Toast.makeText(this, "Đã đẩy dữ liệu local lên cloud", Toast.LENGTH_SHORT).show(); });
        setupNav();
        ThemeManager.apply(this);
    }

    private void buildPalettes() {
        paletteList.removeAllViews();
        for (String[] p : ThemeManager.PALETTES) addPaletteRow(p);
    }

    private void addPaletteRow(String[] p) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_palette, paletteList, false);
        ((TextView) row.findViewById(R.id.tvPaletteName)).setText(p[0]);
        int[] swatches = {R.id.swatch1, R.id.swatch2, R.id.swatch3, R.id.swatch4, R.id.swatch5};
        for (int i = 0; i < 5; i++) {
            try { row.findViewById(swatches[i]).setBackgroundColor(Color.parseColor(p[i + 1])); } catch (Exception ignored) {}
        }
        row.setOnClickListener(v -> {
            ThemeManager.savePalette(this, p);
            saveThemeOnline(p);
            ThemeManager.apply(this);
            Toast.makeText(this, "Đã đổi sang bộ màu " + p[0], Toast.LENGTH_SHORT).show();
        });
        paletteList.addView(row);
    }

    private void setupUrlImport() {
        String saved = getSharedPreferences("settings", MODE_PRIVATE).getString("coolors_url", "");
        edtCoolorsUrl.setText(saved);
        edtCoolorsUrl.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("coolors_url", s.toString()).apply();
                parseCoolorsUrl(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    private void parseCoolorsUrl(String url) {
        // Hỗ trợ link dạng https://coolors.co/101828-344054-7f56d9-12b76a-f04438
        int idx = url.lastIndexOf("/");
        if (idx < 0) return;
        String tail = url.substring(idx + 1).trim();
        if (!tail.contains("-")) return;
        String[] raw = tail.split("-");
        if (raw.length < 5) return;
        String[] p = new String[]{"Coolors URL", "#" + raw[0], "#" + raw[1], "#" + raw[2], "#" + raw[3], "#" + raw[4]};
        try {
            for (int i = 1; i <= 5; i++) Color.parseColor(p[i]);
            ThemeManager.savePalette(this, p);
            ThemeManager.apply(this);
        } catch (Exception ignored) {}
    }

    private void saveThemeOnline(String[] p) {
        Map<String, Object> theme = new HashMap<>();
        theme.put("name", p[0]);
        theme.put("bg1", p[1]);
        theme.put("bg2", p[2]);
        theme.put("primary", p[3]);
        theme.put("secondary", p[4]);
        theme.put("accent", p[5]);
        new FirebaseSyncManager(this).saveTheme(theme);
    }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.navCalendar).setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.fabAdd).setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));
        findViewById(R.id.navReport).setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class)));
        findViewById(R.id.navSettings).setOnClickListener(v -> {});
    }
}
