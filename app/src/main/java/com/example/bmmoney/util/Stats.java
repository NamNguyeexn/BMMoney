package com.example.bmmoney.util;

import com.example.bmmoney.data.TransactionEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tong hop so lieu chi tieu tu danh sach giao dich Room. */
public final class Stats {

    public static final String EXPENSE = "EXPENSE";

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

    public static double totalExpense(List<TransactionEntity> list, long from, long to) {
        double sum = 0;
        for (TransactionEntity t : list) {
            if (EXPENSE.equals(t.getType()) && t.getDate() >= from && t.getDate() <= to) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    /** Tong chi tieu theo danh muc, sap xep giam dan. */
    public static List<Slice> byCategory(List<TransactionEntity> list, long from, long to) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (TransactionEntity t : list) {
            if (!EXPENSE.equals(t.getType())) continue;
            if (t.getDate() < from || t.getDate() > to) continue;
            String key = t.getCategory() == null || t.getCategory().isEmpty() ? "Khác" : t.getCategory();
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
        if (other > 0) out.add(new Slice("Khác", other));
        return out;
    }

    public static double changePercent(double now, double before) {
        if (before <= 0) return now > 0 ? 100 : 0;
        return (now - before) / before * 100d;
    }
}
