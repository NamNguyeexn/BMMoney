package com.example.bmmoney.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.SuggestionEntity;
import com.example.bmmoney.ocr.ReceiptImporter;
import com.example.bmmoney.ocr.ReceiptOcr;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MAN DOC ANH GIAO DICH.
 *
 * <p>Tren cung la o chon anh, duoi la danh sach goi y dang cho. Moi dong co hai nut:
 * dau X bo goi y, dau tich mo man them giao dich voi so tien va thoi gian dien san.
 *
 * <h3>App khong luu anh</h3>
 *
 * <p>Anh duoc doc thang tu Uri nguoi dung vua chon roi tha ra. Khong co ban sao nao
 * trong app, nen anh van hoan toan do nguoi dung quan ly - xoa anh trong thu vien la
 * xoa that. Danh sach ben duoi chi giu ba con so may doc duoc kem doan chu da che so
 * tai khoan, du de doi chieu ma khong can mo lai anh.
 *
 * <h3>Vi sao dau tich khong luu thang</h3>
 *
 * <p>May chi doc duoc ba thu: so tien, ngay, gio. Mot giao dich luu thang tu ba thu do
 * la mot giao dich khong co danh muc - no se roi vao nhom "khong phan loai" va lam
 * lech moi bieu do ma chinh nguoi dung dung de ra quyet dinh. Mo man them giao dich
 * ton mot buoc bam, nhung doi lai moi dong vao so deu day du ngay tu dau.
 *
 * <p>Goi y chi duoc danh dau da dung SAU khi luu thanh cong, viec do do
 * {@link AddExpenseFragment} lam. Bo o giua duong thi goi y van con day.
 */
public class SuggestionsFragment extends Fragment {

    /**
     * Ket qua mot lan doc anh chia se, do man nhan anh chia se dua sang.
     *
     * <p>Chi la ba con so dem duoc, khong co anh nao di kem: man kia da doc xong ngay
     * tai cho, va app khong luu anh nen cung khong co gi de chuyen tiep.
     */
    public static final String ARG_ADDED = "added";
    public static final String ARG_DUPLICATE = "duplicate";
    public static final String ARG_FAILED = "failed";

    /** Chi hien thi mot so luong vua phai, con lai cho lan don sau. */
    private static final int MAX_SHOWN = 50;

    /**
     * So anh toi da moi lan chon.
     *
     * <p>Moi anh mat khoang mot phan tu giay de doc, va chung duoc doc lan luot tren
     * mot luong. Muoi anh la khoang cho con chiu duoc ma khong can thanh tien trinh.
     */
    private static final int MAX_PICK = 10;

    private static final String FAILED =
            "\u0110\u1ecdc \u1ea3nh giao d\u1ecbch th\u1ea5t b\u1ea1i";

    private View root;

    private ActivityResultLauncher<PickVisualMediaRequest> picker;

