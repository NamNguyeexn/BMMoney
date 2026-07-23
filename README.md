# ExpenseManager Java Android - Bản cá nhân online tối giản

App quản lý thu chi cá nhân bằng Java, Room local cache và Firebase Firestore.

## Điểm đã tối ưu cho 1 người dùng
- Bỏ màn hình đăng nhập / đăng ký.
- Mở app là vào thẳng Trang chủ.
- Không dùng Firebase Auth để giảm bước thao tác.
- Không realtime listener liên tục, không service nền.
- Chỉ sync khi mở app, khi thêm giao dịch, hoặc bấm đồng bộ thủ công trong Cài đặt.
- Dữ liệu vẫn lưu local bằng Room để mở nhanh và dùng được khi mạng yếu.

## Firebase
Đã đặt `google-services.json` trong `app/google-services.json`.

Firestore path dùng cho app cá nhân:
```
personal_wallets/nam_personal_wallet/transactions
personal_wallets/nam_personal_wallet/settings/theme
```

## Rules gợi ý khi chỉ có bạn dùng
Nếu muốn khóa theo email/tài khoản, nên bật Auth rồi dùng rules riêng. Nếu muốn cực nhanh trong giai đoạn test cá nhân, có thể dùng rules tạm thời giới hạn thời gian trong Firebase Console.

## Mở project
1. Giải nén ZIP.
2. Android Studio > Open > chọn thư mục ExpenseManager.
3. Sync Gradle.
4. Build > Clean Project.
5. Build > Rebuild Project.
6. Run app.

## CI/CD đã thêm sẵn
Project đã có workflow:

```text
.github/workflows/firebase-distribution.yml
```

Cần tạo GitHub Secrets:

```text
FIREBASE_APP_ID
FIREBASE_SERVICE_ACCOUNT
GOOGLE_SERVICES_JSON   # tùy chọn nếu không commit app/google-services.json
```

Mỗi lần push lên nhánh `main`, GitHub Actions sẽ build APK và upload lên Firebase App Distribution group `testers`.

Xem file `UPDATE_EASY_WAY.md` để biết cách copy ZIP mới vào project chính và push Git.
