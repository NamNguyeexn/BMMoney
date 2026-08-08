package com.example.bmmoney.data;

import java.util.Calendar;

/**
 * Sinh KHOA NGAY dang so nguyen cho bang giao dich.
 *
 * <pre>
 * dayKey   20260807   (yyyyMMdd)
 * monthKey 202608     (yyyyMM)
 * yearKey  2026       (yyyy)
 * </pre>
 *
 * <p><b>Vi sao can?</b> Cot {@code date} luu mili giay. Muon nhom theo thang thi
 * SQLite phai goi {@code strftime} tren TUNG dong - mot bieu thuc nhu vay KHONG
 * dung duoc index, nen moi bieu do deu quet toan bang. Luu san khoa ngay thanh cot
 * rieng co index thi cau nhom theo thang chi con doc dung phan index can thiet.</p>
 *
 * <p><b>Quan trong:</b> khoa duoc tinh theo MUI GIO MAY, dung y nguyen quy uoc ma
 * nguoi dung nhin thay tren lich. Migration cung dung {@code 'localtime'} cho khop.
 * Viet Nam khong doi gio mua he nen khong co ngay nao bi lech.</p>
 */
public final class DateKeys {

    private DateKeys() {
    }

    /** yyyyMMdd, vi du 20260807. */
    public static int day(long millis) {
        Calendar c = at(millis);
        return c.get(Calendar.YEAR) * 10000
                + (c.get(Calendar.MONTH) + 1) * 100
                + c.get(Calendar.DAY_OF_MONTH);
    }

    /** yyyyMM, vi du 202608. */
    public static int month(long millis) {
        Calendar c = at(millis);
        return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1);
    }

    /** yyyy, vi du 2026. */
    public static int year(long millis) {
        return at(millis).get(Calendar.YEAR);
    }

    /** Khoa thang cua thang lien truoc mot khoa thang cho truoc. */
    public static int previousMonth(int monthKey) {
        int y = monthKey / 100;
        int m = monthKey % 100;
        if (m <= 1) return (y - 1) * 100 + 12;
        return y * 100 + (m - 1);
    }

    /** Khoa thang cua thang lien sau mot khoa thang cho truoc. */
    public static int nextMonth(int monthKey) {
        int y = monthKey / 100;
        int m = monthKey % 100;
        if (m >= 12) return (y + 1) * 100 + 1;
        return y * 100 + (m + 1);
    }

    /**
     * Lui {@code count} thang tu khoa thang cho truoc. Dung lam dau mut duoi cho
     * bieu do "12 thang gan nhat".
     */
    public static int monthsBack(int monthKey, int count) {
        int key = monthKey;
        for (int i = 0; i < count; i++) key = previousMonth(key);
        return key;
    }

    private static Calendar at(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c;
    }
}