    /** Ngay gio day du, cho nhung goi y doc duoc ca gio. */
    private final SimpleDateFormat withTime =
            new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());

    /** Chi ngay, cho goi y doc tu man danh sach - o do khong he co gio. */
    private final SimpleDateFormat dateOnly =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bo chon anh cua he thong: tra ve Uri da duoc cap quyen cho rieng lan chon
        // do, nen app khong can quyen doc thu vien anh.
        picker = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
                images -> {
                    if (images == null || images.isEmpty()) return;
                    read(new ArrayList<>(images));
                });

        // Nong bo doc san trong luc nguoi dung con dang tim anh.
        Db.io(ReceiptOcr::prepare);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_suggestions, container, false);

        Refresh.setup(root, R.id.refresh_suggestions, this::reload);

        ViewUtils.onClick(root, R.id.btn_suggest_back, v -> back());
        ViewUtils.onClick(root, R.id.btn_dismiss_all, v -> confirmDismissAll());
        ViewUtils.onClick(root, R.id.btn_pick_image, v -> pick());

        // Ket qua tu man nhan anh chia se: chi con viec bao lai cho nguoi dung. Xoa doi
        // so ngay sau khi dung de xoay man hinh khong bao lai lan nua.
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_ADDED)) {
            ReceiptImporter.Result shared = new ReceiptImporter.Result();
            shared.added = args.getInt(ARG_ADDED, 0);
            shared.duplicate = args.getInt(ARG_DUPLICATE, 0);
            shared.failed = args.getInt(ARG_FAILED, 0);
            args.remove(ARG_ADDED);
            args.remove(ARG_DUPLICATE);
            args.remove(ARG_FAILED);
            report(Notice.loading(root, busyLabel(1)), shared);
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Doc lai moi lan quay ve, vi nguoi dung vua tao giao dich tu mot goi y.
        reload();
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------- chon anh

    private void pick() {
        if (picker == null) return;
        try {
            picker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } catch (Throwable error) {
            Notice.error(root, FAILED, null);
        }
    }

    /**
     * Doc anh vua chon.
     *
     * <p>Doc thang tu Uri, khong sao ra ban nao. Quyen doc Uri do bo chon anh cap cho
     * tien trinh nay va con hieu luc trong luc no con song, du cho ca viec doc dien ra
     * o luong nen.
     */
    private void read(List<Uri> images) {
        if (getContext() == null) return;
        final Notice.Handle notice = Notice.loading(root, busyLabel(images.size()));
        Db.io(() -> report(notice, ReceiptImporter.ingest(getContext(), images)));
    }

    /** Bao ket qua mot lan doc anh roi ve lai danh sach. Goi duoc tu luong nen. */
    private void report(Notice.Handle notice, ReceiptImporter.Result result) {
        Db.ui(() -> {
            if (result.isFailure()) {
                // Nguoi dung khong can biet loi nam o dau: anh mo, khong phai bien lai,
                // hay bo doc that bai - ca ba deu dan den cung mot viec la chon anh khac.
                notice.error(FAILED, null);
            } else if (result.added == 0) {
                notice.info("Giao d\u1ecbch n\u00e0y \u0111\u00e3 c\u00f3 trong danh s\u00e1ch");
            } else {
                notice.success("\u0110\u00e3 \u0111\u1ecdc " + result.added
                        + " giao d\u1ecbch t\u1eeb \u1ea3nh");
            }
            reload();
        });
    }

    private String busyLabel(int count) {
        return count <= 1
                ? "\u0110ang \u0111\u1ecdc \u1ea3nh giao d\u1ecbch\u2026"
                : "\u0110ang \u0111\u1ecdc " + count + " \u1ea3nh\u2026";
    }

    // ------------------------------------------------------------ danh sach

    private void reload() {
        if (root == null || getContext() == null) return;
        Db.load(() -> AppDatabase.suggestions(getContext()).pending(MAX_SHOWN), list -> {
            if (root == null) return;
            build(list == null ? new ArrayList<>() : list);
        });
    }

    private void build(List<SuggestionEntity> list) {
        LinearLayout container = root.findViewById(R.id.container_suggestions);
        if (container == null) return;
        container.removeAllViews();

        boolean empty = list.isEmpty();
        ViewUtils.setVisibility(root, R.id.tv_no_suggestion, empty ? View.VISIBLE : View.GONE);
        ViewUtils.setVisibility(root, R.id.btn_dismiss_all, empty ? View.GONE : View.VISIBLE);

        TextView heading = root.findViewById(R.id.tv_suggest_heading);
        if (heading != null) {
            heading.setText(empty
                    ? "G\u1ee3i \u00fd \u0111ang ch\u1edd"
                    : "G\u1ee3i \u00fd \u0111ang ch\u1edd (" + list.size() + ")");
        }
        if (empty) return;

        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (SuggestionEntity item : list) {
            View row = inflater.inflate(R.layout.item_suggestion, container, false);

            TextView amount = row.findViewById(R.id.tv_suggest_amount);
            if (amount != null) {
                amount.setText((Stats.INCOME.equals(item.type) ? "+" : "-")
                        + Money.vnd(item.amount));
            }

            TextView meta = row.findViewById(R.id.tv_suggest_meta);
            if (meta != null) meta.setText(meta(item));

            TextView title = row.findViewById(R.id.tv_suggest_title);
            if (title != null) title.setText(titleOf(item));

            TextView raw = row.findViewById(R.id.tv_suggest_raw);
            if (raw != null) raw.setText(item.rawText);

            View dismiss = row.findViewById(R.id.btn_suggest_dismiss);
            if (dismiss != null) dismiss.setOnClickListener(v -> dismiss(item));

            View accept = row.findViewById(R.id.btn_suggest_accept);
            if (accept != null) accept.setOnClickListener(v -> openAdd(item));

            row.setOnClickListener(v -> openAdd(item));

            container.addView(row);
        }
    }

    /**
     * Dong phu: thoi diem giao dich.
     *
     * <p>Khong co gio thi noi ro "chua ro gi\u1edd" chu khong in "00:00". In 00:00 la
     * khang dinh mot dieu ma anh khong he cho biet, va nguoi dung se tin no.
     */
    private String meta(SuggestionEntity item) {
        Date when = new Date(item.date);
        if (item.hasTime == 1) return withTime.format(when);
        return dateOnly.format(when) + " \u00b7 ch\u01b0a r\u00f5 gi\u1edd";
    }

    /** Ten khoan doan duoc, kem danh muc neu co. Rong thi de nguoi dung tu dat. */
    private String titleOf(SuggestionEntity item) {
        String name = item.title == null || item.title.trim().isEmpty()
                ? "Ch\u01b0a c\u00f3 t\u00ean"
                : item.title.trim();
        if (item.categoryName != null && !item.categoryName.isEmpty()) {
            return name + " \u00b7 " + item.categoryName;
        }
        return name;
    }

    /** Mo man them giao dich voi form da dien san. Goi y chi duoc danh dau khi luu xong. */
    private void openAdd(SuggestionEntity item) {
        if (getContext() == null) return;
        startActivity(AddNoteActivity.from(getContext(), item.id, item.title, item.amount,
                item.type, item.categoryName, item.date, item.rawText));
    }

    private void dismiss(SuggestionEntity item) {
        if (getContext() == null) return;
        Db.io(() -> AppDatabase.suggestions(getContext())
                .setStatus(item.id, SuggestionEntity.DISMISSED));
        Notice.info(root, "\u0110\u00e3 b\u1ecf g\u1ee3i \u00fd n\u00e0y");
        reloadSoon();
    }

    private void confirmDismissAll() {
        if (getContext() == null) return;
        ConfirmDialog.show(getContext(),
                "\ud83e\uddf9",
                "B\u1ecf t\u1ea5t c\u1ea3 g\u1ee3i \u00fd",
                "C\u00e1c g\u1ee3i \u00fd \u0111ang ch\u1edd s\u1ebd bi\u1ebfn kh\u1ecfi danh s\u00e1ch. "
                        + "Giao d\u1ecbch \u0111\u00e3 ghi tr\u01b0\u1edbc \u0111\u00f3 kh\u00f4ng b\u1ecb \u1ea3nh h\u01b0\u1edfng.",
                "B\u1ecf t\u1ea5t c\u1ea3",
                () -> {
                    if (getContext() == null) return;
                    Db.io(() -> AppDatabase.suggestions(getContext()).dismissAllPending());
                    Notice.success(root, "\u0110\u00e3 d\u1ecdn danh s\u00e1ch g\u1ee3i \u00fd");
                    reloadSoon();
                });
    }

    /** Cho database ghi xong roi moi doc lai, tranh doc phai trang thai cu. */
    private void reloadSoon() {
        Db.io(() -> Db.ui(this::reload));
    }

    private void back() {
        if (getActivity() != null) getActivity().finish();
    }
}
