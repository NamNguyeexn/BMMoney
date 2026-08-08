package com.example.bmmoney.data;

/**
 * Mot o tong hop theo moc thoi gian: ngay, thang hoac nam.
 *
 * <p>{@code bucket} la khoa ngay dang so nguyen ma bang giao dich luu san:
 * {@code 20260807} cho ngay, {@code 202608} cho thang, {@code 2026} cho nam.</p>
 *
 * <p>Nho co san cot nay, bieu do theo thang chi con mot cau GROUP BY chay tren
 * index. Neu nhom bang {@code strftime(date)} thi bieu thuc do KHONG dung duoc
 * index nen moi lan ve bieu do la mot lan quet toan bang.</p>
 */
public class BucketTotal {

    /** 20260807 / 202608 / 2026 tuy cau truy van goi ngay, thang hay nam. */
    public int bucket;

    public long total;

    public int items;
}
