package com.example.bmmoney.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Dinh dang tien te giong thiet ke: 65.481.000 d. */
public final class Money {

    private static final DecimalFormat GROUPED;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        GROUPED = new DecimalFormat("#,###", symbols);
    }

    private Money() {
    }

    public static String vnd(double value) {
        return GROUPED.format(Math.round(value)) + " \u20ab";
    }

    public static String plain(double value) {
        return GROUPED.format(Math.round(value));
    }

    /** Dang ngan gon dung o tam bieu do tron: 65,5 tr d. */
    public static String shortVnd(double value) {
        if (Math.abs(value) >= 1000000d) {
            return String.format(Locale.US, "%.1f", value / 1000000d).replace('.', ',') + " tr \u20ab";
        }
        if (Math.abs(value) >= 1000d) {
            return String.format(Locale.US, "%.0f", value / 1000d) + " ng \u20ab";
        }
        return vnd(value);
    }

    public static String percent(double value) {
        return String.format(Locale.US, "%.1f", value).replace(".0", "") + "%";
    }

    public static String signedPercent(double value) {
        String sign = value >= 0 ? "+" : "";
        return sign + String.format(Locale.US, "%.1f", value) + "%";
    }
}
