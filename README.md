# Tài chính cá nhân – Android (prototype 0.4.0)

Ứng dụng Android native Kotlin + Jetpack Compose + SQLite, thiết kế theo workbook `BCTC (1).xlsx`. Dữ liệu lưu cục bộ trên thiết bị; không cần kết nối mạng.

## Chức năng trong 0.4.0
- Nhập workbook `.xlsx` trực tiếp bằng bộ chọn tệp Android, đọc offline các sheet nguồn: `HỆ THỐNG TK`, `MỤC THU CHI`, `SỔ CÁI`, `SỔ TK`, `VÀNG`, `tủ`.
- Bộ đọc XLSX xử lý shared strings, inline strings, quan hệ sheet, số dòng gốc và giá trị cache của công thức Excel. Các sheet báo cáo/công thức `BCTC`, `THU CHI`, `CHI TIẾT TK` được tính lại từ SỔ CÁI trong ứng dụng.
- Nhập Excel thay thế dữ liệu hiện tại theo một transaction SQLite; nếu parse hoặc kiểm tra dữ liệu thất bại thì không ghi đè. Nên sao lưu JSON trước khi nhập.
- SỔ CÁI: thêm, sửa, xóa giao dịch.
- Tài khoản/danh mục: thêm, sửa, xóa; không cho xóa mục đang được giao dịch sử dụng. Khi đổi tên, các giao dịch liên quan được cập nhật để không làm mất liên kết.
- Sổ tiết kiệm: thêm, sửa, xóa hồ sơ sổ; hiển thị gốc, lãi suất, kỳ hạn, ngày đến hạn, lãi ước tính và số tiền dự kiến khi đáo hạn; đánh dấu tất toán; tái tục sổ đã đến hạn bằng cách tạo sổ mới, cộng lãi ước tính vào gốc với loại lãi nhập gốc.
- Vàng: thêm, sửa, xóa giao dịch mua/bán; tính giá vốn FIFO, vàng tồn và lãi/lỗ đã thực hiện. Các cột kết quả/phụ trợ có sẵn trong dữ liệu gốc được giữ lại khi sửa 3 trường giao dịch chính.
- Tiền mặt: thêm, sửa, xóa mệnh giá/khoản tiền; tính lại bảng kê từ dữ liệu đã nhập.
- Dashboard: tài sản, công nợ, tài sản ròng, tổng thu và chi.
- Báo cáo tháng theo năm/tháng và danh mục.
- Sao lưu/khôi phục JSON qua Android Storage Access Framework.

## Cấu trúc nhập workbook
- `SỔ CÁI`: đọc A:H gồm ngày, số tiền, tăng/giảm, cờ báo cáo, tài khoản, danh mục, ghi chú, giá trị `THÀNH TIỀN` đã cache; giữ đủ 1.293 dòng nguồn có nội dung, kể cả 4 dòng có số tiền trống nhưng vẫn có ngày/tài khoản/danh mục.
- `HỆ THỐNG TK` và `MỤC THU CHI`: giữ dữ liệu phân cấp trong mảng values; cột tên và mã dùng cho báo cáo.
- `SỔ TK`: giữ 10 cột nguồn; các ngày lấy từ giá trị cache trong XLSX nếu ô là công thức.
- `VÀNG`: giữ các cột nguồn; khi sửa giao dịch, app thay đổi ngày/khối lượng/đơn giá và giữ nguyên các cột công thức còn lại.
- `tủ`: giữ bảng kê gốc và các khoản tiền khác.

## Đối chiếu workbook đã cung cấp
- BCTC!W2 tài sản có: 1.580.255.786 VND.
- BCTC!W3 tài sản nợ: -54.472.948 VND.
- BCTC!W4 tài sản ròng: 1.525.782.838 VND.
- Thu/chi tháng 01/2026 khớp THU CHI!O3 = 69.407.038 VND và THU CHI!O4 = -22.318.851 VND (app hiển thị chi dương).
- VÀNG: các giao dịch mua/bán có dữ liệu gồm mua 2 lượng @110 triệu, mua 0,2 @138 triệu, mua 0,1 @173 triệu, bán 0,3 @155,5 triệu, mua 0,05 @154 triệu. FIFO cho lần bán 0,3 lượng có giá vốn 33.000.000 VND, lãi 13.650.000 VND; khớp các ô giá vốn/lãi của dòng bán trong workbook. Vàng tồn 2,05 lượng, giá vốn 239.600.000 VND.
- `tủ`: tổng theo mệnh giá 23.700.000 VND; các khoản tiền khác 66.650.000 VND; tổng bảng kê 90.350.000 VND.

## Giới hạn và lưu ý
- Chức năng tính lãi sổ tiết kiệm hiện là ước tính lãi đơn `gốc × lãi suất năm × kỳ hạn/12`. Tái tục là thao tác thủ công sau ngày đến hạn và tạo một bản ghi sổ mới; chưa mô phỏng chính xác quy ước ngày thực tế, lãi nhập gốc nhiều kỳ, lãi suất bậc thang, tất toán trước hạn hoặc phí/thuế. Tất toán trong app chỉ đổi trạng thái, không tự ghi nhận tiền lãi thực nhận vào SỔ CÁI.
- Bộ đọc XLSX hiện tập trung vào đúng 6 sheet nguồn và cấu trúc workbook đang dùng. Không cam kết tương thích mọi biến thể Excel, file có mật khẩu, macro, external links hoặc bố cục cột đã thay đổi.
- Các công thức báo cáo được tính từ `THÀNH TIỀN` cache trong SỔ CÁI; nếu người dùng sửa công thức trong Excel mà không lưu giá trị tính toán, cần mở và lưu workbook bằng Excel/LibreOffice trước khi nhập.
- Khi nhập workbook, dữ liệu hiện có trong app sẽ bị thay thế; luôn tạo JSON backup trước.
- Chưa có kiểm thử tự động trên Android/POCO và chưa có APK đã biên dịch/xác minh. Phải đối chiếu nhiều kỳ, giao dịch chuyển khoản, cho vay/thu nợ, số dư đầu kỳ và các trường hợp ngoại lệ trước khi dùng như hệ thống sổ sách chính thức.

## Build APK
1. Cài Android Studio, JDK 17, Android SDK Platform 35.
2. Mở thư mục dự án chứa `settings.gradle.kts`.
3. Chờ Gradle Sync hoàn tất.
4. Chọn Build > Build Bundle(s) / APK(s) > Build APK(s).
5. APK debug thường ở `app/build/outputs/apk/debug/app-debug.apk`.

Môi trường làm việc hiện tại không có Android SDK/Gradle nên chưa thể biên dịch hoặc chạy kiểm thử thiết bị. Gói bàn giao là mã nguồn, chưa phải APK.

## Build APK trên GitHub (không cần máy ảo)
Dự án đã kèm workflow `.github/workflows/build-apk.yml`. Xem hướng dẫn từng bước trong `BUILD_APK_ON_GITHUB.md`. Workflow tạo artifact `TaiChinhCaNhan-debug-apk` chứa `app-debug.apk` sau khi build thành công.
