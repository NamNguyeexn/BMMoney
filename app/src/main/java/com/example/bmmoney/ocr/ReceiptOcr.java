package com.example.bmmoney.ocr;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DOC CHU TU ANH, HOAN TOAN TREN MAY.
 *
 * <p>Dung ML Kit Text Recognition v2 ban Latin, nhung san trong APK. Khong goi mang,
 * khong gui anh di dau, khong gan voi tai khoan Google nao - anh bien lai co so tai
 * khoan va so tien, day la loai du lieu khong nen roi khoi thiet bi.
 *
 * <h3>Vi sao tra ve toa do chu khong chi tra ve chu</h3>
 *
 * <p>Man "Hoat dong gan day" cua ngan hang la mot bang: ten noi nhan o cot trai, so
 * tien o cot phai, tieu de ngay nam tren dau moi nhom. Neu chi lay chuoi chu thi thu
 * tu doc cua ML Kit khong bao dam giu dung cap ten - so tien, va "6,000" co the bi
 * gan cho dong ben tren no. Toa do la thu duy nhat cho biet hai manh chu nao thuc su
 * nam tren cung mot hang.
 */
public final class ReceiptOcr {

    /**
     * Han cho doc mot anh.
     *
     * <p>Thuc te chi mat 100-300 ms. Dat 20 giay khong phai vi cham, ma de mot anh
     * loi khong treo vinh vien cai luong dung chung cua Db.
     */
    private static final long TIMEOUT_SECONDS = 20L;

    private static volatile TextRecognizer recognizer;

    private ReceiptOcr() {
    }

    /** Mot dong chu doc duoc, kem vi tri cua no tren anh. */
    public static final class Line {
        public final String text;
        public final int top;
        public final int bottom;
        public final int left;

        Line(String text, int top, int bottom, int left) {
            this.text = text;
            this.top = top;
            this.bottom = bottom;
            this.left = left;
        }

        public int height() {
            return Math.max(1, bottom - top);
        }

        public int centerY() {
            return (top + bottom) / 2;
        }

        /** Hai dong co nam tren cung mot hang khong. */
        public boolean sameRowAs(Line other) {
            int slack = Math.max(height(), other.height()) / 2;
            return Math.abs(centerY() - other.centerY()) <= slack;
        }
    }

    /**
     * Tao san bo doc.
     *
     * <p>Lan khoi tao dau tien mat khoang mot phan tu giay. Goi truoc luc nguoi dung
     * bam chon anh thi cai gia do bi che khuat sau thao tac chon anh cua ho.
     */
    public static TextRecognizer recognizer() {
        TextRecognizer local = recognizer;
        if (local == null) {
            synchronized (ReceiptOcr.class) {
                local = recognizer;
                if (local == null) {
                    local = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    recognizer = local;
                }
            }
        }
        return local;
    }

    /** Nong may san. Goi duoc tu bat ky luong nao, khong nem loi. */
    public static void prepare() {
        try {
            recognizer();
        } catch (Throwable ignored) {
            // Khong co bo doc thi luc doc that se bao loi, khong can om them o day.
        }
    }

    /**
     * Doc chu tu mot anh ma nguoi dung vua chi ra.
     *
     * <p>Doc THANG tu Uri cua nguoi dung, khong sao ra ban nao. Anh chi di qua bo nho
     * trong vai tram mili giay roi bien; app khong giu lai gi ngoai ba con so no can.
     *
     * <p>Chay DONG BO, nen chi duoc goi trong Db.io, va chi duoc goi khi tien trinh con
     * quyen doc Uri do - tuc la ngay trong man hinh da nhan duoc no.
     *
     * @throws Exception khi khong mo duoc anh hoac bo doc that bai
     */
    public static List<Line> read(Context context, Uri image) throws Exception {
        InputImage input = InputImage.fromFilePath(context, image);
        Text result = Tasks.await(recognizer().process(input), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<Line> lines = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                String text = line.getText();
                if (box == null || text == null || text.trim().isEmpty()) continue;
                lines.add(new Line(text.trim(), box.top, box.bottom, box.left));
            }
        }

        // ML Kit tra ve theo khoi, khong theo thu tu doc. Sap lai tu tren xuong de
        // tieu de ngay luon den TRUOC nhung dong thuoc ngay do.
        Collections.sort(lines, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                int byTop = Integer.compare(a.centerY(), b.centerY());
                return byTop != 0 ? byTop : Integer.compare(a.left, b.left);
            }
        });
        return lines;
    }
}
