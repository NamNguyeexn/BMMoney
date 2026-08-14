package com.example.bmmoney.notify;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.bmmoney.data.AppDatabase;
import com.example.bmmoney.data.Db;
import com.example.bmmoney.data.SuggestionEntity;
import com.example.bmmoney.remote.GeminiParser;
import com.example.bmmoney.util.MoneyParse;
import com.example.bmmoney.util.NotifySources;
import com.example.bmmoney.util.TextNorm;

import java.security.MessageDigest;
import java.util.List;

/**
 * Bien mot thong bao thanh mot dong goi y.
 *
 * <p>Trinh tu: doc so tien tren may, che so tai khoan, chong trung, luu vao bang,
 * roi moi nho Gemini dat ten lai. Nho vay tat AI hay mat mang thi van con goi y.
 */
public final class SuggestionEngine {

    private static final String TAG = "BmmSuggest";

    /** Cung mot thong bao trong hai phut chi luu mot lan. */
    private static final long DEDUPE_WINDOW_MS = 2L * 60L * 1000L;

    /** Goi y qua 60 ngay thi don di. */
    private static final long PURGE_AFTER_MS = 60L * 24L * 60L * 60L * 1000L;

    /** Chi giu mot doan noi dung du de doi chieu, khong luu ca bai. */
    private static final int MAX_RAW = 400;

    private SuggestionEngine() {
    }

    /**
     * Xet mot thong bao vua den. Goi duoc tu bat ky luong nao,
     * phan doc ghi database luon chay trong Db.io.
     */
    public static void consider(final Context context, @Nullable final String packageName,
                                @Nullable final String title, @Nullable final String text,
                                final long postTime) {
        if (context == null || packageName == null) return;
        if (!NotifySources.enabled(context)) return;
        if (!NotifySources.isWatched(context, packageName)) return;

        final Context app = context.getApplicationContext();
        Db.io(new Runnable() {
            @Override
            public void run() {
                process(app, packageName, title, text, postTime);
            }
        });
    }

    /** Dung cho luc dich vu vua ket noi va quet lai cac thong bao con tren man hinh. */
    public static void considerAll(Context context, String[] packages, String[] titles,
                                   String[] texts, long[] times) {
        if (packages == null) return;
        for (int i = 0; i < packages.length; i++) {
            consider(context, packages[i],
                    titles == null || i >= titles.length ? null : titles[i],
                    texts == null || i >= texts.length ? null : texts[i],
                    times == null || i >= times.length ? System.currentTimeMillis() : times[i]);
        }
    }

    @WorkerThread
    private static void process(Context context, String packageName, @Nullable String title,
                               @Nullable String text, long postTime) {
        try {
            String joined = join(title, text);
            if (joined.isEmpty()) return;

            // Doc so tien truoc khi che, vi che xong thi khong con doc duoc nua.
            MoneyParse.Found found = MoneyParse.find(joined);
            if (found == null) return;

            String masked = MoneyParse.mask(joined);
            if (masked.length() > MAX_RAW) masked = masked.substring(0, MAX_RAW);

            long when = postTime > 0L ? postTime : System.currentTimeMillis();
            String key = dedupeKey(packageName, masked, when);
            if (AppDatabase.suggestions(context).countByKey(key) > 0) return;

            SuggestionEntity item = new SuggestionEntity(
                    key,
                    packageName,
                    NotifySources.labelOf(context, packageName),
                    masked,
                    shorten(masked),
                    found.amount,
                    found.type,
                    MoneyParse.guessCategory(joined),
                    when,
                    System.currentTimeMillis());

            long rowId = AppDatabase.suggestions(context).insertIgnore(item);
            if (rowId <= 0L) return;

            AppDatabase.suggestions(context)
                    .purgeOlderThan(System.currentTimeMillis() - PURGE_AFTER_MS);

            if (!NotifySources.aiEnabled(context)) return;

            List<String> names = AppDatabase.categories(context).activeNames();
            GeminiParser.Result better = GeminiParser.parse(masked, names, found.amount);
            if (better == null) return;

            AppDatabase.suggestions(context).refine((int) rowId,
                    better.title, better.amount, better.type, better.category);
        } catch (Throwable error) {
            Log.w(TAG, "process: khong xu ly duoc thong bao", error);
        }
    }

    /**
     * Khoa chong trung gom package, noi dung da chuan hoa va moc thoi gian theo cua so hai phut.
     */
    private static String dedupeKey(String packageName, String masked, long when) {
        String seed = packageName + "|" + TextNorm.normalize(masked)
                + "|" + (when / DEDUPE_WINDOW_MS);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(seed.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte value : bytes) {
                out.append(Integer.toHexString((value & 0xFF) | 0x100).substring(1, 3));
            }
            return out.toString();
        } catch (Throwable error) {
            return String.valueOf(seed.hashCode());
        }
    }

    private static String join(@Nullable String title, @Nullable String text) {
        String left = title == null ? "" : title.trim();
        String right = text == null ? "" : text.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        if (right.startsWith(left)) return right;
        return left + " \u2014 " + right;
    }

    /** Tieu de tam thoi khi chua co AI: mot doan dau cua thong bao. */
    private static String shorten(String masked) {
        String value = masked.trim();
        if (value.length() <= 48) return value;
        return value.substring(0, 48).trim() + "\u2026";
    }
}
