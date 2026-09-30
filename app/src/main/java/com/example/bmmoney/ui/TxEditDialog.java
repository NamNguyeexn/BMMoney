package com.example.bmmoney.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import android.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryEntity;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.LoanEntity;
import com.example.bmmoney.data.TransactionEntity;
import com.example.bmmoney.data.TxRow;
import com.example.bmmoney.util.AutoBackup;
import com.example.bmmoney.util.Categories;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.PaymentNote;
import com.example.bmmoney.util.Stats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ban va 30/09. SUA mot ban ghi da nhap (chu U trong CRUD).
 *
 * <p>Truoc day ban ghi chi xem / xoa duoc; nhap nham so tien hay danh muc la phai xoa
 * roi nhap lai. Popup nay sua TAI CHO, giu nguyen id nen lich su dong bo khong bi dut.</p>
 *
 * <p>Truong hien ra tuy loai:</p>
 * <ul>
 *   <li>Chi: tieu de, so tien, thoi gian, danh muc, thanh toan, ghi chu;</li>
 *   <li>Thu: tieu de (cach nhan), so tien, thoi gian, ghi chu;</li>
 *   <li>Di vay / Cho vay: + nguoi, han, thanh toan. Sua so tien = sua ca tien goc
 *       cua khoan vay, nhung khong duoc nho hon so da tra / da thu;</li>
 *   <li>Tra no / Thu no: so tien khong duoc vuot so con lai cua khoan goc.</li>
 * </ul>
 *
 * <p>Khong cho doi LOAI giao dich: doi Chi thanh Cho vay nghia la phai tao / huy
 * ca mot khoan vay, cach an toan la xoa roi nhap lai.</p>
 */
public final class TxEditDialog {

    private TxEditDialog() {
    }

    private static final SimpleDateFormat DATE_TIME =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_ONLY =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    /** Trang thai dang sua, gom lai de lambda doc / ghi duoc. */
    private static final class Form {
        long time;
        long due;
        String category;
        String payment;
    }

    public static void show(final Context context, final TxRow row,
                            @Nullable final TxDialog.OnChanged onChanged) {
        if (context == null || row == null || row.tx == null) return;

        final String type = Stats.normalize(row.getType());
        final boolean expense = Stats.EXPENSE.equals(type);
        final boolean debtKind = Stats.isDebtKind(type);
        final boolean loanHead = Stats.BORROW.equals(type) || Stats.LEND.equals(type);
        final boolean settlement = Stats.REPAY.equals(type) || Stats.COLLECT.equals(type);

        final PaymentNote.Parts parts = PaymentNote.split(row.getNote());
        final Form form = new Form();
        form.time = row.getDate();
        form.due = row.dueMillis();
        form.category = row.getCategory() == null ? "" : row.getCategory();
        form.payment = parts.label;

        // ------------------------------------------------------------ bo cuc
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_dialog);
        int pad = dp(context, 20);
        box.setPadding(pad, pad, pad, pad);

