# Cách dễ nhất để cập nhật project và đẩy Git

Bạn chỉ giữ **một project chính** trên máy, ví dụ:

```bash
/home/namnguyeexn/project/thu_chi_app
```

Mỗi lần nhận ZIP mới:

## 1. Giải nén ZIP vào thư mục tạm
Ví dụ:

```bash
/home/namnguyeexn/Downloads/ExpenseManager
```

## 2. Copy đè vào project chính bằng rsync

```bash
rsync -av \
  --exclude='.git' \
  --exclude='.gradle' \
  --exclude='.idea' \
  --exclude='build' \
  --exclude='app/build' \
  --exclude='local.properties' \
  /home/namnguyeexn/Downloads/ExpenseManager/ \
  /home/namnguyeexn/project/thu_chi_app/
```

## 3. Kiểm tra build

```bash
cd /home/namnguyeexn/project/thu_chi_app
./gradlew assembleDebug
```

## 4. Commit và push

```bash
git status
git add .
git commit -m "Update generated app"
git push
```

Sau khi `git push`, GitHub Actions sẽ tự build và gửi bản mới lên Firebase App Distribution.
