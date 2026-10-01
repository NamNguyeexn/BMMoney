# Hướng dẫn chạy (bước 2 → 3)

## Bước 2 — chuyển JSON
Dump anh gửi đã che UID ("xxxx…"), nên phải truyền UID thật (xem trong Firebase Console → Authentication, hoặc tên document trong `users`):

```bash
python3 convert.py firestore-dump.json bmm-v6.json --uid KBaoWlOOSTQfPQem1PnyKEKOMyf2
```
Script in số lượng + cảnh báo. ID sinh cố định từ ID cũ → chạy lại không tạo bản trùng.

## Bước 3 — nạp lên Firestore
```bash
cd upload
npm install
cp /đường/dẫn/serviceAccount.json .      # KHÔNG gửi file này cho ai
node upload.mjs ../bmm-v6.json            # chạy thử: kiểm tra liên kết, in kế hoạch, KHÔNG ghi
node upload.mjs ../bmm-v6.json --commit   # ghi thật, rồi đối chiếu số lượng cloud vs file
```
Sau đó dán `firestore.rules` vào Firebase Console → Firestore → Rules → Publish.

An toàn:
- Chỉ ghi vào `accounts/{uid}`, **không đụng `users/{uid}`** → app bản hiện tại vẫn đồng bộ như cũ.
- Rules vẫn giữ quyền cho `users/{uid}` trong thời gian chuyển.
- Muốn làm lại: xóa `accounts/{uid}` trên Console rồi chạy lại.

## Bước 4 — cập nhật app (sync mới + sửa/xóa)
1. Chép đè thư mục `app/` trong `bmm-step4.zip` vào project (giữ nguyên cấu trúc `app/src/main/...`).
2. Dán lại `firestore.rules` (bản này cho phép `personId` trống, thêm mã thanh toán CARD/EWALLET/OTHER) → Publish.
3. Build & cài app. Cài đặt → **Đồng bộ**. Lần đầu app đẩy toàn bộ dữ liệu máy lên `accounts/{uid}` (nếu đã nạp bmm-v6.json ở bước 3 thì ID trùng khớp, không bị nhân đôi).
4. Nếu lỗi: `adb logcat -s BmmSync` rồi gửi log.
