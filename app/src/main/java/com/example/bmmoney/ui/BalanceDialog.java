package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ban va 04/08. KHOAN THU / CHI CAN BANG.
 *
 * <p>So du vi trong app la ket qua cong tru cua tung ban ghi. Tien that trong tui
 * thi khong bao gio khop tuyet doi: quen ghi mot bat pho, tra tien le, chuyen khoan
 * nhan mat phi, hay ghi trung mot khoan. Sau vai tuan hai con so lech nhau va nguoi
 * dung mat long tin vao so lieu cua app.
 *
 * <p>Man nay lam mot viec duy nhat: nguoi dung dem tien that roi nhap vao, app tinh
 * chenh lech va sinh DUNG MOT ban ghi de keo so du ve dung thuc te.
 *
 * <pre>
 * chenhLech = soThucTe - soDuApp
 * chenhLech > 0  ->  mot khoan INCOME  (tien thua ra, app ghi thieu o dau do)
 * chenhLech < 0  ->  mot khoan EXPENSE (tien hut di, co khoan chi chua ghi)
 * chenhLech = 0  ->  khong tao gi ca
 * </pre>
 *
 * <p><b>Vi sao khong lam mot loai giao dich moi:</b> them loai thu bay se phai sua
 * walletBalance, netProfitInRange, TypeStyle, Stats.typeName, moi truy van DAO dang
 * hard-code 'EXPENSE' / 'INCOME', va migration Room. Dung lai INCOME / EXPENSE thi
 * so du vi tu dong dung ma khong sua mot dong nao trong tang ke toan.
 *
 * <p><b>Bu lai:</b> khoan can bang KHONG phai chi tieu hay thu nhap thuc, nen no
 * duoc gan danh muc rieng {@link Stats#CATEGORY_BALANCE} va moi truy van ngan sach,
 * bieu do danh muc, lai lo deu doi sang ban ...Skip de loai danh muc nay ra. Nho vay
 * mot lan can bang 3 trieu khong bop meo the "Phan tich theo danh muc" cua ban.
 */
public final class BalanceDialog {

    /** So tien lech duoi muc nay thi coi nhu da khop, khong sinh ban ghi. */
    private static final double EPSILON = 1d;

    /** Chan tran o nhap de Double.parseDouble khong tran so. */
    private static final int MAX_DIGITS = 15;

    public interface OnDone {
        /** @param difference so tien da can bang, duong la thu, am la chi, 0 la da khop san */
        void onDone(double difference);
    }

    private BalanceDialog() {
    }

    /** So lieu doc san tu database truoc khi mo hop thoai. */
    private static class Snapshot {
        double wallet;
        double adjusted;
        @Nullable TransactionEntity last;
    }

    public static void show(final Context context, final OnDone callback) {
        if (context == null) return;
        final Context app = context.getApplicationContext();

        Db.load(() -> {
            Snapshot s = new Snapshot();
            s.wallet = AppDatabase.dao(app).walletBalance();
            s.adjusted = AppDatabase.dao(app).balanceAdjustTotal(Stats.CATEGORY_BALANCE);
            s.last = AppDatabase.dao(app).latestOfCategory(Stats.CATEGORY_BALANCE);
            return s;
        }, s -> {
            if (s == null) return;
            build(context, app, s, callback);
        });
    }

    private static void build(final Context context, final Context app,
                              final Snapshot snap, final OnDone callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_balance, null, false);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            // Bo nen trang goc vuong cua he thong de lo bo goc cua bg_dialog
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        final TextView appValue = view.findViewById(R.id.tv_bal_app);
        final TextView diffView = view.findViewById(R.id.tv_bal_diff);
        final TextView effectView = view.findViewById(R.id.tv_bal_effect);
        final TextView historyView = view.findViewById(R.id.tv_bal_history);
        final EditText input = view.findViewById(R.id.edt_bal_actual);

        appValue.setText(Money.vnd(snap.wallet));
        historyView.setText(history(snap));

        final Runnable update = () -> {
            Double actual = parse(input);
            if (actual == null) {
                diffView.setText("Ch\u01b0a nh\u1eadp s\u1ed1");
                diffView.setTextColor(color(context, R.color.subtle_text));
                effectView.setText("\u0110\u1ebfm ti\u1ec1n m\u1eb7t, c\u1ed9ng s\u1ed1 d\u01b0 ng\u00e2n h\u00e0ng v\u00e0 v\u00ed \u0111i\u1ec7n t\u1eed r\u1ed3i nh\u1eadp t\u1ed5ng v\u00e0o \u0111\u00e2y.");
                return;
            }
            double diff = actual - snap.wallet;
            if (Math.abs(diff) < EPSILON) {
                diffView.setText("Kh\u1edbp ch\u00ednh x\u00e1c");
                diffView.setTextColor(color(context, R.color.net_positive));
                effectView.setText("Kh\u00f4ng c\u1ea7n t\u1ea1o ghi ch\u00fa n\u00e0o.");
                return;
            }
            boolean surplus = diff > 0;
            diffView.setText((surplus ? "Th\u1eeba " : "Thi\u1ebfu ") + Money.vnd(Math.abs(diff)));
            diffView.setTextColor(color(context, surplus ? R.color.net_positive : R.color.net_negative));
            effectView.setText(surplus
                    ? "S\u1ebd th\u00eam m\u1ed9t kho\u1ea3n THU " + Money.vnd(diff)
                            + " \u2014 ti\u1ec1n th\u1eadt nhi\u1ec1u h\u01a1n, c\u00f3 kho\u1ea3n thu b\u1ea1n ch\u01b0a ghi."
                    : "S\u1ebd th\u00eam m\u1ed9t kho\u1ea3n CHI " + Money.vnd(-diff)
                            + " \u2014 ti\u1ec1n th\u1eadt \u00edt h\u01a1n, c\u00f3 kho\u1ea3n chi b\u1ea1n ch\u01b0a ghi.");
        };

        input.addTextChangedListener(new TextWatcher() {
            private boolean guard = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (guard) return;
                guard = true;
                String digits = s.toString().replaceAll("[^0-9]", "");
                if (digits.length() > MAX_DIGITS) digits = digits.substring(0, MAX_DIGITS);
                String pretty = digits.isEmpty() ? "" : Money.plain(Double.parseDouble(digits));
                if (!pretty.equals(s.toString())) {
                    input.setText(pretty);
                    input.setSelection(pretty.length());
                }
                guard = false;
                update.run();
            }
        });

        update.run();

        view.findViewById(R.id.btn_bal_cancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btn_bal_copy).setOnClickListener(v -> {
            // Loi tat: dat luon so thuc te bang so app dang tinh, de nguoi dung sua nhe di
            input.setText(Money.plain(snap.wallet));
            input.setSelection(input.getText().length());
        });

        view.findViewById(R.id.btn_bal_save).setOnClickListener(v -> {
            Double actual = parse(input);
            if (actual == null) {
                diffView.setText("Nh\u1eadp s\u1ed1 d\u01b0 th\u1ef1c t\u1ebf tr\u01b0\u1edbc \u0111\u00e3 nh\u00e9");
                diffView.setTextColor(color(context, R.color.net_negative));
                return;
            }
            final double diff = actual - snap.wallet;
            dialog.dismiss();

            if (Math.abs(diff) < EPSILON) {
                if (callback != null) callback.onDone(0d);
                return;
            }

            final TransactionEntity entity = entity(diff, actual, snap.wallet);
            Db.io(() -> {
                AppDatabase.dao(app).insert(entity);
                // Gom thay doi roi sao luu mot lan, giong moi giao dich khac
                AutoBackup.scheduleSoon(app);
                Db.ui(() -> {
                    if (callback != null) callback.onDone(diff);
                });
            });
        });

        dialog.show();
    }

    /** Dung INCOME / EXPENSE san co nen tang ke toan khong phai sua gi. */
    private static TransactionEntity entity(double diff, double actual, double wallet) {
        boolean surplus = diff > 0;
        String title = surplus
                ? "C\u00e2n b\u1eb1ng s\u1ed1 d\u01b0 (th\u1eeba)"
                : "C\u00e2n b\u1eb1ng s\u1ed1 d\u01b0 (thi\u1ebfu)";
        String note = "Th\u1ef1c t\u1ebf " + Money.vnd(actual)
                + " \u00b7 App t\u00ednh " + Money.vnd(wallet);
        return new TransactionEntity(title, Math.abs(diff),
                surplus ? Stats.INCOME : Stats.EXPENSE,
                Stats.CATEGORY_BALANCE, note, System.currentTimeMillis());
    }

    private static String history(Snapshot snap) {
        if (snap.last == null) {
            return "B\u1ea1n ch\u01b0a c\u00e2n b\u1eb1ng l\u1ea7n n\u00e0o.";
        }
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi"));
        String when = format.format(new Date(snap.last.getDate()));
        String total = (snap.adjusted >= 0 ? "+" : "\u2212") + Money.vnd(Math.abs(snap.adjusted));
        return "L\u1ea7n g\u1ea7n nh\u1ea5t " + when + " \u00b7 T\u1ed5ng \u0111\u00e3 c\u00e2n b\u1eb1ng " + total;
    }

    @Nullable
    private static Double parse(EditText input) {
        String digits = input.getText().toString().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.length() > MAX_DIGITS) digits = digits.substring(0, MAX_DIGITS);
        return Double.parseDouble(digits);
    }

    private static int color(Context context, int id) {
        return ContextCompat.getColor(context, id);
    }
}