        TextView heading = label(context, "S\u1eeda " + Stats.typeName(type).toLowerCase(Locale.getDefault()));
        heading.setTextSize(18f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(ContextCompat.getColor(context, R.color.dark_green));
        box.addView(heading);

        final EditText title = field(context, box, loanHead ? "Ti\u00eau \u0111\u1ec1" : "M\u00f4 t\u1ea3",
                row.getTitle(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        final EditText amount = field(context, box, "S\u1ed1 ti\u1ec1n (\u0111)",
                String.valueOf(row.getAmount()), InputType.TYPE_CLASS_NUMBER);

        final TextView time = picker(context, box, "Th\u1eddi gian", DATE_TIME.format(new Date(form.time)));
        time.setOnClickListener(v -> DateTimeDialog.show(context, form.time, picked -> {
            form.time = picked;
            time.setText(DATE_TIME.format(new Date(picked)));
        }));

        final EditText person = loanHead
                ? field(context, box, Stats.LEND.equals(type) ? "Ng\u01b0\u1eddi vay" : "Ng\u01b0\u1eddi cho vay",
                        row.personOrEmpty(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                : null;

        if (loanHead) {
            final TextView due = picker(context, box, Stats.LEND.equals(type) ? "H\u1ea1n \u0111\u00f2i" : "H\u1ea1n tr\u1ea3",
                    dueText(form.due));
            due.setOnClickListener(v -> {
                List<String> options = new ArrayList<>();
                options.add("Ch\u1ecdn ng\u00e0y\u2026");
                options.add("Kh\u00f4ng \u0111\u1eb7t h\u1ea1n");
                SelectDialog.show(context, "H\u1ea1n", options, null, (index, value) -> {
                    if (index == 1) {
                        form.due = 0L;
                        due.setText(dueText(0L));
                    } else {
                        DateTimeDialog.show(context, form.due > 0 ? form.due : System.currentTimeMillis(), picked -> {
                            form.due = picked;
                            due.setText(dueText(picked));
                        });
                    }
                });
            });
        }

        if (expense) {
            final TextView cat = picker(context, box, "Danh m\u1ee5c",
                    form.category.isEmpty() ? "Ch\u01b0a ch\u1ecdn" : form.category);
            cat.setOnClickListener(v -> {
                final List<String> names = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                String selected = null;
                for (Categories.Item item : Categories.all(context)) {
                    if (item.name == null || item.name.isEmpty()) continue;
                    names.add(item.name);
                    labels.add(item.label());
                    if (item.name.equals(form.category)) selected = item.label();
                }
                SelectDialog.show(context, "Danh m\u1ee5c", labels, selected, (index, value) -> {
                    form.category = names.get(index);
                    cat.setText(value);
                });
            });
        }

        if (expense || debtKind) {
            final TextView pay = picker(context, box, "Thanh to\u00e1n",
                    form.payment == null ? "Kh\u00f4ng ghi" : form.payment);
            pay.setOnClickListener(v -> SelectDialog.show(context, "Thanh to\u00e1n",
                    PaymentNote.LABELS, form.payment, (index, value) -> {
                        form.payment = value;
                        pay.setText(value);
                    }));
        }

        final EditText note = field(context, box, "Ghi ch\u00fa", parts.text,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setSingleLine(false);
        note.setMaxLines(4);

        // ------------------------------------------------------------ nut bam
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(context, 20);
        actions.setLayoutParams(alp);
        TextView cancel = button(context, "H\u1ee7y", false);
        TextView save = button(context, "L\u01b0u", true);
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).rightMargin = dp(context, 8);
        actions.addView(cancel);
        actions.addView(save);
        box.addView(actions);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            final long newAmount;
            try {
                String digits = amount.getText().toString().replaceAll("[^0-9]", "");
                newAmount = digits.isEmpty() ? 0L : Long.parseLong(digits);
            } catch (NumberFormatException e) {
                amount.setError("S\u1ed1 ti\u1ec1n qu\u00e1 l\u1edbn");
                return;
            }
            if (newAmount <= 0) {
                amount.setError("Nh\u1eadp s\u1ed1 ti\u1ec1n l\u1edbn h\u01a1n 0");
                return;
            }
            final String newPerson = person == null ? null : person.getText().toString().trim();
            if (person != null && newPerson.isEmpty()) {
                person.setError("Ghi t\u00ean ng\u01b0\u1eddi nh\u00e9");
                return;
            }
            if (expense && form.category.isEmpty()) {
                Toast.makeText(context, "Ch\u1ecdn m\u1ed9t danh m\u1ee5c nh\u00e9", Toast.LENGTH_SHORT).show();
                return;
            }
            String t = title.getText().toString().trim();
            if (t.isEmpty()) t = expense ? form.category : (newPerson != null ? newPerson : Stats.typeName(type));
            final String newTitle = t;
            final String newNote = PaymentNote.join(debtKind, form.payment, note.getText().toString());
            final Context app = context.getApplicationContext();
            final int id = row.getId();
            final String loanId = row.loanIdOrEmpty();

            Db.io(() -> {
                String error = null;
                try {
                    AppDatabase db = AppDatabase.getInstance(app);
                    final String[] problem = new String[1];
                    db.runInTransaction(() -> {
                        TransactionEntity tx = db.transactionDao().rawById(id);
                        if (tx == null) {
                            problem[0] = "B\u1ea3n ghi kh\u00f4ng c\u00f2n t\u1ed3n t\u1ea1i";
                            return;
                        }
                        long now = System.currentTimeMillis();
                        Double paidRaw = loanId.isEmpty() ? null : db.transactionDao().paidOfLoan(loanId);
                        long paid = paidRaw == null ? 0L : Math.round(paidRaw);

                        if (loanHead && !loanId.isEmpty() && newAmount < paid) {
                            problem[0] = "Kho\u1ea3n n\u00e0y \u0111\u00e3 tr\u1ea3/thu " + Money.vnd(paid)
                                    + ", ti\u1ec1n g\u1ed1c kh\u00f4ng th\u1ec3 nh\u1ecf h\u01a1n";
                            return;
                        }
                        if (settlement && !loanId.isEmpty()) {
                            LoanEntity head = db.loanDao().byId(loanId);
                            if (head != null) {
                                long remainingIfEdited = head.getPrincipal() - (paid - tx.getAmount());
                                if (newAmount > remainingIfEdited) {
                                    problem[0] = "V\u01b0\u1ee3t s\u1ed1 c\u00f2n l\u1ea1i c\u1ee7a kho\u1ea3n g\u1ed1c ("
                                            + Money.vnd(remainingIfEdited) + ")";
                                    return;
                                }
                            }
                        }

                        tx.setTitle(newTitle);
                        tx.setAmount(newAmount);
                        tx.setDate(form.time);
                        tx.setNote(newNote);
                        if (expense) {
                            tx.setCategoryId(db.categoryDao().ensure(form.category, CategoryEntity.FALLBACK_EMOJI));
                        }
                        Integer partnerId = tx.getPartnerId();
                        if (loanHead) {
                            partnerId = db.partnerDao().ensure(newPerson);
                            tx.setPartnerId(partnerId);
                            tx.setDueDate(form.due);
                        }
                        tx.setUpdatedAt(now);
                        db.transactionDao().update(tx);

                        if (loanHead && !loanId.isEmpty()) {
                            LoanEntity head = db.loanDao().byId(loanId);
                            if (head != null) {
                                head.setPrincipal(newAmount);
                                head.setPartnerId(partnerId);
                                head.setOpenedDate(form.time);
                                head.setDueDate(form.due);
                                // Tien goc moi da bang so da tra -> khoan tu tat toan
                                head.setSettled(paid >= newAmount ? 1 : head.getSettled());
                                head.setUpdatedAt(now);
                                db.loanDao().update(head);
                            }
                        }
                        if (settlement && !loanId.isEmpty()) {
                            // Tra / thu du thi danh dau tat toan ca khoan goc
                            LoanEntity head = db.loanDao().byId(loanId);
                            Double after = db.transactionDao().paidOfLoan(loanId);
                            if (head != null && after != null) {
                                int settled = Math.round(after) >= head.getPrincipal() ? 1 : 0;
                                if (settled != head.getSettled()) {
                                    db.loanDao().setSettled(loanId, settled, now);
                                }
                            }
                        }
                    });
                    error = problem[0];
                } catch (Throwable e) {
                    error = "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c: " + e.getMessage();
                }
                if (error == null) AutoBackup.scheduleSoon(app);
                final String shown = error;
                Db.ui(() -> {
                    if (shown != null) {
                        amount.setError(shown);
                        amount.requestFocus();
                        return;
                    }
                    dialog.dismiss();
                    if (onChanged != null) onChanged.onChanged();
                });
            });
        });

        dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    // ------------------------------------------------------------ dung o nhap
    private static TextView label(Context context, String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(13f);
        v.setTextColor(ContextCompat.getColor(context, R.color.olive));
        return v;
    }

    private static void addLabel(Context context, LinearLayout parent, String text) {
        TextView v = label(context, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 14);
        lp.bottomMargin = dp(context, 4);
        v.setLayoutParams(lp);
        parent.addView(v);
    }

    private static EditText field(Context context, LinearLayout parent, String caption,
                                  @Nullable String value, int inputType) {
        addLabel(context, parent, caption);
        EditText e = new EditText(context);
        e.setBackgroundResource(R.drawable.bg_field);
        e.setInputType(inputType);
        e.setText(value == null ? "" : value);
        e.setTextSize(16f);
        e.setTextColor(ContextCompat.getColor(context, R.color.dark_green));
        e.setMinHeight(dp(context, 48));
        int p = dp(context, 12);
        e.setPadding(p, p / 2, p, p / 2);
        parent.addView(e, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private static TextView picker(Context context, LinearLayout parent, String caption, String value) {
        addLabel(context, parent, caption);
        TextView v = new TextView(context);
        v.setBackgroundResource(R.drawable.bg_field);
        v.setText(value);
        v.setTextSize(16f);
        v.setTextColor(ContextCompat.getColor(context, R.color.dark_green));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinHeight(dp(context, 48));
        int p = dp(context, 12);
        v.setPadding(p, 0, p, 0);
        v.setClickable(true);
        v.setFocusable(true);
        parent.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    private static TextView button(Context context, String text, boolean primary) {
        TextView b = new TextView(context);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        int p = dp(context, 12);
        b.setPadding(p, p, p, p);
        b.setBackgroundResource(primary ? R.drawable.bg_pill_olive : R.drawable.bg_pill_cream);
        b.setTextColor(ContextCompat.getColor(context, primary ? R.color.cream : R.color.dark_green));
        b.setClickable(true);
        b.setFocusable(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private static String dueText(long due) {
        return due > 0 ? DATE_ONLY.format(new Date(due)) : "Kh\u00f4ng \u0111\u1eb7t h\u1ea1n";
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
