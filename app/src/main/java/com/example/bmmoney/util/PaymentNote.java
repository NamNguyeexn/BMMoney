package com.example.bmmoney.util;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Ban va 30/09 (v6). Phuong thuc thanh toan dang nam CHUNG trong cot note.
 *
 * <p>Man Them ghi hai kieu:</p>
 * <ul>
 *   <li>Chi tieu: {@code "ghi chu · 🏦 Chuyen khoan"} (phuong thuc dung SAU);</li>
 *   <li>Vay / no: {@code "🏦 Chuyen khoan · ghi chu"} (phuong thuc dung TRUOC);</li>
 *   <li>Khong co ghi chu: chi mot minh {@code "🏦 Chuyen khoan"}.</li>
 * </ul>
 *
 * <p>Lop nay tach ra / ghep lai dung hai kieu do, de man Sua va phan dong bo
 * (cloud luu {@code paymentMethod} rieng) khong phai doan chuoi o nhieu noi.
 * Room CHUA doi cau truc - note duoi may van giu nguyen kieu cu.</p>
 */
public final class PaymentNote {

    private PaymentNote() {
    }

    public static final String SEP = " \u00b7 ";

    /** Nhan hien thi - PHAI khop tung chu voi danh sach PAYMENTS trong AddExpenseFragment. */
    public static final List<String> LABELS = Arrays.asList(
            "\ud83c\udfe6 Chuy\u1ec3n kho\u1ea3n",
            "\ud83d\udcb5 Ti\u1ec1n m\u1eb7t",
            "\ud83d\udcb3 Th\u1ebb t\u00edn d\u1ee5ng",
            "\ud83d\udcf1 V\u00ed \u0111i\u1ec7n t\u1eed",
            "\ud83e\uddfe Kh\u00e1c");

    /** Ma luu tren cloud, cung thu tu voi {@link #LABELS}. */
    public static final List<String> CODES = Arrays.asList("BANK", "CASH", "CARD", "EWALLET", "OTHER");

    /** Ket qua tach: phuong thuc (nhan hien thi, co the null) + phan ghi chu thuan. */
    public static final class Parts {
        @Nullable public final String label;
        public final String text;

        Parts(@Nullable String label, String text) {
            this.label = label;
            this.text = text;
        }

        @Nullable
        public String code() {
            return codeOf(label);
        }
    }

    public static Parts split(@Nullable String note) {
        String s = note == null ? "" : note.trim();
        if (s.isEmpty()) return new Parts(null, "");
        for (String label : LABELS) {
            if (s.equals(label)) return new Parts(label, "");
            if (s.endsWith(SEP + label)) {
                return new Parts(label, s.substring(0, s.length() - (SEP + label).length()).trim());
            }
            if (s.startsWith(label + SEP)) {
                return new Parts(label, s.substring((label + SEP).length()).trim());
            }
        }
        return new Parts(null, s);
    }

    /**
     * Ghep lai dung kieu man Them dang ghi.
     *
     * @param debtKind true voi BORROW / LEND / REPAY / COLLECT (phuong thuc dung truoc)
     */
    public static String join(boolean debtKind, @Nullable String label, @Nullable String text) {
        String t = text == null ? "" : text.trim();
        if (label == null || label.isEmpty()) return t;
        if (t.isEmpty()) return label;
        return debtKind ? label + SEP + t : t + SEP + label;
    }

    @Nullable
    public static String codeOf(@Nullable String label) {
        int i = label == null ? -1 : LABELS.indexOf(label);
        return i < 0 ? null : CODES.get(i);
    }

    @Nullable
    public static String labelOf(@Nullable String code) {
        int i = code == null ? -1 : CODES.indexOf(code);
        return i < 0 ? null : LABELS.get(i);
    }
}
