package com.example.bmmoney.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.CategoryDao;
import com.example.bmmoney.data.CategoryEntity;
import com.example.bmmoney.data.Db;

import java.util.ArrayList;
import java.util.List;

/**
 * CAU NOI danh muc: giu nguyen cach goi cu, nhung nguon du lieu da la BANG THAT.
 *
 * <h3>Vi sao khong xoa han lop nay</h3>
 *
 * <p>Sau man hinh dang goi {@code Categories.all(context)} va {@code Categories.names(...)}
 * ngay tren luong giao dien. Doi tat ca sang goi DAO se keo theo mot loat sua doi
 * khong lien quan gi den viec chuan hoa du lieu. Giu lai lop nay nghia la thay doi
 * nam gon trong tang du lieu.</p>
 *
 * <h3>Van de phai giai: Room CAM truy van tren luong giao dien</h3>
 *
 * <p>{@code all(Context)} tra ve ket qua NGAY lap tuc, con Room thi nem
 * {@code IllegalStateException} neu bi goi tren luong chinh. Hai dieu nay khong the
 * cung dung neu doc thang.</p>
 *
 * <p>Cach giai: giu mot ban sao trong bo nho. Bo nho duoc nap san luc mo app
 * ({@link #preload(Context)} goi tu {@code BmmApp}), doc ra thi tra ve tuc thi, con
 * moi lan ghi deu day xuong luong nen roi lam moi ban sao.</p>
 *
 * <p>Danh sach danh muc chi vai chuc dong va rat it khi doi, nen giu trong bo nho la
 * hop ly - day khong phai bo nho dem tam bo, ma la dung ban chat du lieu.</p>
 */
public final class Categories {

    /** Ban sao trong bo nho. {@code volatile} de luong nen ghi xong thi luong giao dien thay ngay. */
    private static volatile List<Item> cache;

    private Categories() {
    }

    /** Mot danh muc nhin tu phia giao dien. */
    public static class Item {
        public String emoji;
        public String name;

        /** Id trong bang. 0 nghia la dong moi chua luu. */
        public int id;

        /** "emoji ten", dung cho o chon va the loc. Khop voi CategoryEntity.label(). */
        public String label() {
            String e = emoji == null || emoji.isEmpty()
                    ? CategoryEntity.FALLBACK_EMOJI : emoji;
            return e + " " + (name == null ? "" : name);
        }

        public Item(String emoji, String name) {
            this.emoji = emoji;
            this.name = name;
        }

        public Item(int id, String emoji, String name) {
            this.id = id;
            this.emoji = emoji;
            this.name = name;
        }
    }

    /**
     * Nap san ban sao. Goi tu {@code BmmApp.onCreate} tren luong nen.
     *
     * <p>Nho buoc nay ma man hinh dau tien da co san danh muc de ve, khong phai doi
     * mot vong truy van.</p>
     */
    public static void preload(final Context context) {
        Db.io(new Runnable() {
            @Override
            public void run() {
                refresh(context);
            }
        });
    }

    /** Doc lai tu bang. <b>Phai goi tu luong nen.</b> */
    public static List<Item> refresh(Context context) {
        List<Item> list = new ArrayList<>();
        try {
            List<CategoryEntity> rows = AppDatabase.categories(context).active();
            if (rows != null) {
                for (CategoryEntity row : rows) {
                    list.add(new Item(row.getId(), row.emojiOrTag(), row.nameOrEmpty()));
                }
            }
        } catch (Throwable ignored) {
            // Co so du lieu chua san sang - lan sau nap lai
        }
        cache = list;
        return list;
    }

    /**
     * Danh sach danh muc dang hien.
     *
     * <p>Tra ve tuc thi tu ban sao. Neu ban sao chua kip nap thi tra ve danh sach
     * rong chu KHONG doc co so du lieu - doc o day se lam treo luong giao dien.</p>
     */
    public static List<Item> all(Context context) {
        List<Item> snapshot = cache;
        if (snapshot == null) {
            preload(context);
            return new ArrayList<>();
        }
        return new ArrayList<>(snapshot);
    }

    /** Ban sao trong bo nho da nap xong chua. */
    public static boolean isReady() {
        return cache != null;
    }

