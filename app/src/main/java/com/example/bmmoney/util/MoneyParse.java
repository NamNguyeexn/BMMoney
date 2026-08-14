package com.example.bmmoney.util;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Doc so tien va doan loai giao dich tu mot dong thong bao, chay hoan toan tren may.
 *
 * <p>Day la cong chan truoc khi goi Gemini: khong tim ra so tien thi khong goi AI,
 * nho vay thong bao chat, tin nhan khuyen mai... khong bao gio bi gui di dau.
 */
public final class MoneyParse {

    /** So tien co duoi tien: 1.200.000d, 45,000 VND, 250000 dong. */
    private static final Pattern MONEY = Pattern.compile(
            "([+\\-])?\\s*(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{4,})"
                    + "\\s*(?:vn\u0111|vnd|\u20ab|dong|d\u1ed3ng|\u0111|d)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Day so dai kieu so tai khoan hoac so the.
     *
     * <p>Phai la chin chu so tro len va khong duoc di kem duoi tien, neu khong
     * mot khoan chi 15000000d se bi che thanh ***000.
     */
    private static final Pattern LONG_DIGITS = Pattern.compile(
            "\\d{9,}(?!\\s*(?:vn\u0111|vnd|\u20ab|dong|d\u1ed3ng|\u0111|d)\\b)",
            Pattern.CASE_INSENSITIVE);

    /** Tu khoa cho biet tien di ra. */
    private static final String[] OUT = {
            "tru", "thanh toan", "chi tieu", "chuyen tien", "chuyen khoan di", "rut",
            "mua", "quet the", "ghi no", "da tra", "phi", "nap the"};

    /** Tu khoa cho biet tien di vao. */
    private static final String[] IN = {
            "nhan duoc", "cong", "ghi co", "nhan tien", "tien vao", "hoan tien",
            "luong", "thu nhap", "chuyen den", "da nhan"};

    private MoneyParse() {
    }

    /** So tien va loai giao dich doc duoc tu mot dong thong bao. */
    public static final class Found {
        public final long amount;
        public final String type;

        Found(long amount, String type) {
            this.amount = amount;
            this.type = type;
        }
    }

    /**
     * Tim so tien dau tien trong cau. Lay so dau tien chu khong lay so lon nhat,
     * vi thong bao ngan hang luon viet so bien dong truoc roi moi den so du.
     *
     * @return null khi cau khong he noi ve tien
     */
    @Nullable
    public static Found find(@Nullable String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = MONEY.matcher(text);
        if (!matcher.find()) return null;

        long amount = digits(matcher.group(2));
        if (amount <= 0L) return null;

        String sign = matcher.group(1);
        String type = Stats.EXPENSE;
        if ("+".equals(sign)) {
            type = Stats.INCOME;
        } else if (sign == null) {
            type = guessType(text);
        }
        return new Found(amount, type);
    }

    /** Doan chieu tien khi thong bao khong co dau cong tru ro rang. */
    public static String guessType(String text) {
        String plain = TextNorm.normalize(text);
        if (has(plain, IN) && !has(plain, OUT)) return Stats.INCOME;
        return Stats.EXPENSE;
    }

    /** Bo dau phan cach nghin de doi thanh so. */
    public static long digits(@Nullable String raw) {
        if (raw == null) return 0L;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        if (out.length() == 0 || out.length() > 18) return 0L;
        try {
            return Long.parseLong(out.toString());
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    /**
     * Che so tai khoan, chi giu ba so cuoi.
     *
     * <p>Luon doc so tien truoc roi moi che, vi che xong thi khong con doc duoc nua.
     */
    public static String mask(@Nullable String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = LONG_DIGITS.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String group = matcher.group();
            String tail = group.substring(Math.max(0, group.length() - 3));
            matcher.appendReplacement(out, Matcher.quoteReplacement("***" + tail));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Doan danh muc bang tu khoa. Chi tra ve ten khi that su chac,
     * con lai de null cho nguoi dung tu chon.
     */
    @Nullable
    public static String guessCategory(@Nullable String text) {
        String plain = TextNorm.normalize(text == null ? "" : text);
        if (has(plain, new String[]{"grab", "be ", "xanh sm", "taxi", "xang", "petrol",
                "ve xe", "ve may bay", "gui xe", "vetc", "epass"})) {
            return "Di chuy\u1ec3n";
        }
        if (has(plain, new String[]{"an ", "quan ", "nha hang", "cafe", "ca phe", "tra sua",
                "highlands", "shopeefood", "grabfood", "beamin", "bakery"})) {
            return "\u0102n u\u1ed1ng";
        }
        if (has(plain, new String[]{"tien dien", "tien nuoc", "internet", "cuoc", "hoa don",
                "evn", "viettel", "vinaphone", "mobifone", "fpt"})) {
            return "H\u00f3a \u0111\u01a1n";
        }
        if (has(plain, new String[]{"shopee", "lazada", "tiki", "tiktok shop", "sieu thi",
                "winmart", "bach hoa", "circle k", "mua sam"})) {
            return "Mua s\u1eafm";
        }
        if (has(plain, new String[]{"benh vien", "phong kham", "nha thuoc", "long chau",
                "pharmacity", "kham benh", "thuoc"})) {
            return "Y t\u1ebf";
        }
        if (has(plain, new String[]{"netflix", "spotify", "youtube", "cgv", "lotte cinema",
                "game", "rap", "ve phim"})) {
            return "Gi\u1ea3i tr\u00ed";
        }
        return null;
    }

    private static boolean has(String plain, String[] keys) {
        for (String key : keys) {
            if (plain.contains(key)) return true;
        }
        return false;
    }
}
