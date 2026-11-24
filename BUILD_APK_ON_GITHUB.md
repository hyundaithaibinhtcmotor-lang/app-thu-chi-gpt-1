# Biên dịch APK bằng GitHub Actions (không cần Android Emulator)

Gói này đã có workflow tại `.github/workflows/build-apk.yml`. GitHub sẽ dùng máy chủ Ubuntu để cài JDK 17, Gradle 8.9 và Android SDK 35 rồi chạy `assembleDebug`.

## A. Đưa mã nguồn lên GitHub bằng GitHub Desktop

1. Tải và cài GitHub Desktop: https://desktop.github.com/ (nếu máy đã có thì mở ứng dụng).
2. Đăng nhập GitHub.
3. Chọn **File → Add local repository** nếu thư mục dự án đã là Git repository; nếu chưa, chọn **File → New repository** và tạo repository mới.
4. Đặt tên repository, ví dụ `TaiChinhCaNhanAndroid`. Chọn thư mục dự án `TaiChinhCaNhan_Android_v0.4.0` làm local path (hoặc tạo repository ở thư mục khác rồi chép toàn bộ nội dung dự án vào đó).
5. Đảm bảo thư mục `.github/workflows/build-apk.yml` được giữ nguyên. Nếu Windows không hiện thư mục `.github`, bật hiển thị mục ẩn trong File Explorer.
6. Trong GitHub Desktop, ghi mô tả commit, chọn **Commit to main**, sau đó **Publish repository**.

> Quan trọng: phải đẩy các thư mục `app`, `.github` và các file Gradle lên repository. Không chỉ tải riêng file ZIP nguồn lên GitHub, vì Actions không tự giải nén ZIP để build.

## B. Chạy build trên GitHub

1. Mở repository vừa Publish trên github.com.
2. Chọn tab **Actions**.
3. Chọn workflow **Build Android APK** ở cột bên trái.
4. Nhấn **Run workflow** → chọn nhánh `main` → **Run workflow**.
5. Chờ job màu xanh (Success). Lần đầu có thể mất vài phút để tải Gradle và Android SDK.

Workflow cũng tự chạy khi bạn push commit mới lên nhánh `main` hoặc `master`.

## C. Tải APK

1. Mở lần chạy workflow đã hoàn tất trong tab **Actions**.
2. Kéo xuống phần **Artifacts**.
3. Tải `TaiChinhCaNhan-debug-apk`.
4. Giải nén file tải về; APK nằm trong đó với tên `app-debug.apk`.
5. Chép APK sang POCO F8 Pro và cài đặt để kiểm thử. Nếu Android hỏi, cấp quyền cài ứng dụng từ nguồn bạn dùng để mở tệp.

## Thông tin kỹ thuật

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Gradle: 8.9
- Compile/Target SDK: 35
- Min SDK: 26
- Artifact: APK debug (`app-debug.apk`), phù hợp kiểm thử.

## Nếu workflow báo lỗi

Mở lần chạy bị lỗi → chọn job `Build debug APK` → mở bước `Build APK` → sao chép phần lỗi đầu tiên có chữ `e:` hoặc `FAILURE: Build failed with an exception` và gửi lại để xử lý. Không gửi token, mật khẩu hoặc khóa bí mật GitHub.

## Giới hạn cần biết

- Gói này cấu hình GitHub Actions để biên dịch; chưa có nghĩa APK đã được biên dịch thành công. Chỉ xác nhận được sau khi workflow chạy xanh.
- Đây là APK debug, chưa ký phát hành (release signing) cho Google Play.
- Trước khi dùng dữ liệu thật, hãy kiểm thử nhập Excel, báo cáo, FIFO vàng, sổ tiết kiệm, sao lưu/khôi phục trên điện thoại và đối chiếu với workbook gốc.
