package com.example.bmmoney.ocr;

import android.content.Context;
import android.net.Uri;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.SuggestionDao;
import com.example.bmmoney.data.SuggestionEntity;
import com.example.bmmoney.util.MoneyParse;

import java.security.MessageDigest;
import java.util.List;

/**
 * DOC ANH GIAO DICH RA THANH GOI Y.
 *
 * <h3>App khong luu anh</h3>
 *
 * <p>Anh duoc doc THANG tu Uri ma nguoi dung chi ra: khong sao ra ban nao, khong ghi
 * vao bo nho rieng, khong luu duong dan. Anh chi di qua bo nho vai tram mili giay,
 * roi thu duy nhat con lai trong may la ba con so: so tien, ngay, gio - cong voi doan
 * chu da che so tai khoan de nguoi dung doi chieu.
 *
 * <p>Anh la cua nguoi dung, va ho da co cho de quan ly no roi. Mot ban sao thu hai
 * nam trong app chi tao ra hai rui ro: no phinh dan ma khong ai thay, va no khien
 * viec nguoi dung xoa anh trong thu vien tro thanh vo nghia.
 *
 * <p>Doi lai: sau khi doc xong khong con duong nao quay lai anh goc. Vi vay man them
 * giao dich duoc dien san CA doan chu doc duoc, de nguoi dung kiem tra con so ngay
 * tai do ma khong can mo lai anh.
 *
 * <h3>Phai goi trong Db.io, va phai goi khi con quyen doc Uri</h3>
 *
 * <p>Quyen doc Uri chia se song theo vong doi cua Activity da nhan Intent. Vi vay
 * viec doc phai xay ra ngay trong man hinh do, chu khong the day sang man khac roi
 * doc sau.
 */
public final class ReceiptImporter {

    /** Goi y cu hon moc nay se bi don khoi bang. */
    private static final long PURGE_AFTER_MS = 60L * 24 * 60 * 60 * 1000;

    /** Nhan nguon, hien tren tung dong goi y. */
    private static final String SOURCE = "\u1ea2nh giao d\u1ecbch";

    private ReceiptImporter() {
    }

    /** Ket qua mot lan doc anh. */
    public static final class Result {
        /** So goi y moi da ghi. */
        public int added;
        /** So giao dich bi bo vi da co trong bang. */
        public int duplicate;
        /** So anh khong doc duoc, hoac doc duoc nhung khong thay giao dich nao. */
        public int failed;

        /** Khong ghi duoc gi va cung khong co gi trung -> coi la that bai. */
        public boolean isFailure() {
            return added == 0 && duplicate == 0;
        }
    }

    /**
     * Doc cac anh nguoi dung chi ra va ghi goi y. Khong bao gio nem loi.
     *
     * <p>Moi anh loi chi lam tang {@link Result#failed}, nho vay chia se nam anh ma
     * mot anh mo thi bon anh con lai van vao duoc bang.
     */
    public static Result ingest(Context context, List<Uri> images) {
        Result result = new Result();
        if (images == null || images.isEmpty()) {
            result.failed = 1;
            return result;
        }

        SuggestionDao dao = AppDatabase.suggestions(context);
        long now = System.currentTimeMillis();

        for (Uri image : images) {
            if (image == null) {
                result.failed++;
                continue;
            }

            List<ReceiptParser.Draft> drafts;
            String raw;
            try {
                List<ReceiptOcr.Line> lines = ReceiptOcr.read(context, image);
                drafts = ReceiptParser.parse(lines);
                raw = ReceiptParser.rawOf(lines);
            } catch (Throwable error) {
                result.failed++;
                continue;
            }

            if (drafts.isEmpty()) {
                result.failed++;
                continue;
            }

            for (ReceiptParser.Draft draft : drafts) {
                SuggestionEntity item = new SuggestionEntity(
                        dedupeKey(draft),
                        draft.refCode,
                        SOURCE,
                        raw,
                        draft.title,
                        draft.amount,
                        draft.type,
                        MoneyParse.guessCategory(draft.title),
                        draft.date,
                        draft.hasTime,
                        now);

                if (dao.insertIgnore(item) > 0) result.added++;
                else result.duplicate++;
            }
        }

        purge(dao, now);
        return result;
    }

    /**
     * Khoa chong trung.
     *
     * <p>Co ma giao dich thi dung luon ma do: day la khoa ngan hang dat ra, duy nhat
     * tuyet doi, nen chon cung mot anh muoi lan cach nhau nhieu thang cung chi sinh ra
     * mot dong. Dieu nay quan trong hon truoc, vi bay gio app khong con giu anh de
     * biet minh da doc anh nao.
     *
     * <p>Khong co ma thi bam so tien cong moc thoi gian cong ten khoan. Hai giao dich
     * that su khac nhau ma trung ca ba thu nay thi gan nhu chac chan la mot.
     */
    private static String dedupeKey(ReceiptParser.Draft draft) {
        if (draft.refCode != null && !draft.refCode.trim().isEmpty()) {
            return "ref:" + draft.refCode.trim();
        }
        return "img:" + sha1(draft.amount + "|" + draft.date + "|" + draft.type
                + "|" + draft.title);
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception error) {
            // Khong bam duoc thi dung nguyen chuoi. Van la mot khoa dung, chi dai hon.
            return value;
        }
    }

    /** Don goi y qua cu. Khong co tep nao phai xoa theo vi app khong giu anh. */
    private static void purge(SuggestionDao dao, long now) {
        try {
            dao.purgeOlderThan(now - PURGE_AFTER_MS);
        } catch (Throwable ignored) {
            // Don rac that bai khong phai ly do de mot lan doc anh thanh cong bao loi.
        }
    }
}
