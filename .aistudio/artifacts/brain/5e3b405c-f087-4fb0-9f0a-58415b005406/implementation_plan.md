# Kế hoạch tối ưu hóa hiệu năng nạp đề thi & parse JSON

## Tổng quan
Tối ưu hóa tốc độ nạp đề thi, loại bỏ hiện tượng khựng giao diện (UI Freeze) bằng cách:
1. Đưa các biểu thức chính quy (`Regex`) vào `companion object` (biên dịch một lần duy nhất).
2. Thiết lập cơ chế In-Memory RAM Cache (`ConcurrentHashMap`) cho danh sách `QuestionModel` đã parse để tránh parse lại chuỗi JSON lớn nhiều lần trong một phiên làm việc.
3. Giữ nguyên tuyệt đối thứ tự ưu tiên 3 tầng: 
   - Ưu tiên 1: GitHub (Cache / Tải trực tiếp)
   - Ưu tiên 2: File JSON trong Assets
   - Ưu tiên 3: index.html (Test Mode / Dự phòng)

---

## Chi tiết kỹ thuật

### 1. Tối ưu Regex trong `DynamicExamGenerator` và `QuestionModel`
- Khai báo các `Regex` ở `companion object`:
  - `private val DIGIT_REGEX = Regex("\\d+")`
  - `private val GRADE_ROUND_REGEX = Regex("^k(\\d+)_c(\\d+)", RegexOption.IGNORE_CASE)`
  - `private val NORMALIZE_REGEX = Regex("[^a-z0-9àáảãạăắằẳẵặâấầnẩẫậnèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ]")`

### 2. In-Memory RAM Cache trong `DynamicExamGenerator`
- Sử dụng `ConcurrentHashMap<String, List<QuestionModel>>` với key dạng `"$grade_$round"` để cache kết quả parse câu hỏi.
- Khi GitHub sync thành công hoặc khi đổi đề, có thể invalidate cache để đảm bảo nhận đề mới nhất.

### 3. Bảo toàn thứ tự ưu tiên 3 tầng
- `loadQuestionPool` vẫn giữ nguyên logic:
  1. Kiểm tra cache RAM / GitHub Cache File.
  2. Nếu không có, gọi `GitHubQuestionRepository.getLocalAssetQuestionsJson`.
  3. Nếu vẫn không có, dùng `localQuestionsJsonStr` từ `index.html`.
