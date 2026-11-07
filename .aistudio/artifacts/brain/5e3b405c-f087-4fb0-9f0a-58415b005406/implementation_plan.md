# Kế hoạch cải tiến Dạng câu hỏi Sắp xếp câu (SX) trong Đề thi 30 câu

Chuyển đổi dạng câu hỏi Sắp xếp (SX) từ hình thức nhập liệu văn bản bàn phím thủ công sang giao diện tương tác chạm ô từ (Interactive Tile Touch) trực quan, chuẩn format thi Trạng Nguyên Tiếng Việt Tiểu học.

---

## Bối cảnh & Vấn đề hiện tại
- **Vấn đề:** Hiện nay câu hỏi loại `SX` đang bị ánh xạ vào dạng `FILL` (nhập văn bản bằng bàn phím). Nội dung câu hỏi hiển thị chuỗi text gộp cồng kềnh: *"Hãy sắp xếp các từ/cụm từ... Các từ cần sắp xếp: [ ngát. / tỏa hương / thơm / Hoa sen ]"*, học sinh tiểu học phải tự gõ từng từ trên bàn phím điện thoại rất dễ sai chính tả và bất tiện.
- **Mục tiêu:** 
  1. Hiển thị nội dung câu hỏi ngắn gọn: **"Sắp xếp thành câu"** (hoặc tiêu đề chuẩn mực).
  2. Các phần tử từ ngữ được thiết kế thành **các ô từ (Word Tiles)** sinh động, nổi bật.
  3. Cơ chế chạm tương tác: Chạm vào ô từ ở ngân hàng từ sẽ di chuyển lên khung câu ghép; chạm vào từ trên khung câu ghép sẽ trả về vị trí cũ; có nút đặt lại (làm lại) tiện lợi.
  4. Đảm bảo giữ nguyên 100% logic đang chạy ổn định của các dạng câu hỏi khác (`MCQ`, `FILL`, `MATCH_MINI`, `CLASSIFY_MINI`).

---

## 1. Thiết kế Giao diện & Trải nghiệm Người dùng (UX/UI)

