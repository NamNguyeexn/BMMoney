package com.example.bmmoney.ocr;

import androidx.annotation.Nullable;

import com.example.bmmoney.util.MoneyParse;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.TextNorm;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TACH SO TIEN, NGAY VA GIO TU CHU DOC DUOC TREN ANH.
 *
 * <h3>Vi sao la bieu thuc chinh quy chu khong phai mot mo hinh ngon ngu</h3>
 *
 * <p>Ba truong can lay - so tien, ngay, gio - deu la chuoi so in bang phong chu ro
 * net theo mot khuon co dinh. Voi dang du lieu nay, bieu thuc chinh quy khong chi
 * du dung ma con DANG TIN HON mot mo hinh sinh: no hoac khop, hoac khong khop. Mot
 * mo hinh ngon ngu duoc yeu cau "doc so tien" thi luon tra ve mot con so, ke ca khi
 * anh mo den muc khong the doc - va mot con so tien sai am tham la loai sai te nhat
 * trong mot app so sach.
 *
 * <h3>Hai dang anh, hai loi doc khac nhau</h3>
 *
 * <ol>
 *   <li><b>Anh chi tiet mot giao dich:</b> co ma giao dich, co ngay VA gio. Doc duoc
 *       tron ven ca ba truong.</li>
 *   <li><b>Anh danh sach "Hoat dong gan day":</b> nhieu dong, moi dong chi co so tien;
 *       ngay nam o tieu de nhom phia tren; KHONG co gio. Phai gom theo toa do.</li>
 * </ol>
 *
 * <p>Thu dang chi tiet truoc. Chi khi anh khong phai dang chi tiet moi chuyen sang
 * doc dang danh sach - dao thu tu lai thi mot anh chi tiet se bi tach thanh nhieu
 * dong rac, vi trong do con so du, phi va han muc cung mang dau cong tru.
 */
public final class ReceiptParser {

    /** Duoi khoan tien coi la that. Duoi muc nay gan nhu chi la so thu tu hoac ma. */
    private static final long MIN_AMOUNT = 1000L;

    /** Chu doc duoc luu lai toi da bao nhieu ky tu. */
    private static final int MAX_RAW = 400;

    private static final Pattern DATE_SLASH =
            Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");

