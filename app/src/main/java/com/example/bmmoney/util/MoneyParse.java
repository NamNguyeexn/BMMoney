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

    /** Duoi tien co the gap trong thong bao ngan hang. */
    private static final String UNIT =
            "(?:vn\u0111|vnd|\u20ab|d\u1ed3ng|dong|\u0111|d)";

    /**
     * Ban va 20/08 - VI SAO PHAI BO \b O DAY.
     *
     * <p>Mau cu ket thuc bang {@code \b} ngay sau duoi tien. Nhung {@code \b} chi la
     * ranh gioi giua ky tu "tu" ([A-Za-z0-9_]) va ky tu khac; ma "\u0111" va "\u20ab"
     * KHONG thuoc lop ky tu tu. Vi vay voi "-50.000\u0111" thi ky tu vua khop la
     * "\u0111" (khong phai chu tu) va ky tu ke tiep la dau cach hoac het cau (cung
     * khong phai chu tu) - hai ben giong nhau nen KHONG co ranh gioi, va ca mau
     * that bai. Ket qua: moi thong bao viet tien bang "\u0111" hoac "\u20ab" - tuc la
     * gan nhu toan bo ngan hang Viet Nam - deu bi coi la "khong noi ve tien" va bi bo
     * ngay tai cong chan, nen man Goi y luon trong.</p>
     *
     * <p>Thay bang lookahead "phia sau khong duoc la chu hoac chu so": dung y do ban
     * dau (khong an vao mot tu dai hon) ma khong phu thuoc vao lop ky tu tu.</p>
     */
    private static final String NOT_WORD_AFTER = "(?![\\p{L}\\d])";

    /** Cum chu so tien: 1.200.000 hoac 45,000 hoac 250000. */
    private static final String DIGITS = "(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{3,})";

    /** So tien co duoi tien dat sau: 1.200.000\u0111, 45,000 VND, 250000 dong. */
    private static final Pattern MONEY_SUFFIX = Pattern.compile(
            "([+\\-])?\\s*" + DIGITS + "\\s*" + UNIT + NOT_WORD_AFTER,
            Pattern.CASE_INSENSITIVE);

    /** So tien co duoi tien dat truoc: VND 1.200.000, \u20ab45,000. */
    private static final Pattern MONEY_PREFIX = Pattern.compile(
            "(?:vn\u0111|vnd|\u20ab)\\s*([+\\-])?\\s*" + DIGITS,
            Pattern.CASE_INSENSITIVE);

    /**
     * So tien khong co duoi tien nhung co dau cong / tru va dau phan cach nghin,
     * kieu "-50.000" hay "+1,200,000". Nhieu vi dien tu viet dung nhu vay.
     */
    private static final Pattern MONEY_SIGNED = Pattern.compile(
            "([+\\-])\\s*(\\d{1,3}(?:[.,\\s]\\d{3})+)");

    /** Thu tu thu: co duoi tien truoc, roi moi den dang chi co dau cong tru. */
    private static final Pattern[] MONEY_PATTERNS =
            {MONEY_SUFFIX, MONEY_PREFIX, MONEY_SIGNED};

    /**
     * Day so dai kieu so tai khoan hoac so the.
     *
     * <p>Phai la chin chu so tro len va khong duoc di kem duoi tien, neu khong
     * mot khoan chi 150000000d se bi che thanh ***000. Lookahead o day cung phai
     * bo {@code \b} vi cung mot ly do nhu tren.
     */
    private static final Pattern LONG_DIGITS = Pattern.compile(
            "\\d{9,}(?!\\s*" + UNIT + NOT_WORD_AFTER + ")",
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

        for (Pattern pattern : MONEY_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) continue;

            long amount = digits(matcher.group(2));
            if (amount <= 0L) continue;

            String sign = matcher.group(1);
            String type;
            if ("+".equals(sign)) {
                type = Stats.INCOME;
            } else if ("-".equals(sign)) {
                type = Stats.EXPENSE;
            } else {
                type = guessType(text);
            }
            return new Found(amount, type);
        }
        return null;
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
