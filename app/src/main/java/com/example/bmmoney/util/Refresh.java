package com.example.bmmoney.util;

import android.view.View;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bmmoney.R;

/** C\u1ea5u h\u00ecnh v\u00f2ng quay "k\u00e9o \u0111\u1ec3 t\u1ea3i l\u1ea1i" theo \u0111\u00fang m\u00e0u c\u1ee7a \u1ee9ng d\u1ee5ng. */
public final class Refresh {

    private Refresh() {
    }

    public static SwipeRefreshLayout setup(View root, int id, final Runnable onRefresh) {
        if (root == null) return null;
        final SwipeRefreshLayout layout = root.findViewById(id);
        if (layout == null) return null;

        layout.setColorSchemeResources(R.color.olive, R.color.burnt, R.color.sandy);
        layout.setProgressBackgroundColorSchemeResource(R.color.card_cream);
        layout.setOnRefreshListener(() -> {
            if (onRefresh != null) onRefresh.run();
        });
        return layout;
    }
}
