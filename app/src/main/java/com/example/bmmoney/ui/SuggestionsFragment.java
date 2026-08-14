package com.example.bmmoney.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bmmoney.R;
import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.SuggestionEntity;
import com.example.bmmoney.util.Money;
import com.example.bmmoney.util.Notice;
import com.example.bmmoney.util.NotifySources;
import com.example.bmmoney.util.Refresh;
import com.example.bmmoney.util.Stats;
import com.example.bmmoney.util.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Danh sach goi y doc tu thong bao.
 *
 * <p>Bam vao mot goi y thi mo man them ghi chu voi form da dien san, quay lai thi
 * ve dung day. Bam dau X la bo goi y do di.
 */
public class SuggestionsFragment extends Fragment {

    /** Chi hien thi mot so luong vua phai, con lai cho lan don sau. */
    private static final int MAX_SHOWN = 50;

    private View root;

    private final SimpleDateFormat when =
            new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_suggestions, container, false);

        Refresh.setup(root, R.id.refresh_suggestions, this::reload);

        ViewUtils.onClick(root, R.id.btn_suggest_back, v -> back());
        ViewUtils.onClick(root, R.id.btn_dismiss_all, v -> confirmDismissAll());
        ViewUtils.onClick(root, R.id.btn_grant_access, v -> {
            if (getContext() == null) return;
            NotifySources.openAccessSettings(getContext());
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Doc lai moi lan quay ve, vi nguoi dung vua tao giao dich tu mot goi y.
        reload();
    }

    private void reload() {
        if (root == null || getContext() == null) return;

        boolean granted = NotifySources.hasAccess(getContext());
        ViewUtils.setVisibility(root, R.id.card_no_access, granted ? View.GONE : View.VISIBLE);

        TextView sub = root.findViewById(R.id.tv_suggest_sub);
        if (sub != null) {
            sub.setText(NotifySources.enabled(getContext())
                    ? "Ch\u1ecdn m\u1ed9t g\u1ee3i \u00fd \u0111\u1ec3 ghi th\u00e0nh giao d\u1ecbch"
                    : "\u0110ang t\u1eaft. B\u1eadt trong C\u00e0i \u0111\u1eb7t \u0111\u1ec3 b\u1eaft \u0111\u1ea7u nh\u1eadn g\u1ee3i \u00fd");
        }

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
        if (empty) return;

        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (SuggestionEntity item : list) {
            View row = inflater.inflate(R.layout.item_suggestion, container, false);

            TextView title = row.findViewById(R.id.tv_suggest_title);
            if (title != null) title.setText(item.title);

            TextView meta = row.findViewById(R.id.tv_suggest_meta);
            if (meta != null) meta.setText(meta(item));

            TextView amount = row.findViewById(R.id.tv_suggest_amount);
            if (amount != null) {
                amount.setText((Stats.INCOME.equals(item.type) ? "+" : "-")
                        + Money.vnd(item.amount));
            }

            TextView raw = row.findViewById(R.id.tv_suggest_raw);
            if (raw != null) raw.setText(item.rawText);

            View dismiss = row.findViewById(R.id.btn_suggest_dismiss);
            if (dismiss != null) dismiss.setOnClickListener(v -> dismiss(item));

            row.setOnClickListener(v -> openAdd(item));

            container.addView(row);
        }
    }

    /** Dong phu: ten app, gio nhan, danh muc doan duoc va dau hieu chua qua AI. */
    private String meta(SuggestionEntity item) {
        StringBuilder out = new StringBuilder();
        out.append(item.appLabel);
        out.append(" \u00b7 ").append(when.format(new Date(item.date)));
        if (item.categoryName != null && !item.categoryName.isEmpty()) {
            out.append(" \u00b7 ").append(item.categoryName);
        }
        if (item.aiParsed == 0) {
            out.append(" \u00b7 ch\u01b0a qua AI");
        }
        return out.toString();
    }

    /** Mo man them ghi chu voi form da dien san. Goi y chi duoc danh dau khi luu xong. */
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
        // Bo khoi danh sach ngay, khong cho database tra loi.
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