    /**
     * Cho ban sao nap xong roi chay {@code done} tren luong giao dien.
     *
     * <p>Vi sao can: {@link #all(Context)} tra ve danh sach RONG khi ban sao chua kip
     * nap. Man hinh nao ve danh muc ngay luc vua mo app se hien "0 muc" hoac mat sach
     * the loc, va khong co gi keo no ve lai - phai roi di roi quay lai moi thay.</p>
     *
     * <p>Sau khi {@code done} chay thi {@link #isReady()} chac chan tra ve {@code true}
     * (du danh sach co the van rong that), nen ben goi kiem tra {@code isReady()} truoc
     * roi goi lai chinh no se khong bao gio lap vo han.</p>
     */
    public static void whenReady(final Context context, @Nullable final Runnable done) {
        if (cache != null) {
            if (done != null) Db.ui(done);
            return;
        }
        Db.io(new Runnable() {
            @Override
            public void run() {
                refresh(context);
                if (done != null) Db.ui(done);
            }
        });
    }

    public static String[] names(Context context) {
        List<Item> list = all(context);
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i).name;
        return out;
    }

    /**
     * Ghi lai toan bo danh sach tu man Cai dat.
     *
     * <p>Danh muc bi bo khoi danh sach se duoc AN ({@code archived = 1}) chu khong
     * xoa. Xoa that se keo theo {@code ON DELETE SET NULL} lam moi giao dich cu mat
     * danh muc - lich su chi tieu thung mot mang ma khong the khoi phuc.</p>
     */
    public static void save(final Context context, final List<Item> list) {
        save(context, list, null);
    }

    /**
     * Nhu {@link #save(Context, List)}, nhung bao lai khi da ghi VA nap lai xong.
     *
     * <p>Vi sao can {@code done}: viec ghi chay tren luong nen, con ben goi truoc day ve
     * lai danh sach NGAY dong ke tiep - tuc la ve bang ban sao CU. Doi ten, sap xep lai,
     * them hay xoa mot danh muc deu khong thay gi doi cho den luc roi khoi man hinh roi
     * quay lai. Chay {@code done} sau khi lam moi ban sao thi giao dien khop ngay.</p>
     */
    public static void save(final Context context, final List<Item> list,
                            @Nullable final Runnable done) {
        final List<Item> copy = list == null ? new ArrayList<Item>() : new ArrayList<>(list);
        Db.io(new Runnable() {
            @Override
            public void run() {
                try {
                    apply(context, copy);
                } catch (Throwable ignored) {
                    // Khong de loi ghi lam sap app
                }
                refresh(context);
                if (done != null) Db.ui(done);
            }
        });
    }

    /** <b>Phai goi tu luong nen.</b> */
    private static void apply(Context context, List<Item> list) {
        CategoryDao dao = AppDatabase.categories(context);
        long now = System.currentTimeMillis();

        List<Integer> keep = new ArrayList<>();
        int order = 0;

        for (Item item : list) {
            if (item == null || item.name == null) continue;
            String name = item.name.trim();
            if (name.isEmpty()) continue;
            order++;

            String emoji = item.emoji == null || item.emoji.isEmpty()
                    ? CategoryEntity.FALLBACK_EMOJI : item.emoji;

            CategoryEntity row = item.id > 0 ? dao.byId(item.id) : dao.byName(name);
            if (row == null) {
                Integer id = dao.ensure(name, emoji);
                if (id != null) keep.add(id);
                continue;
            }

            // Doi ten o day la MOI bao cao doi theo, vi giao dich chi giu khoa so.
            row.setName(name);
            row.setEmoji(emoji);
            row.setSortOrder(order);
            row.setArchived(0);
            row.setUpdatedAt(now);
            dao.update(row);
            keep.add(row.getId());
        }

        // Danh muc khong con trong danh sach: AN di, khong xoa.
        List<CategoryEntity> current = dao.all();
        if (current == null) return;
        for (CategoryEntity row : current) {
            if (keep.contains(row.getId())) continue;
            if (row.isArchived()) continue;
            dao.setArchived(row.getId(), 1, now);
        }
    }
}
