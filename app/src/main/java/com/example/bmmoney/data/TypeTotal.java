package com.example.bmmoney.data;

/**
 * Tong tien gop theo loai ghi chu (EXPENSE / INCOME / LEND / BORROW / ...).
 *
 * <p>{@code total} de {@code double} cung ly do nhu {@link CategoryTotal}: day la
 * so lieu bao cao dung de chia ty le, khong phai so tien luu tru.</p>
 */
public class TypeTotal {

    public String type;

    public double total;

    public int items;
}
