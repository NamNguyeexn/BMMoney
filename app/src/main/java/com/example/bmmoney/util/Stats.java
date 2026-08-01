package com.example.bmmoney.util;

import com.example.bmmoney.data.TransactionEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tong hop so lieu tu danh sach giao dich Room.
 *
 * <p><b>Ban va 02/08 - bon loai ghi chu tai chinh.</b></p>
 * <ul>
 *   <li>{@link #EXPENSE} Chi tieu - tien ra khoi vi</li>
 *   <li>{@link #INCOME}  Thu nhap - tien vao vi</li>
 *   <li>{@link #LEND}    Cho vay  - nguoi khac vay cua minh, minh CAN DOI</li>
 *   <li>{@link #DEBT}    No phai tra - minh no nguoi khac, minh CAN TRA</li>
 * </ul>
 *
 * <p><b>Nguyen tac ke toan:</b> LEND va DEBT KHONG lam thay doi vi. Chung khong
 * duoc cong vao tong chi, tong thu, ngan sach hay bieu do tron theo danh muc.
 * Moi ham tinh tien trong lop nay deu loc theo type nen tu dong dam bao dieu do.</p>
 */
public final class Stats {

    public static final String EXPENSE = "EXPENSE";
    public static final String INCOME = "INCOME";
    /** Cho vay: nguoi khac vay cua minh -> minh can doi lai. */
    public static final String LEND = "LEND";
    /** No phai tra: minh no nguoi khac -> minh can tra. */
    public static final String DEBT = "DEBT";

    private Stats() {
    }

    public static class Slice {
        public final String name;
        public final double total;

        public Slice(String name, double total) {
            this.name = name;
            this.total = total;
        }
    }

    // ------------------------------------------------------------- loai ghi chu

    /** Loai nay co lam thay doi so du vi khong. */
    public static boolean affectsWallet(String type) {
        return EXPENSE.equals(type) || INCOME.equals(type);
    }

    /** Loai nay thuoc nhom vay / no khong. */
    public static boolean isDebtKind(String type) {
        return LEND.equals(type) || DEBT.equals(type);
    }

    /** Ten hien thi tieng Viet cua mot loai. */
    public static String typeName(String type) {
        if (INCOME.equals(type)) return "Thu nh\u1eadp";
        if (LEND.equals(type)) return "Cho vay";
        if (DEBT.equals(type)) return "N\u1ee3 ph\u1ea3i tr\u1ea3";
        return "Chi ti\u00eau";
    }

    /** Ky hieu mui ten hien o o tron ben trai moi dong danh sach. */
    public static String typeGlyph(String type) {
        if (INCOME.equals(type)) return "\u2191";
        if (LEND.equals(type)) return "\u21aa";
        if (DEBT.equals(type)) return "\u21a9";
        return "\u2193";
    }

    /** Dau hien thi truoc so tien. LEND / DEBT khong doi vi nen khong co dau. */
    public static String typeSign(String type) {
        if (INCOME.equals(type)) return "+ ";
        if (isDebtKind(type)) return "";
        return "- ";
    }

    // ------------------------------------------------------------- moc thoi gian

    public static long startOfMonth(int monthOffset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, monthOffset);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfMonth(int monthOffset) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfMonth(monthOffset));
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis() - 1;
    }

    public static int daysLeftInMonth() {
        Calendar c = Calendar.getInstance();
        return c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH);
    }

    // ------------------------------------------------------------- tong hop

    public static double totalExpense(List<TransactionEntity> list, long from, long to) {
        return totalOfType(list, EXPENSE, from, to);
    }

    /** Tong so tien cua mot loai bat ky trong khoang thoi gian. */
    public static double totalOfType(List<TransactionEntity> list, String type, long from, long to) {
        double sum = 0;
        if (list == null) return sum;
        for (TransactionEntity t : list) {
            if (type.equals(t.getType()) && t.getDate() >= from && t.getDate() <= to) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    /** Tong chi tieu theo danh muc, sap xep giam dan. Chi tinh EXPENSE. */
    public static List<Slice> byCategory(List<TransactionEntity> list, long from, long to) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (TransactionEntity t : list) {
            if (!EXPENSE.equals(t.getType())) continue;
            if (t.getDate() < from || t.getDate() > to) continue;
            String key = t.getCategory() == null || t.getCategory().isEmpty() ? "Kh\u00e1c" : t.getCategory();
            Double old = map.get(key);
            map.put(key, (old == null ? 0d : old) + t.getAmount());
        }
        List<Slice> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : map.entrySet()) {
            out.add(new Slice(e.getKey(), e.getValue()));
        }
        Collections.sort(out, new Comparator<Slice>() {
            @Override
            public int compare(Slice a, Slice b) {
                return Double.compare(b.total, a.total);
            }
        });
        return out;
    }

    /** Gop thanh toi da 5 phan, phan con lai don vao "Khac" giong thiet ke. */
    public static List<Slice> topWithOther(List<Slice> slices, int max) {
        List<Slice> out = new ArrayList<>();
        double other = 0;
        for (int i = 0; i < slices.size(); i++) {
            if (i < max - 1) {
                out.add(slices.get(i));
            } else {
                other += slices.get(i).total;
            }
        }
        if (other > 0) out.add(new Slice("Kh\u00e1c", other));
        return out;
    }

    public static double changePercent(double now, double before) {
        if (before <= 0) return now > 0 ? 100 : 0;
        return (now - before) / before * 100d;
    }

    /** Cau mo ta bien dong: "t\u0103ng 12,5%" hoac "gi\u1ea3m 8%". */
    public static String changePhrase(double now, double before) {
        double change = changePercent(now, before);
        String direction = change >= 0 ? "t\u0103ng" : "gi\u1ea3m";
        return direction + " " + Money.percent(Math.abs(change)) + " so v\u1edbi k\u1ef3 tr\u01b0\u1edbc";
    }
}
