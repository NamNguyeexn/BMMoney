package com.example.bmmoney.remote;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.bmmoney.util.Stats;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Nho Gemini dat ten khoan chi va chon danh muc cho mot dong thong bao.
 *
 * <p>Chi duoc goi khi tren may da chac chan doc ra so tien, va van ban dua sang
 * day luon la ban da che so tai khoan. Moi that bai deu tra ve null de duong
 * doc bang tu khoa van dung duoc.
 */
public final class GeminiParser {

    private static final String TAG = "BmmGemini";

    private static final String MODEL = "gemini-2.5-flash";

    /** Cho toi da 20 giay, qua thi bo, khong de dich vu thong bao bi treo. */
    private static final long TIMEOUT_SECONDS = 20L;

    private GeminiParser() {
    }

    /** Ket qua AI da lam sach, san sang ghi vao bang goi y. */
    public static final class Result {
        public final String title;
        public final long amount;
        public final String type;
        @Nullable public final String category;

        Result(String title, long amount, String type, @Nullable String category) {
            this.title = title;
            this.amount = amount;
            this.type = type;
            this.category = category;
        }
    }

    /**
     * @param maskedText  noi dung thong bao da che so tai khoan
     * @param categories  danh muc dang co, AI chi duoc chon trong danh sach nay
     * @param fallbackAmount so tien doc duoc tren may, dung de doi chieu
     */
    @Nullable
    @WorkerThread
    public static Result parse(String maskedText, List<String> categories, long fallbackAmount) {
        try {
            GenerativeModel model = FirebaseAI
                    .getInstance(GenerativeBackend.googleAI())
                    .generativeModel(MODEL);
            Content content = new Content.Builder().addText(prompt(maskedText, categories)).build();
            GenerateContentResponse response = GenerativeModelFutures.from(model)
                    .generateContent(content)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return read(response == null ? null : response.getText(), categories, fallbackAmount);
        } catch (Throwable error) {
            Log.w(TAG, "parse: khong goi duoc Gemini", error);
            return null;
        }
    }

    private static String prompt(String maskedText, List<String> categories) {
        StringBuilder names = new StringBuilder();
        for (String name : categories) {
            if (name == null || name.trim().isEmpty()) continue;
            if (names.length() > 0) names.append(", ");
            names.append(name.trim());
        }
        return "Ban doc thong bao ngan hang hoac vi dien tu tieng Viet va tra ve JSON thuan,"
                + " khong kem giai thich, khong kem dau ```.\n"
                + "Cac khoa: isTransaction (true/false), title (chuoi ngan tieng Viet co dau,"
                + " toi da 8 tu, mo ta khoan tien), amount (so nguyen dong, khong dau phan cach),"
                + " type (\"EXPENSE\" khi tien di ra, \"INCOME\" khi tien di vao),"
                + " category (chon dung mot ten trong danh sach sau, hoac null neu khong chac).\n"
                + "Danh sach danh muc: " + names + "\n"
                + "Neu day khong phai bien dong so du that su (quang cao, ma OTP, tin nhac no)"
                + " thi tra ve {\"isTransaction\": false}.\n"
                + "Thong bao: " + maskedText;
    }

    @Nullable
    private static Result read(@Nullable String reply, List<String> categories, long fallbackAmount) {
        if (reply == null) return null;
        String text = reply.trim();
        if (text.startsWith("```")) {
            text = text.replace("```json", "").replace("```", "").trim();
        }
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open < 0 || close <= open) return null;

        try {
            JSONObject json = new JSONObject(text.substring(open, close + 1));
            if (!json.optBoolean("isTransaction", true)) return null;

            long amount = json.optLong("amount", 0L);
            if (amount <= 0L || lopsided(amount, fallbackAmount)) {
                // Tin so doc duoc tren may hon khi AI lech qua xa.
                amount = fallbackAmount;
            }
            if (amount <= 0L) return null;

            String type = Stats.INCOME.equalsIgnoreCase(json.optString("type", ""))
                    ? Stats.INCOME : Stats.EXPENSE;

            String title = json.optString("title", "").trim();
            if (title.length() > 60) title = title.substring(0, 60).trim();
            if (title.isEmpty()) return null;

            String category = json.optString("category", "").trim();
            if (category.isEmpty() || "null".equalsIgnoreCase(category)
                    || !containsIgnoreCase(categories, category)) {
                category = null;
            }
            return new Result(title, amount, type, category);
        } catch (Throwable error) {
            Log.w(TAG, "read: JSON tra ve khong doc duoc", error);
            return null;
        }
    }

    /** Hai so lech nhau tu muoi lan tro len thi coi nhu AI doc sai hang. */
    private static boolean lopsided(long left, long right) {
        if (left <= 0L || right <= 0L) return false;
        long big = Math.max(left, right);
        long small = Math.min(left, right);
        return big / small >= 10L;
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(needle)) return true;
        }
        return false;
    }
}