### 1.1. Cấu trúc hiển thị câu hỏi Sắp xếp
```
┌────────────────────────────────────────────────────────┐
│ [Câu 5: Sắp xếp câu (10 điểm)]                         │
├────────────────────────────────────────────────────────┤
│  Nội dung: Sắp xếp thành câu                           │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ [VÙNG CÂU ĐANG GHÉP] (Khung nền sáng viền bo tròn) │ │
│ │  [ Hoa sen ] [ tỏa hương ] [ thơm ] [ ngát. ]      │ │
│ │  (Chạm vào từ để gỡ bỏ)     [↺ Làm lại]           │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ [NGÂN HÀNG Ô TỪ CẦN SẮP XẾP]                       │ │
│ │  [ ngát. ]  [ tỏa hương ]  [ thơm ]  [ Hoa sen ]   │ │
│ │  (Chạm vào ô từ để đưa vào câu)                    │ │
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

- **Thẻ định danh câu hỏi:** `Câu X: Sắp xếp câu (10 điểm)`.
- **Nội dung câu hỏi:** `"Sắp xếp thành câu"`.
- **Vùng 1 (Khung câu ghép kết quả):**
  - Hiển thị nền vàng kem nhạt bo góc lớn, có placeholder: *"Chạm vào các ô từ bên dưới để ghép thành câu..."* khi chưa chọn từ nào.
  - Khi học sinh bấm vào các từ bên dưới, các thẻ từ xuất hiện liên tiếp theo thứ tự bấm.
  - Chạm vào bất kỳ từ nào trong câu đã ghép sẽ lập tức trả từ đó về lại ngân hàng từ bên dưới.
  - Nút **"Làm lại" / "↺ Đặt lại"** nhỏ gọn ở góc phải để xóa nhanh nếu muốn sắp xếp lại từ đầu.
- **Vùng 2 (Ngân hàng ô từ cần sắp xếp):**
  - Hiển thị các ô từ dạng thẻ 3D thiếu nhi (`btn-3d`, bo góc `rounded-xl`, đổ bóng nổi bật, màu sắc tươi sáng, tương phản cao, dễ chạm với touch target >= 48dp).
  - Từ nào đã được đưa lên khung câu ghép sẽ tự động ẩn đi hoặc chuyển sang trạng thái đã dùng, giúp học sinh nhận biết rõ các từ còn lại.

---

## 2. Kế hoạch triển khai kỹ thuật từng bước

### Bước 1: Chuẩn hóa Model dữ liệu câu hỏi trong JavaScript (`index.html`)
- Trong hàm `normalizeExamQuestion(q, idx)`:
  - Khi `type === 'SX'` hoặc `q.elements` tồn tại:
    - Thiết lập `finalType = 'ARRANGE'` (dạng riêng biệt, không còn dùng chung `FILL`).
    - `title`: `"Sắp xếp câu"`.
    - `question`: `"Sắp xếp thành câu"`.
    - `elements`: Danh sách các từ xáo trộn ban đầu (lấy từ `q.elements`).
    - `answer`: Chuỗi câu hoàn chỉnh chuẩn (`q.answer`).
    - `explain`: Lời giải thích ý nghĩa ngữ pháp câu.

### Bước 2: Xây dựng Component hiển thị & Tương tác Chạm (`renderExamMode2Question`)
- Thêm trường hợp `case 'ARRANGE':` trong `switch (curQ.type)`:
  - Khởi tạo và đồng bộ trạng thái sắp xếp theo chỉ số câu hỏi hiện tại (`curIdx`).
  - Hỗ trợ lưu trữ trạng thái người dùng đã chọn: mảng thứ tự từ đã ghép `[wordIndex1, wordIndex2, ...]`.
  - Hàm `pickArrangeWord(wordIndex)`: Chuyển ô từ từ ngân hàng từ lên khung câu ghép, tự động nối chuỗi và cập nhật vào `AppState.userAnswers30[curIdx]`.
  - Hàm `removeArrangeWord(slotIndex)`: Gỡ từ khỏi khung câu ghép trả về ngân hàng từ.
  - Hàm `resetArrangeWords()`: Trả toàn bộ từ về ban đầu và xóa câu trả lời hiện tại.

### Bước 3: Đồng bộ Logic chấm điểm & Hiển thị kết quả (`finishExamMode2`)
- Trong vòng lặp chấm điểm 30 câu:
  - Thêm xử lý `q.type === 'ARRANGE'`:
    - So sánh chuỗi học sinh ghép được với đáp án chuẩn `q.answer`.
    - Thuật toán so khớp thông minh: Loại bỏ khoảng trắng thừa, chuẩn hóa dấu câu (ví dụ `"Hoa sen tỏa hương thơm ngát ."` khớp với `"Hoa sen tỏa hương thơm ngát."`).
  - Hiển thị chi tiết trong bảng lịch sử câu hỏi: Câu ghép của học sinh, Đáp án đúng chuẩn, Trạng thái Đúng/Sai kèm Lời giải thích.

### Bước 4: Kiểm thử và xác minh từng bước (Verification & Test Cases)
1. **Kiểm thử hiển thị:**
   - Mở Đề thi 30 câu có câu hỏi dạng Sắp xếp.
   - Xác nhận nội dung câu hỏi hiển thị đúng `"Sắp xếp thành câu"` (không còn đoạn text thô dài dòng).
   - Xác nhận các ô từ hiển thị đẹp mắt, rõ ràng.
2. **Kiểm thử tương tác:**
   - Chạm vào từng ô từ -> Từ xuất hiện tuần tự trên khung câu ghép.
   - Chạm vào từ trong khung câu ghép -> Từ quay trở lại danh sách.
   - Bấm nút "Làm lại" -> Reset về trạng thái ban đầu.
3. **Kiểm thử chấm điểm:**
   - Ghép đúng câu -> Được 10 điểm, hiển thị màu xanh khi nộp bài.
   - Ghép sai trật tự từ -> Không tính điểm, hiển thị đáp án đúng để học sinh học tập.
4. **Kiểm tra độ an toàn (Regression Test):**
   - Đảm bảo các dạng câu hỏi khác (`MCQ`, `FILL`, `MATCH_MINI`, `CLASSIFY_MINI`) hoạt động hoàn toàn bình thường, không bị ảnh hưởng.
