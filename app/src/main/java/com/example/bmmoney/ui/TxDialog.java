package com.example.bmmoney.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.TypeStyle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ban va 02/08. Popup chi tiet mot ban ghi, dung dung chu de cua app thay cho
 * AlertDialog mac dinh cua he thong.
 *
 * <p>Dung chung cho man Tim kiem, Trang chu va man Lich de ba noi hien thi
 * giong het nhau. Voi khoan Cho vay / No phai tra, popup hien them nguoi lien
 * quan, han doi hoac han tra, so ngay con lai va nut danh dau da tat toan.</p>
 */
public final class TxDialog {

    /** Goi lai sau khi nguoi dung xoa hoac tat toan de man dang mo nap lai. */
    public interface OnChanged {
        void onChanged();
    }

    private static final SimpleDateFormat DATE_TIME =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_ONLY =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private TxDialog() {
    }

    public static void show(Context context, TransactionEntity t) {
        show(context, t, false, null);
    }

    /**
     * @param allowDelete co hien nut Xoa hay khong
     * @param onChanged   goi lai khi du lieu doi (xoa hoac tat toan)
     */
    public static void show(final Context context, final TransactionEntity t,
                            boolean allowDelete, final OnChanged onChanged) {
        if (context == null || t == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_tx, null, false);
        final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();

        Window window = dialog.getWindow();
        if (window != null) {
            // Bo nen trang mac dinh de chi con thay nen kem bo goc cua layout
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Ban va 03/08: chuan hoa loai, ban ghi cu con luu "DEBT"
        final String type = Stats.normalize(t.getType());
        final boolean debtKind = Stats.isDebtKind(type);
        final boolean loan = Stats.BORROW.equals(type) || Stats.LEND.equals(type);
        final boolean receivable = Stats.isReceivable(type) || Stats.COLLECT.equals(type);

        TextView icon = view.findViewById(R.id.tv_tx_icon);
        icon.setText(Stats.typeGlyph(type));
        icon.setBackgroundResource(TypeStyle.bg(type));

        text(view, R.id.tv_tx_title, title(t));
        text(view, R.id.tv_tx_kind, Stats.typeName(type));

        TextView amount = view.findViewById(R.id.tv_tx_amount);
        amount.setText(Stats.typeSign(type) + Money.vnd(t.getAmount()));
        amount.setTextColor(ContextCompat.getColor(context, TypeStyle.color(type)));

        LinearLayout rows = view.findViewById(R.id.container_tx_rows);
        rows.removeAllViews();

        if (debtKind) {
            // Bon loai cong no deu can biet doi tac va huong tien
            row(rows, receivable ? "Ng\u01b0\u1eddi vay" : "\u0110\u1ed1i t\u00e1c",
                    empty(t.personOrEmpty()) ? "Ch\u01b0a ghi" : t.personOrEmpty());
            row(rows, "Th\u1eddi gian", DATE_TIME.format(new Date(t.getDate())));
            if (loan && t.dueMillis() > 0) {
                row(rows, receivable ? "H\u1ea1n \u0111\u00f2i" : "H\u1ea1n ph\u1ea3i tr\u1ea3",
                        DATE_ONLY.format(new Date(t.dueMillis())) + " \u00b7 " + remain(t.dueMillis()));
            }
            if (loan) {
                row(rows, "Tr\u1ea1ng th\u00e1i", t.isWrittenOff()
                        ? "\u0110\u00e3 x\u00f3a s\u1ed5"
                        : t.isSettled()
                        ? "\u0110\u00e3 t\u1ea5t to\u00e1n"
                        : receivable ? "C\u00f2n ph\u1ea3i thu" : "C\u00f2n ph\u1ea3i tr\u1ea3");
            }
            if (!empty(t.loanIdOrEmpty())) {
                row(rows, "Kho\u1ea3n g\u1ed1c", t.loanIdOrEmpty());
            }
            row(rows, "Thanh to\u00e1n", empty(t.getCategory()) ? "Kh\u00f4ng c\u00f3" : t.getCategory());
        } else {
            row(rows, "Danh m\u1ee5c", empty(t.getCategory()) ? "Kh\u00f4ng c\u00f3" : t.getCategory());
            row(rows, "Th\u1eddi gian", DATE_TIME.format(new Date(t.getDate())));
        }
        row(rows, "Ghi ch\u00fa", empty(t.getNote()) ? "Kh\u00f4ng c\u00f3" : t.getNote());

        if (debtKind) {
            // Nghiep vu moi: cong no CO lam doi so du vi, chi khong tinh vao lai lo
            row(rows, "\u1ea2nh h\u01b0\u1edfng",
                    (Stats.walletSign(type) > 0 ? "Ti\u1ec1n v\u00e0o v\u00ed" : "Ti\u1ec1n ra v\u00ed")
                            + " \u00b7 kh\u00f4ng t\u00ednh v\u00e0o l\u00e3i l\u1ed7");
        }

        final TextView settle = view.findViewById(R.id.btn_tx_settle);
        if (loan) {
            // Chi khoan vay GOC moi tat toan duoc; ban ghi tra no / thu no thi khong
            settle.setVisibility(View.VISIBLE);
            settle.setText(t.isSettled()
                    ? "B\u1ecf \u0111\u00e1nh d\u1ea5u t\u1ea5t to\u00e1n"
                    : receivable ? "\u0110\u00e3 thu \u0111\u1ee7 kho\u1ea3n n\u00e0y"
                    : "\u0110\u00e3 tr\u1ea3 xong kho\u1ea3n n\u00e0y");
            settle.setOnClickListener(v -> {
                final int next = t.isSettled() ? 0 : 1;
                final android.content.Context app = context.getApplicationContext();
                Db.io(() -> {
                    AppDatabase.dao(app).setSettled(t.getId(), next);
                    AutoBackup.scheduleSoon(app);
                    Db.ui(() -> {
                        dialog.dismiss();
                        if (onChanged != null) onChanged.onChanged();
                    });
                });
            });
        } else {
            settle.setVisibility(View.GONE);
        }

        TextView delete = view.findViewById(R.id.btn_tx_delete);
        if (allowDelete && onChanged != null) {
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> {
                final android.content.Context app = context.getApplicationContext();
                Db.io(() -> {
                    AppDatabase.dao(app).delete(t);
                    AutoBackup.scheduleSoon(app);
                    Db.ui(() -> {
                        dialog.dismiss();
                        onChanged.onChanged();
                    });
                });
            });
        } else {
            delete.setVisibility(View.GONE);
        }

        view.findViewById(R.id.btn_tx_close).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Tieu de goi y: khoan vay / no lay ten nguoi lam tieu de neu chua dat ten. */
    private static String title(TransactionEntity t) {
        if (!empty(t.getTitle())) return t.getTitle();
        if (!empty(t.personOrEmpty())) return t.personOrEmpty();
        return Stats.typeName(Stats.normalize(t.getType()));
    }

    /** Con bao nhieu ngay toi han, hoac da qua han bao nhieu ngay. */
    public static String remain(long due) {
        long today = startOfDay(System.currentTimeMillis());
        long target = startOfDay(due);
        long days = (target - today) / 86400000L;
        if (days == 0) return "\u0111\u1ebfn h\u1ea1n h\u00f4m nay";
        if (days > 0) return "c\u00f2n " + days + " ng\u00e0y";
        return "qu\u00e1 h\u1ea1n " + (-days) + " ng\u00e0y";
    }

    public static long startOfDay(long time) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void row(LinearLayout parent, String label, String value) {
        Context context = parent.getContext();
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 8);
        line.setLayoutParams(lp);

        TextView left = new TextView(context);
        left.setText(label);
        left.setTextSize(13f);
        left.setTextColor(ContextCompat.getColor(context, R.color.olive));
        left.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView right = new TextView(context);
        right.setText(value);
        right.setTextSize(13f);
        right.setGravity(android.view.Gravity.RIGHT);
        right.setTextColor(ContextCompat.getColor(context, R.color.dark_green));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
        rp.leftMargin = dp(context, 12);
        right.setLayoutParams(rp);

        line.addView(left);
        line.addView(right);
        parent.addView(line);
    }

    /** Gan chu cho mot TextView trong popup, bo qua neu khong tim thay. */
    private static void text(View parent, int id, String value) {
        if (parent == null) return;
        View view = parent.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setText(value);
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
