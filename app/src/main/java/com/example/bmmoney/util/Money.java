package com.example.bmmoney.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Dinh dang tien te: 65.481.000 \u20ab.
 *
 * <p>Ban nay nhan {@code long} lam kieu chinh vi so tien trong may da la
 * {@code long}. Cac ham {@code double} van con de bieu do dung - o do con so la ty
 * le hay trung binh nen thap phan la dung, chu khong phai so tien.</p>
 *
 * <p><b>Luu y:</b> {@link DecimalFormat} KHONG an toan khi nhieu luong cung goi.
 * Man Phan tich dinh dang hang chuc nhan trong luc luong nen dang chay, nen moi lan
 * dung deu phai vao khoi {@code synchronized}.</p>
 */
public final class Money {

    private static final DecimalFormat GROUPED;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        GROUPED = new DecimalFormat("#,###", symbols);
    }

    private Money() {
    }

    /** 65.481.000 \u20ab */
    public static String vnd(long value) {
        return grouped(value) + " \u20ab";
    }

    /** 65.481.000 (khong kem ky hieu tien) */
    public static String plain(long value) {
        return grouped(value);
    }

    /** Dang ngan gon dung o tam bieu do tron: 65,5 tr \u20ab. */
    public static String shortVnd(long value) {
        long abs = Math.abs(value);
        if (abs >= 1000000L) {
            return String.format(Locale.US, "%.1f", value / 1000000d)
                    .replace('.', ',') + " tr \u20ab";
        }
        if (abs >= 1000L) {
            return String.format(Locale.US, "%.0f", value / 1000d) + " ng \u20ab";
        }
        return vnd(value);
    }

    private static String grouped(long value) {
        synchronized (GROUPED) {
            return GROUPED.format(value);
        }
    }

    // ------------------------------------------------------------ ty le phan tram

    public static String percent(double value) {
        return String.format(Locale.US, "%.1f", value).replace(".0", "") + "%";
    }

    public static String signedPercent(double value) {
        String sign = value >= 0 ? "+" : "";
        return sign + String.format(Locale.US, "%.1f", value) + "%";
    }

    // --------------------------------------------------- ban cu, cho bieu do

    /**
     * Danh cho gia tri tinh toan tra ve {@code double} (trung binh, du bao...).
     * Voi so tien lay tu co so du lieu, hay dung ban {@code long}.
     */
    public static String vnd(double value) {
        return vnd(Math.round(value));
    }

    public static String plain(double value) {
        return plain(Math.round(value));
    }

    public static String shortVnd(double value) {
        return shortVnd(Math.round(value));
    }
}
