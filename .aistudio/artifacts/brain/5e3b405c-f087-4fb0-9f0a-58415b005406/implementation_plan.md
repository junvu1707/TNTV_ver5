# Kế hoạch Đồng bộ Dữ liệu GitHub & Sửa giao diện Phân loại Từ ngữ vào Giỏ (Ảnh 2)

## Giải đáp thắc mắc về nguồn dữ liệu & Bảng thống kê

> [!IMPORTANT]
> **Xác nhận chính xác 100%**: File `grade 1_round 1.json` trên kho GitHub thực tế có tới **1.784 câu hỏi**, trong đó có đầy đủ **357 câu Điền từ (DT)** (cùng 1.011 câu TN, 178 câu SX, 119 câu NOI, 119 câu CHON).
>
> **Nguồn gốc bảng thống kê trước đó**: Bảng thống kê trong lượt trước được quét từ thư mục **Asset nội bộ offline** đóng gói sẵn trong bộ code (`app/src/main/assets/*.json`), chứ không phải dữ liệu trực tiếp trên GitHub. Do bản asset nội bộ cũ của Lớp 1 Vòng 1 chỉ chứa 783 câu và bị thiếu câu DT, dẫn đến kết quả quét nội bộ bị báo thiếu.

---

## 1. Mục tiêu & Phạm vi công việc

1. **Khắc phục nguồn dữ liệu & Đồng bộ GitHub**:
   - Cập nhật 357 câu Điền từ (DT) từ GitHub trực tiếp vào file asset nội bộ `grade 1_round 1.json`.
   - Bổ sung bộ xử lý an toàn (JSON sanitizer) để tự động sửa các lỗi cú pháp JSON nhỏ trên một số file GitHub (như thiếu dấu phẩy ở Lớp 2 Vòng 3-4, Lớp 4, Lớp 5), đảm bảo ứng dụng luôn tải mượt mà trực tiếp từ GitHub khi có mạng và dùng asset chuẩn khi offline.

2. **Sửa giao diện Phân loại từ vào Giỏ (`CHON` / `CLASSIFY_MINI`) chuẩn theo Ảnh 2**:
   - Hiển thị đầy đủ hướng dẫn động: `💡 Bấm chọn 1 từ bên dưới rồi bấm vào Chiếc Giỏ tương ứng để xếp (X từ chia vào Y giỏ):`
   - Hiển thị từng **Chiếc Giỏ**:
     - Khung card viền nét đứt màu vàng hổ phách (`border: 2px dashed #f59e0b`), nền vàng nhạt `amber-50`.
     - Tiêu đề giỏ: Biểu tượng `🧺` kèm tên nhóm từ (ví dụ: `Đồ dùng học tập`, `Con vật quen thuộc`, `Cây cối & Trái cây`).
     - Badge số lượng từ trong giỏ ở góc phải: `[0 từ]`, cập nhật theo thời gian thực `[1 từ]`, `[2 từ]`...
     - Vùng chứa các từ đã xếp: hiển thị dạng thẻ từ bo tròn, có nút `✕` để khi bấm vào sẽ hoàn từ trở lại khay bên dưới.
   - Hiển thị **Khung từ cần xếp vào giỏ** ở phía dưới:
     - Tiêu đề: `Từ cần xếp vào giỏ (X từ còn lại):`
     - Các nút từ ngữ dạng bo tròn (pill button), hiệu ứng nổi bật khi được chọn.
     - Chạm 1 từ ở khay dưới -> chạm vào 1 chiếc giỏ -> từ bay vào giỏ tương ứng.
     - Chạm vào từ trong giỏ -> từ quay trở về khay bên dưới.
   - Đảm bảo chấm điểm chính xác và hiển thị lịch sử xem lại kết quả sau khi nộp bài.

---

## 2. Thiết kế Giao diện Người dùng (UI/UX) theo Ảnh 2

