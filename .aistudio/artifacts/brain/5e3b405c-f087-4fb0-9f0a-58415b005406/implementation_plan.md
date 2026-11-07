# Kế hoạch Loại bỏ Ghi chú Thừa & Tối ưu Hiển thị Tên Giỏ Phân loại

## 1. Mục tiêu & Yêu cầu chỉnh sửa

Theo đúng hình chụp khoanh đỏ từ người dùng:

1. **Loại bỏ hoàn toàn các dòng ghi chú / hướng dẫn thừa trong các dạng câu hỏi**:
   - **Dạng Điền từ & Trắc nghiệm (Screenshot 1)**:
     - Loại bỏ khung ghi chú `💡 Chọn phụ âm...` / `💡 Điền quan hệ từ...` thừa bên trong hộp câu hỏi.
     - Chỉ hiển thị trực tiếp nội dung câu hỏi/câu văn ngắn gọn, rõ ràng (ví dụ: `"(...) tet dai"` hoặc `"Lời nói chẳng mất tiền mua, lựa lời mà nói cho vừa lòng (...)"`).
   - **Dạng Sắp xếp câu (Screenshot 3)**:
     - Loại bỏ dòng ghi chú phụ `"Chạm vào các ô từ bên dưới để đưa vào câu. Chạm vào từ trong câu để gỡ bỏ."`.
     - Chỉ giữ lại tiêu đề ngắn gọn: **Sắp xếp thành câu**.
   - **Dạng Phân loại vào Giỏ (Screenshot 2)**:
     - Loại bỏ dòng ghi chú mờ `"Chạm giỏ để xếp từ vào đây..."` khi giỏ còn trống.

2. **Hiển thị đầy đủ tên các Giỏ phân loại (không bị cắt chữ `...`)**:
   - Loại bỏ class `truncate` gây cắt ngắn tên giỏ (như `Từ chỉ hoạ...`).
   - Cho phép tên giỏ tự động xuống dòng (`whitespace-normal break-words`), đảm bảo hiển thị 100% đầy đủ tên các nhóm từ (ví dụ: **Từ chỉ hoạt động**, **Từ chỉ sự vật**, **Từ chỉ đặc điểm**...).

---

## 2. Các thay đổi kỹ thuật chi tiết (`app/src/main/assets/index.html`)

### A. Tối ưu dạng Điền từ (`case 'FILL'`) & Trắc nghiệm (`case 'MCQ'`)
- Loại bỏ hoàn toàn khối `div` chứa thẻ `💡 [Phần ghi chú/hướng dẫn]`.
- Làm sạch chuỗi câu hỏi: Tự động cắt bỏ các tiền tố dài dòng kiểu *"Chọn phụ âm đầu đúng cho tiếng trong ngoặc:"* hay *"Điền quan hệ từ vào chỗ trống phù hợp:"*, chỉ hiển thị trực tiếp câu văn chứa chỗ trống.

### B. Tối ưu dạng Sắp xếp câu (`case 'ARRANGE'`)
- Loại bỏ thẻ `<p class="text-xs text-slate-500 mt-1 font-medium">Chạm vào các ô từ bên dưới...</p>`.
- Chỉ giữ tiêu đề chính: `<p class="text-base sm:text-lg font-black text-slate-800">Sắp xếp thành câu</p>`.

### C. Tối ưu dạng Phân loại vào Giỏ (`case 'CLASSIFY_MINI'`)
- Xóa bỏ dòng placeholder `<span class="text-[11px] text-amber-800/40 italic py-0.5 select-none">Chạm giỏ để xếp từ vào đây...</span>`.
- Bỏ class `truncate` ở thẻ tên giỏ `<span>${cleanName}</span>`, đổi sang `whitespace-normal break-words leading-tight` để nếu tên giỏ dài sẽ tự động xuống dòng đẹp mắt, đầy đủ nét chữ.

---

## 3. Các bước triển khai & Kiểm thử

1. **Cập nhật code HTML/JS (`index.html`)**: Thực hiện sửa các `case` tương ứng trong `renderExamMode2Question()`.
2. **Kiểm thử tự động**: Chạy `gradle :app:testDebugUnitTest` xác nhận tất cả các Unit Test giao diện và dữ liệu tiếp tục vượt qua.
3. **Biên dịch ứng dụng**: Chạy `compile_applet` xác nhận app xây dựng thành công.
