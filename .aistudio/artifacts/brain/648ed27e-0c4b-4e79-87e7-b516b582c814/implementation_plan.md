# Kế Hoạch Triển Khai: Quét & Khôi Phục Tài Khoản VIP

Kế hoạch này chi tiết hóa tính năng **Quét & Khôi Phục Tài Khoản VIP** cho ứng dụng "Ôn luyện Trạng nhí Tiếng Việt". Tính năng này kết hợp dữ liệu Google Play Billing API với thông tin Tài khoản, Email phụ huynh và ID Thiết bị (`ANDROID_ID`) để tự động kiểm tra và kích hoạt lại quyền VIP khi đăng nhập lại hoặc cài lại ứng dụng.

---

## Xác Nhận Từ Người Dùng & Quyết Định Quan Trọng

> [!IMPORTANT]
> - **Giữ trạng thái hiện tại**: Nếu người dùng không đăng xuất, ứng dụng giữ nguyên trạng thái tài khoản hiện tại (Tài khoản Thường hoặc VIP) mà không bắt buộc quét lại mỗi lần mở app.
> - **Điều kiện kích hoạt VIP khi đăng nhập/cài lại**:
>   1. Người dùng nhập Tài khoản (Tên bé) và Email phụ huynh.
>   2. Hệ thống kiểm tra kết nối Google Play Billing API để tìm các giao dịch mua thành công (`PURCHASED`) thuộc về các gói VIP (`vip1`, `vip2`, `vip_package_1`, `vip_package_2`).
>   3. Khi xác thực gói VIP hoạt động từ Google Play Billing, hệ thống tự động đồng bộ trạng thái VIP và lưu dữ liệu xác nhận.
> - **Giao diện nhập VIP**: Thiết kế 1 dòng nằm ngang (Horizontal row) gọn gàng, sáng đẹp, mượt mà, thân thiện với trẻ em.

---

## 1. Tổng Quan & Mục Tiêu

- **Tính Năng**: Quét & Khôi Phục VIP (Account & Purchase Scanning & Restoration).
- **Đối Tượng Sử Dụng**: Phụ huynh và học sinh khi chuyển thiết bị, đăng xuất/đăng nhập lại, hoặc cài đặt lại ứng dụng.
- **Giá Trị Mang Lại**: Đảm bảo quyền lợi mua gói VIP của người dùng không bị mất khi xóa app hay đổi thiết bị, quá trình khôi phục diễn ra nhanh chóng chỉ với 1 thao tác nhấn "Quét & Khôi phục".

---

## 2. Trải Nghiệm Người Dùng & Thiết Kế Giao Diện

- **Luồng Sử Dụng**:
  1. Người dùng mở Modal Tài khoản / Đăng ký VIP từ thanh tiêu đề chính.
  2. Trường thông tin được thiết kế dạng 1 dòng nằm ngang (Horizontal Row):
     - **Tài khoản**: Input text
     - **Email**: Input text
     - **ID thiết bị**: Read-only text (lấy tự động từ `ANDROID_ID`)
  3. Bấm nút **"🔍 Quét & Khôi Phục VIP"**.
  4. Hệ thống kiểm tra Google Play Billing API `queryPurchasesAsync()` đồng bộ:
     - Nếu phát hiện gói VIP đã mua: Thông báo thành công "Tài khoản VIP đã được kích hoạt thành công!", cập nhật UI tức thì thành trạng thái VIP kèm Tên Tài Khoản.
     - Nếu chưa tìm thấy giao dịch: Thông báo "Chưa tìm thấy gói VIP hợp lệ cho tài khoản/thiết bị này."

- **Phong Cách Giao Diện**:
  - Dải màu pastel sáng tươi (`#FFFBEB` nền kem dịu mắt, viền bơ vàng `#FCD34D`, nút xanh lá dưa hấu `#10B981` tươi tắn).
  - Phông chữ bo tròn thân thiện với trẻ em.
  - Phản hồi trực quan bằng Toast/Dialog tiếng Việt rõ ràng, ngắn gọn.

---

## 3. Quyết Định Sản Phẩm & So Sánh Giải Pháp

- **Giải Pháp Chọn**: Kết hợp kiểm tra trực tiếp lịch sử giao dịch từ Google Play Billing client (`queryPurchasesAsync`) với dữ liệu tài khoản cục bộ (`VipPreferences`).
  - *Lý do*: Đáp ứng chính xác cơ chế offline-first, không phụ thuộc vào server trung gian phức tạp, tận dụng cơ chế xác thực an toàn của Google Play Store.
- **Dữ Liệu Khôi Phục**:
  - Lưu trữ trạng thái VIP, tên tài khoản, email phụ huynh, ID thiết bị trong `SharedPreferences` (`VipPreferences.kt`).
  - Cập nhật thời gian thực tới giao diện HTML/JS qua `AndroidBridge`.

---

## 4. Kiến Trúc Kỹ Thuật & Luồng Dữ Liệu

### Sơ Đồ Kiến Trúc Hệ Thống

```
┌─────────────────────────────────────────────────────────┐
│                    Giao Diện Web (JS)                   │
│   • Top Header (Tên tài khoản / Huy hiệu VIP)           │
│   • Modal Tài Khoản (Nút 🔍 Quét & Khôi Phục VIP)       │
└────────────────────────────┬────────────────────────────┘
                             │
                  AndroidBridge (JavascriptInterface)
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│                    MainActivity (Kotlin)                │
│   • scanAndRestoreVip(userName, userEmail)              │
│   • Hiển thị Toast & xác nhận khôi phục                 │
└──────────────┬──────────────────────────┬───────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────────────┐ ┌────────────────────────┐
│      BillingManager.kt       │ │    VipPreferences.kt   │
│ • queryPurchasesAsync()      │ │ • saveVipStatus()      │
│ • Verify active VIP purchase │ │ • getDeviceId()        │
└──────────────┬───────────────┘ └────────────────────────┘
               │
               ▼
┌──────────────────────────────┐
│   Google Play Billing API    │
│ • Danh sách VIP Purchases    │
└──────────────────────────────┘
```

### Các Bước Triển Khai Chi Tiết:

1. **`BillingManager.kt`**:
   - Bổ sung hàm `scanAndRestoreVip(userName: String, userEmail: String, callback: (Boolean, Int, String) -> Unit)`.
   - Gọi `queryPurchasesAsync(QueryPurchasesParams)` để quét danh sách các gói sản phẩm đã mua trên thiết bị/tài khoản Google Play.
   - Khi tìm thấy gói `vip1` (hoặc `vip2`), trả về `true`, `vipLevel` tương ứng, và `purchaseToken`.

2. **`MainActivity.kt`**:
   - Expose phương thức `scanAndRestoreVip(userName: String, userEmail: String)` qua `AndroidBridge`.
   - Nếu kết quả quét thành công: Lưu trạng thái VIP vào `VipPreferences`, gọi `window.onVipStatusChanged(vipLevel, userName)` để cập nhật Web UI.
   - Hiển thị Dialog/Toast thông báo tiếng Việt tích cực.

3. **`index.html` (Web UI)**:
   - Thêm nút **"🔍 Quét & Khôi Phục VIP"** trong giao diện Modal Tài khoản.
   - Định dạng hàng thông tin Tài khoản, Email, ID Thiết bị thành 1 dòng nằm ngang đẹp mắt, màu sáng phù hợp trẻ em.
