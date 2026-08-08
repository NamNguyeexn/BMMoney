# BMMoney — Cấu trúc dữ liệu trên Firestore

Bản này thay thế hoàn toàn kiểu "một cục JSON cắt mảnh" của phiên bản trước.

---

## 1. Cây dữ liệu

```
users/{uid}                     email, displayName, lastSeenAt
  ├── meta/sync                 updatedAt, count, device, schemaVersion, settings{}
  ├── cats/{categoryId}         name, emoji, kind, sortOrder, archived, updatedAt, deleted
  ├── people/{partnerId}        name, phone, note, updatedAt, deleted
  ├── loans/{loanId}            partnerName, direction, principal, rate,
  │                             openedDate, dueDate, settled, writtenOff, updatedAt, deleted
  └── tx/{transactionId}        type, amount, date, title, note,
                                categoryName, partnerName, loanId,
                                dueDate, settled, writtenOff, rate, updatedAt, deleted
```

### Tại sao không đặt tên là `transactions`

Hàm `cleanupLegacy()` đang có nhiệm vụ **xóa sạch** hai collection cũ:

| Collection cũ | Của bản nào |
|---|---|
| `users/{uid}/transactions` | bản per-document đời đầu |
| `users/{uid}/backup` | bản JSON cắt mảnh `part_0..N` |

Nếu bản mới cũng dùng tên `transactions` thì cứ sau mỗi lần sao lưu thành công, app sẽ tự xóa đúng dữ liệu vừa đẩy lên. Vì vậy bản mới dùng `tx` / `cats` / `people`. `loans` là tên mới hoàn toàn nên an toàn.

---

## 2. Quy tắc khóa tài liệu

| Collection | Doc ID | Ghi chú |
|---|---|---|
| `cats` | `categoryId` (số cục bộ) | chỉ dùng làm khóa cho ổn định, **không** dùng để liên kết |
| `people` | `partnerId` (số cục bộ) | như trên |
| `loans` | `loanId` (chuỗi `L…`) | đã duy nhất toàn cục, sinh bởi `LoanDao.newLoanId()` |
| `tx` | `transactionId` (số cục bộ) | |

### Không bao giờ đẩy id cục bộ lên làm liên kết

`categoryId` và `partnerId` là số tự tăng của **riêng từng máy**. Máy khác đánh số khác hẳn. Nếu đồng bộ id, một giao dịch "Ăn uống" ở máy A tải về máy B có thể thành "Y tế".

Vì thế tài liệu `tx` và `loans` chỉ lưu **`categoryName` / `partnerName`**. Lúc kéo về, `applyRestore()` tra tên → id của chính máy đó, thiếu thì `ensure(...)` tạo mới.

Các cột `dayKey` / `monthKey` / `yearKey` **không** được đẩy lên: chúng suy ra từ `date`, và `TransactionEntity.setDate()` tự tính lại khi khôi phục.

---

## 3. Đẩy lên — chỉ phần thay đổi

1. Đọc `meta/sync` từ **máy chủ** (không phải cache).
2. Lấy `updatedAt` trong đó làm mốc `since`.
3. `since > 0` → chỉ lấy `changedSince(since)` của bốn DAO. `since = 0` → lấy `getAllForSync()` (toàn bộ).
4. Ghi theo lô, mỗi lô tối đa **400** thao tác (giới hạn cứng của `WriteBatch` là 500).
5. **Dữ liệu xong hẳn mới ghi `meta/sync`.**

Bước 5 là bắt buộc. Mốc trong `meta/sync` là căn cứ cho lần đẩy sau; ghi mốc trước mà dữ liệu hỏng giữa chừng thì lần sau app tưởng đã đẩy hết và **bỏ qua vĩnh viễn** những bản ghi còn thiếu.

Đọc `meta/sync` mà rơi về cache thì coi như không biết mốc → đẩy toàn bộ cho chắc.

---

## 4. Kéo về — đúng thứ tự khóa ngoại

Room bật `PRAGMA foreign_keys = ON`, nên thứ tự không thể đảo:

```
xóa:   tx  →  loans
chèn:  cats  →  people  →  loans  →  tx
```

Trong bước chèn `tx`, gặp `loanId` chưa có đầu khoản vay thì **dựng tạm một đầu** (`principal = 0`, hướng suy từ loại giao dịch: `REPAY`/`BORROW` → `BORROW`, còn lại → `LEND`). Không có bước này, một bản sao lưu thiếu đầu khoản vay sẽ làm hỏng cả mẻ chèn.

### Ba chốt an toàn

| Tình huống | Xử lý |
|---|---|
| Cloud không có giao dịch sống mà máy đang có dữ liệu | **Từ chối kéo về**, báo "bản trên cloud đang rỗng" |
| `cats` trên cloud rỗng | **Không** `wipe()` bảng danh mục — nếu không sẽ mất luôn bộ danh mục mặc định |
| `people` trên cloud rỗng | tương tự |

Sau khi ghi xong: áp `settings`, đặt lại `Prefs.lastBackup` / `localChangedAt` bằng mốc của cloud, rồi `Categories.refresh()` để bộ nhớ đệm danh mục không còn số cũ.

---

## 5. Xóa

`deleteBackup()` xóa lần lượt `tx` → `loans` → `cats` → `people`, cuối cùng mới xóa `meta/sync`, mỗi lô 400 tài liệu. Dữ liệu dưới máy không bị đụng tới.

`cleanupLegacy()` chỉ dọn `transactions` và `backup`, chạy ngầm sau mỗi lần sao lưu thành công, một lần cho mỗi máy (cờ `Prefs.legacyCleaned`).

---

## 6. Nạp cấu hình

```bash
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

`firestore.rules` khóa chặt: chỉ chủ tài khoản đọc/ghi được nhánh `users/{uid}` của mình.

`firestore.indexes.json` khai báo bốn chỉ mục ghép. **Hiện tại app chưa cần chúng** vì nó đọc nguyên cả collection. Chúng dành cho bước sau (kéo về từng phần theo `updatedAt`, lọc theo `type`, xem khoản vay theo hạn). Chưa muốn thì cứ bỏ qua lệnh deploy indexes.

---

## 7. Còn nợ

- **Kéo về vẫn là toàn bộ.** Đẩy lên đã tăng dần, nhưng `restoreLatest()` đọc nguyên bốn collection. Muốn kéo tăng dần thì thêm `whereGreaterThan("updatedAt", mốc)` và xử lý bia mộ (`deleted = 1`) thay vì `wipe()`.
- **Chưa trộn hai chiều.** `syncNow()` vẫn quyết định *hoặc* đẩy *hoặc* kéo, không hòa hai bên. Muốn trộn thật thì mỗi giao dịch cần một `uuid` cố định thay vì id tự tăng.
- Bia mộ trên cloud (`deleted = 1`) hiện chỉ được ghi lên chứ chưa bao giờ được dọn.