    /**
     * Gio dang 24 tieng.
     *
     * <p>Hai lookaround hai ben la de khong cat nham mot phan cua chuoi dai hon:
     * khong co chung thi "12:34:56" se cho ra 12:34, va so the dang "1234:5678" cung
     * bi doc thanh gio.
     */
    private static final Pattern TIME =
            Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d:])");

    /** Ma giao dich in tren bien lai. */
    private static final Pattern REF = Pattern.compile(
            "m[\u00e3a]\\s*giao\\s*d[\u1ecbi]ch\\s*:?\\s*([A-Za-z0-9\\\\/_-]{6,40})",
            Pattern.CASE_INSENSITIVE);

    /** So tien co dau va co duoi tien dat truoc: "-VND 6,000". */
    private static final Pattern VND_SIGNED = Pattern.compile(
            "([+\\-\u2212])\\s*(?:VND|\u20ab)\\s*(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{4,})",
            Pattern.CASE_INSENSITIVE);

    /**
     * Mot dong chi chua duy nhat so tien co dau, kieu "- 6,000" o cot phai.
     *
     * <p>Bat buoc phai co dau cong hoac tru, va phai khop TRON dong. Neu noi long
     * dieu kien nay thi so du vi, so du kha dung va cac con so trang tri khac deu
     * lot vao danh sach goi y.
     */
    private static final Pattern ROW_AMOUNT = Pattern.compile(
            "^([+\\-\u2212])\\s*(?:VND|\u20ab)?\\s*(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{4,})"
                    + "\\s*(?:VND|\u20ab|\u0111)?$",
            Pattern.CASE_INSENSITIVE);

    /** Tieu de ngay kieu Viet: "3 Thg 9, 2026". Nam co the bi khuyet. */
    private static final Pattern DATE_VN = Pattern.compile(
            "(\\d{1,2})\\s*(?:thg|th\u00e1ng|thang)\\s*(\\d{1,2})\\s*,?\\s*(\\d{4})?",
            Pattern.CASE_INSENSITIVE);

    /**
     * Nhung dong chu KHONG bao gio la ten khoan chi.
     *
     * <p>Day la nhan giao dien cua app ngan hang. So sanh sau khi bo dau bang
     * TextNorm, nen chi can viet ban khong dau.
     */
    private static final String[] LABELS = {
            "chi tiet giao dich", "giao dich", "thanh cong", "hoat dong gan day",
            "ma giao dich", "so tien", "tong so tien", "noi dung", "phi giao dich",
            "tai khoan", "so du", "nguoi nhan", "nguoi chuyen", "ngan hang",
            "chia se", "luu anh", "xong", "hom nay", "hom qua", "techcombank",
            "xem tat ca", "tim kiem", "thoi gian", "trang thai", "loai giao dich"};

    private ReceiptParser() {
    }

    /** Mot giao dich doc duoc tu anh. Chi ba truong dau la do may doc. */
    public static final class Draft {
        public long amount;
        public String type = Stats.EXPENSE;
        public long date;
        /** false khi anh chi cho biet ngay, khong cho biet gio. */
        public boolean hasTime;
        public String title = "";
        @Nullable
        public String refCode;
    }

    // ------------------------------------------------------------------ loi vao

    /**
     * Doc mot anh thanh danh sach giao dich.
     *
     * @return danh sach rong khi anh khong chua giao dich nao doc duoc
     */
    public static List<Draft> parse(List<ReceiptOcr.Line> lines) {
        List<Draft> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return out;

        Draft single = detail(lines, join(lines));
        if (single != null) {
            out.add(single);
            return out;
        }
        return listRows(lines);
    }

    /** Chu doc duoc, da che so tai khoan va cat bot cho vua bang. */
    public static String rawOf(List<ReceiptOcr.Line> lines) {
        String masked = MoneyParse.mask(join(lines));
        return masked.length() <= MAX_RAW ? masked : masked.substring(0, MAX_RAW);
    }

    // -------------------------------------------------- dang 1: anh chi tiet

    /**
     * Doc anh chi tiet mot giao dich.
     *
     * <p>Dieu kien nhan dang: co ngay dang dd/MM/yyyy, VA co it nhat mot trong hai dau
     * hieu "day la mot bien lai don le" - ma giao dich, hoac mot moc gio. Anh danh sach
     * khong co ca hai, nen no se roi xuong loi doc thu hai.
     *
     * @return null khi anh khong phai dang chi tiet
     */
    @Nullable
    private static Draft detail(List<ReceiptOcr.Line> lines, String full) {
        Matcher dateMatch = DATE_SLASH.matcher(full);
        if (!dateMatch.find()) return null;

        String ref = null;
        Matcher refMatch = REF.matcher(full);
        if (refMatch.find()) ref = refMatch.group(1);

        int hour = -1;
        int minute = 0;
        Matcher timeMatch = TIME.matcher(full);
        if (timeMatch.find()) {
            hour = number(timeMatch.group(1));
            minute = number(timeMatch.group(2));
        }

        if (ref == null && hour < 0) return null;

        long amount = 0L;
        String type = Stats.EXPENSE;
        Matcher amountMatch = VND_SIGNED.matcher(full);
        if (amountMatch.find()) {
            amount = MoneyParse.digits(amountMatch.group(2));
            type = "+".equals(amountMatch.group(1)) ? Stats.INCOME : Stats.EXPENSE;
        } else {
            // Khong thay dang "-VND 6,000" thi nho lai bo doc so tien cua app,
            // no biet ca "6.000d" va "6000 dong".
            MoneyParse.Found found = MoneyParse.find(full);
            if (found != null) {
                amount = found.amount;
                type = found.type;
            }
        }
        if (amount < MIN_AMOUNT) return null;

        Draft draft = new Draft();
        draft.amount = amount;
        draft.type = type;
        draft.refCode = ref;
        draft.hasTime = hour >= 0;
        draft.date = stamp(number(dateMatch.group(3)), number(dateMatch.group(2)),
                number(dateMatch.group(1)), hour < 0 ? 0 : hour, minute);
        draft.title = biggestName(lines);
        return draft;
    }

    // ------------------------------------------------- dang 2: anh danh sach

    /**
     * Doc anh danh sach hoat dong.
     *
     * <p>Di tu tren xuong. Gap tieu de ngay thi ghi nho ngay dang xet; gap mot dong
     * chi co so tien thi do la mot giao dich thuoc ngay do, va ten khoan la manh chu
     * nam cung hang o phia trai.
     *
     * <p>Khong co gio nen {@code hasTime} bang false va gio la 00:00. Doan bua mot moc
     * gio o day thi giao dich se nam sai vi tri trong dong thoi gian ma nguoi dung
     * khong he biet la may tu dat ra.
     */
    private static List<Draft> listRows(List<ReceiptOcr.Line> lines) {
        List<Draft> out = new ArrayList<>();
        Long day = null;

        for (ReceiptOcr.Line line : lines) {
            Long header = headerDate(line.text);
            if (header != null) {
                day = header;
                continue;
            }

            Matcher match = ROW_AMOUNT.matcher(line.text.trim());
            if (!match.matches()) continue;

            long amount = MoneyParse.digits(match.group(2));
            if (amount < MIN_AMOUNT) continue;

            Draft draft = new Draft();
            draft.amount = amount;
            draft.type = "+".equals(match.group(1)) ? Stats.INCOME : Stats.EXPENSE;
            draft.hasTime = false;
            draft.date = day == null ? startOfToday() : day;
            draft.title = sameRowName(lines, line);
            out.add(draft);
        }
        return out;
    }

    /** Doc mot tieu de ngay. Tra ve 00:00 cua ngay do, hoac null neu khong phai. */
    @Nullable
    private static Long headerDate(String text) {
        String plain = TextNorm.normalize(text);

        if (plain.contains("hom nay")) return startOfToday();
        if (plain.contains("hom qua")) return startOfToday() - 86400000L;

        Matcher vn = DATE_VN.matcher(text);
        if (vn.find()) {
            String year = vn.group(3);
            Calendar now = Calendar.getInstance();
            return stamp(year == null ? now.get(Calendar.YEAR) : number(year),
                    number(vn.group(2)), number(vn.group(1)), 0, 0);
        }

        // Chi coi la tieu de khi CA dong chi co ngay. "03/09/2026" lan trong mot cau
        // dai thi khong phai tieu de nhom.
        Matcher slash = DATE_SLASH.matcher(text.trim());
        if (slash.matches()) {
            return stamp(number(slash.group(3)), number(slash.group(2)),
                    number(slash.group(1)), 0, 0);
        }
        return null;
    }

    // ----------------------------------------------------------- doan ten khoan

    /**
     * Manh chu nam cung hang voi so tien, ve phia trai no.
     *
     * <p>Chon manh o trai nhat: trong mot dong danh sach, ten noi nhan luon bat dau
     * sat le trai, con nhung manh o giua thuong la nhan phu nhu ten ngan hang.
     */
    private static String sameRowName(List<ReceiptOcr.Line> lines, ReceiptOcr.Line amount) {
        ReceiptOcr.Line best = null;
        for (ReceiptOcr.Line line : lines) {
            if (line == amount || line.left >= amount.left) continue;
            if (!line.sameRowAs(amount)) continue;
            if (!looksLikeName(line.text)) continue;
            if (best == null || line.left < best.left) best = line;
        }
        return best == null ? "" : trim(best.text);
    }

    /**
     * Ten khoan cho anh chi tiet: manh chu CAO NHAT o nua tren cua anh.
     *
     * <p>Dung chieu cao chu thay vi vi tri, vi tren bien lai ten nguoi nhan duoc in
     * to hon moi nhan xung quanh no. Do la dau hieu on dinh hon nhieu so voi "dong
     * thu ba tu tren xuong", vi so dong tieu de thay doi theo tung loai giao dich.
     */
    private static String biggestName(List<ReceiptOcr.Line> lines) {
        if (lines.isEmpty()) return "";
        int top = lines.get(0).centerY();
        int bottom = lines.get(lines.size() - 1).centerY();
        int half = top + (bottom - top) / 2;

        ReceiptOcr.Line best = null;
        for (ReceiptOcr.Line line : lines) {
            if (line.centerY() > half) continue;
            if (!looksLikeName(line.text)) continue;
            if (best == null || line.height() > best.height()) best = line;
        }
        return best == null ? "" : trim(best.text);
    }

    /** Mot manh chu co the la ten khoan chi khong. */
    private static boolean looksLikeName(String text) {
        String value = text.trim();
        if (value.length() < 4 || value.length() > 60) return false;

        int letters = 0;
        int digits = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
        }
        // Phai la chu, va khong duoc la mot chuoi so co vai chu dinh kem.
        if (letters < 3 || digits > letters) return false;

        String plain = TextNorm.normalize(value);
        for (String label : LABELS) {
            if (plain.equals(label) || plain.startsWith(label)) return false;
        }
        return true;
    }

    // -------------------------------------------------------------- tien ich

    private static String join(List<ReceiptOcr.Line> lines) {
        StringBuilder out = new StringBuilder();
        for (ReceiptOcr.Line line : lines) {
            if (out.length() > 0) out.append('\n');
            out.append(line.text);
        }
        return out.toString();
    }

    private static String trim(String text) {
        String value = text.trim();
        return value.length() <= 60 ? value : value.substring(0, 60);
    }

    private static int number(@Nullable String raw) {
        if (raw == null) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    /** Doi ngay gio doc duoc thanh moc thoi gian, theo mui gio cua may. */
    private static long stamp(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, Math.max(0, month - 1), day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static long startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