```
┌────────────────────────────────────────────────────────────────────────┐
│ 💡 Bấm chọn 1 từ bên dưới rồi bấm vào Chiếc Giỏ tương ứng để xếp       │
│    (12 từ chia vào 3 giỏ):                                             │
├────────────────────────────────────────────────────────────────────────┤
│ ┌ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -┐ │
│ │ 🧺 Đồ dùng học tập                                        [ 0 từ ] │ │
│ │────────────────────────────────────────────────────────────────────│ │
│ │ (Vùng chứa các từ học sinh đã thả vào giỏ này)                     │ │
│ └ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -┘ │
│                                                                        │
│ ┌ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -┐ │
│ │ 🧺 Con vật quen thuộc                                     [ 0 từ ] │ │
│ │────────────────────────────────────────────────────────────────────│ │
│ │ (Vùng chứa các từ học sinh đã thả vào giỏ này)                     │ │
│ └ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -┘ │
│                                                                        │
│ ┌ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -┐ │
│ │ 🧺 Cây cối & Trái cây                                     [ 0 từ ] │ │
│ │────────────────────────────────────────────────────────────────────│ │
│ │ (Vùng chứa các từ học sinh đã thả vào giỏ này)                     │ │
│ └ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -┘ │
├────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Từ cần xếp vào giỏ (12 từ còn lại):                                │ │
│ │                                                                    │ │
│ │  [ cục tẩy ]   [ bút chì ]   [ gà trống ]   [ chó vàng ]           │ │
│ │  [ thước kẻ ]  [ hoa sen ]   [ cây bàng ]   [ cây chuối ]          │ │
│ │  [ vịt bầu ]   [ quyển vở ]  [ quả táo ]    [ mèo con ]            │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

- **Màu sắc & Phông chữ**:
  - Viền giỏ: `border-2 border-dashed border-amber-400 bg-amber-50/60 rounded-2xl`
  - Tiêu đề nhóm: `font-black text-amber-950 text-sm sm:text-base`
  - Badge số lượng: `bg-emerald-100 text-emerald-800 font-bold text-xs px-2.5 py-0.5 rounded-full`
  - Nút từ khay dưới: `bg-white border-2 border-amber-300 text-slate-800 font-bold rounded-2xl px-4 py-2 hover:bg-amber-50 shadow-sm active:scale-95`
  - Nút từ đang chọn: `bg-amber-400 border-2 border-amber-600 text-amber-950 font-black ring-4 ring-amber-200 scale-105 shadow-md`

---

## 3. Các bước thực hiện chi tiết

### Bước 1: Chuẩn hóa dữ liệu câu hỏi trong `DynamicExamGenerator.kt`
- Đảm bảo khi tạo câu hỏi `CHON`, cả `groups`, `words`, và `answer` (dạng `JSONObject` map nhóm sang danh sách từ) đều được bảo toàn nguyên vẹn 100% trong JSON xuất ra, không bị ghi đè chuỗi rỗng.
- Cập nhật file asset `app/src/main/assets/grade 1_round 1.json` đồng bộ đầy đủ 357 câu Điền từ từ GitHub.

### Bước 2: Cập nhật hàm chuẩn hóa câu hỏi `normalizeExamQuestion` trong `index.html`
- Trích xuất linh hoạt cấu trúc câu hỏi `CHON` / `CLASSIFY_MINI`:
  - Lấy danh sách nhóm `groups` dù được định nghĩa dạng Dictionary `{ "G1": "...", "G2": "..." }` hay Array `[{ name: "...", words: [...] }]`.
  - Lấy danh sách từ khóa đầy đủ từ `q.words` hoặc từ `q.answer[gKey]`.
  - Tạo cấu trúc đối tượng chuẩn: `groups: [{ id: "G1", name: "...", words: [...] }]`, `words: ["..."]`.

### Bước 3: Thiết kế lại giao diện tương tác `renderExamMode2Question` (`case 'CLASSIFY_MINI'`)
- Thay thế hoàn toàn giao diện cũ bằng cấu trúc chuẩn theo Ảnh 2:
  - Header thông báo: `💡 Bấm chọn 1 từ bên dưới rồi bấm vào Chiếc Giỏ tương ứng để xếp (X từ chia vào Y giỏ):`
  - Danh sách các Giỏ viền nét đứt amber với badge đếm số từ.
  - Khay chứa từ bên dưới với số lượng từ còn lại được cập nhật liên tục.
  - Xử lý các sự kiện `pickMiniWord`, `dropToMiniGroup`, `removeWordFromMiniGroup` mượt mà, phản hồi âm thanh gõ trống / pop vui tai.

### Bước 4: Chấm điểm & Xem lại kết quả
- Cập nhật logic chấm điểm trong `finishExamMode2` so khớp từng từ của học sinh với đáp án chuẩn của từng giỏ.
- Hiển thị trực quan trong bảng kết quả chi tiết để học sinh và phụ huynh xem lại đáp án đúng.

### Bước 5: Kiểm thử tự động & Biên dịch
- Chạy unit test Robolectric (`gradle :app:testDebugUnitTest`) kiểm tra bảo toàn dữ liệu và sinh đề cho câu hỏi `CHON`.
- Chạy `compile_applet` để xác nhận ứng dụng Android biên dịch thành công 100%.
