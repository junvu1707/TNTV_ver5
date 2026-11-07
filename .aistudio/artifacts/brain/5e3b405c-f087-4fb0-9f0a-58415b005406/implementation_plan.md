# Phương án Chuẩn hóa Tuyệt đối Logic Lấy Đề (3 Cấp Ưu tiên) & Sửa Giao diện Phân loại Từ ngữ (Ảnh 2)

## 1. Phân tích & Cam kết tuân thủ tuyệt đối Luồng Ưu tiên Lấy Đề

Theo đúng yêu cầu của bạn, luồng nạp đề thi sẽ tuân thủ tuyệt đối theo thứ tự 3 cấp độ:

```
                  ┌──────────────────────────────────────────────┐
                  │ HỌC SINH MỞ ỨNG DỤNG / BẮT ĐẦU VÀO BÀI THI   │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌────────────────────────────────────────────────┐
                 │ [ƯU TIÊN 1] ĐỀ TRÊN GITHUB (LẤY MỖI KHI MỞ APP)│
                 │ - Kiểm tra kết nối mạng & Tải trực tiếp GitHub │
                 │ - Đồng bộ nền tự động ngay khi bật app         │
                 │ - Lưu cache file từ GitHub để tái sử dụng      │
                 └───────────────────────┬────────────────────────┘
                                         │
                     Thành công? ────────┼──────── Không có mạng / Lỗi tải?
                                         │                         │
                                         ▼                         ▼
                              ┌─────────────────────┐   ┌────────────────────────────────┐
                              │ NẠP ĐỀ TỪ GITHUB    │   │ [ƯU TIÊN 2] FILE JSON TRONG    │
                              │ (1.784 câu Lớp 1 R1,│   │ ASSETS CỦA ỨNG DỤNG            │
                              │ có 357 câu Điền từ) │   │ (Dùng khi offline không mạng)  │
                              └─────────────────────┘   └───────────────┬────────────────┘
                                                                        │
                                            Thành công? ────────────────┼──────── Lỗi đọc asset?
                                                                        │                   │
                                                                        ▼                   ▼
                                                             ┌─────────────────────┐  ┌──────────────────┐
                                                             │ NẠP ĐỀ TỪ ASSETS    │  │ [ƯU TIÊN 3]      │
                                                             │ (Đã đồng bộ đủ 357  │  │ DỮ LIỆU ĐỀ TRONG │
                                                             │ câu DT cho Lớp 1 R1)│  │ index.html        │
                                                             └─────────────────────┘  └──────────────────┘
```

---

## 2. Chi tiết Phương án Triển khai Từng Cấp Ưu tiên

### [Ưu tiên 1]: Đề trên GitHub - Lấy luôn mỗi khi bật app
1. **Khởi động ứng dụng (`MainActivity.onCreate`)**:
   - Gọi ngay hàm `githubRepo.syncAllGradesInBackground()` để tải song song và cập nhật cache toàn bộ các khối lớp (Lớp 1 đến Lớp 5, Round 1 đến Round 4) từ GitHub repository:
     `https://raw.githubusercontent.com/junvu1707/Trangsinhi_de/refs/heads/main/grade%20${grade}_round%20${round}.json`
2. **Khi học sinh bấm vào bất kỳ Chặng thi nào**:
   - Hệ thống chủ động kiểm tra cache GitHub đã tải về hợp lệ từ GitHub (`github_g{grade}_r{round}.json`).
   - Nếu máy có mạng, tiếp tục gửi request ngầm cập nhật phiên bản mới nhất từ GitHub.
   - Khi có dữ liệu GitHub: sử dụng 100% đề từ nguồn này (Đảm bảo có đủ 1.784 câu Lớp 1 Vòng 1 với 357 câu Điền từ).

### [Ưu tiên 2]: Nếu không có mạng, không load được GitHub thì lấy từ file JSON trong assets
1. **Cơ chế kích hoạt**:
   - Khi thiết bị không có internet, chế độ máy bay, hoặc GitHub không phản hồi / timeout, hệ thống tự động chuyển sang đọc file asset cục bộ: `grade ${grade}_round ${round}.json` trong thư mục `assets/`.
2. **Cập nhật dữ liệu Asset nội bộ**:
   - File `app/src/main/assets/grade 1_round 1.json` nội bộ hiện tại sẽ được cập nhật đồng bộ toàn bộ **357 câu Điền từ (DT)** từ GitHub sang, nâng tổng số câu trong asset lên 1.784 câu.
   - Nhờ đó, ngay cả khi học sinh ngắt mạng 100% và chạy ở Ưu tiên 2, đề thi Lớp 1 Chặng 1-6 vẫn có đầy đủ 7 câu Điền từ theo đúng chuẩn tỉ lệ (17 TN, 7 DT, 2 NOI, 2 CHON, 2 SX).

### [Ưu tiên 3]: Nếu 2 phương án trên không được mới lấy đề ở `index.html`
1. **Cơ chế kích hoạt**:
   - Chỉ khi cả GitHub (Ưu tiên 1) và file asset JSON (Ưu tiên 2) đều không đọc được (ví dụ lỗi đọc file hệ thống hoặc file bị hỏng), hệ thống mới kích hoạt tầng dự phòng cuối cùng: bộ sinh đề test mode trong `index.html`.
   - Đảm bảo ứng dụng không bao giờ bị đứng hình hay màn hình trắng trong bất kỳ tình huống bất khả kháng nào.

---

## 3. Sửa Giao diện Dạng câu hỏi Phân loại Từ ngữ vào Giỏ (`CHON` / `CLASSIFY_MINI`) theo Ảnh 2

Song song với việc chuẩn hóa luồng ưu tiên dữ liệu, hoàn thiện giao diện phân loại từ ngữ theo đúng Ảnh 2 đã duyệt:

1. **Hiển thị các Chiếc Giỏ (Baskets)**:
   - Viền nét đứt màu vàng hổ phách (`border: 2px dashed #f59e0b`), nền vàng nhạt `amber-50`.
   - Tiêu đề nhóm: Biểu tượng `🧺` kèm tên nhóm từ (ví dụ: `Đồ dùng học tập`, `Con vật quen thuộc`, `Cây cối & Trái cây`).
   - Badge số lượng từ trong giỏ ở góc phải: `[0 từ]`, cập nhật theo thời gian thực `[1 từ]`, `[2 từ]`...
   - Vùng chứa các từ đã xếp: hiển thị dạng thẻ từ bo tròn, có nút `✕` để khi bấm vào sẽ hoàn từ trở lại khay bên dưới.

2. **Khung từ cần xếp vào giỏ ở phía dưới**:
   - Tiêu đề: `Từ cần xếp vào giỏ (X từ còn lại):`
   - Các nút từ ngữ dạng bo tròn (pill button), hiệu ứng nổi bật khi được chọn.
   - Chạm 1 từ ở khay dưới -> chạm vào 1 chiếc giỏ -> từ bay vào giỏ tương ứng.
   - Chạm vào từ trong giỏ -> từ quay trở về khay bên dưới.
   - Âm thanh tương tác vui tai và chấm điểm chính xác 100%.

---

## 4. Kế hoạch Kiểm thử & Xác nhận từng bước

1. **Test Ưu tiên 1 (Online/GitHub)**:
   - Kiểm tra log tải đề thành công từ GitHub raw URL khi bật app và khi vào phòng thi.
   - Xác nhận có đủ 357 câu Điền từ và các câu Phân loại/Chọn từ GitHub.
2. **Test Ưu tiên 2 (Offline/Assets)**:
   - Ngắt kết nối mạng giả lập hoặc chặn URL GitHub -> Xác nhận ứng dụng nạp mượt mà từ file JSON trong assets, sinh đề 30 câu đúng cơ cấu 17 TN / 7 DT / 2 NOI / 2 CHON / 2 SX.
3. **Test Ưu tiên 3 (Dự phòng index.html)**:
   - Xác nhận cơ chế an toàn hoạt động khi cả 2 tầng trên không có dữ liệu.
4. **Test Giao diện Phân loại Giỏ (Ảnh 2)**:
   - Kiểm tra giao diện hiển thị đúng giỏ nét đứt, đếm số từ, chọn từ và thả vào giỏ, gỡ từ khỏi giỏ.
5. **Chạy Unit Test & Biên dịch**:
   - `gradle :app:testDebugUnitTest` và `compile_applet` xác nhận không có bất kỳ lỗi nào.
