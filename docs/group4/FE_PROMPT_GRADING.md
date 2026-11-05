# Prompt giao AI Frontend

Bạn đang làm frontend React + Vite cho AiViva. Hãy triển khai phần giảng viên tại `/lecturer/evaluations` và trang chi tiết rà soát/chốt điểm dựa trên backend contract dưới đây. Trước khi sửa, khảo sát code frontend hiện có, giữ layout/component/router/i18n conventions. Không sửa backend. Không dùng dữ liệu giả hoặc mock API; khi backend chưa sẵn sàng, hiển thị trạng thái chưa kết nối và lý do thật. Dùng UUID string cho mọi ID (backend không dùng numeric ID).

## Quy tắc nghiệp vụ bắt buộc

- AI chỉ đưa gợi ý theo rubric; điểm chính thức chỉ xuất hiện sau khi giảng viên bấm Chốt điểm và xác nhận.
- Hiển thị tách biệt “Gợi ý AI” và “Điểm giảng viên”. Đừng gộp hai con số vào một trường/nhãn mơ hồ.
- Chỉ giảng viên được phân công môn/kỳ thi hoặc admin mới dùng được các API. Backend vẫn quyết định quyền; FE chỉ xử lý lỗi 403.
- Điểm criterion AI là gợi ý. Giảng viên có thể nhận gợi ý, sửa (lý do bắt buộc nếu vượt ngưỡng backend), hoặc chấm tay.
- Không hiển thị AI feedback cho sinh viên. Chỉ nhận xét có trạng thái `APPROVED` là nội dung dự kiến hiển thị cho sinh viên.
- Các tín hiệu STT/fluency chỉ là tín hiệu cần lưu ý; tuyệt đối không biến chúng thành điểm trừ.
- Không giả định có endpoint lấy audio có ký. API detail hiện không trả audio URL; không tự dựng URL từ DB.

## Kết nối và response envelope

Base API: `/api/v1/grading`. Tất cả endpoint cần JWT Bearer thông qua API client hiện hành. Backend trả:

```ts
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  timestamp: string;
  errorCode: string | null;
}
```

## API thực tế

- `GET /capabilities` trả `data.available`, `data.ai.ready`, `data.ai.provider`, `data.ai.model`, `data.ai.reason`, `data.features`. FE phải dùng `ai.ready`, không tự suy ra từ server sống. `provider=disabled` là mặc định.
- `GET /queue?examId=<uuid>&status=<status>&attemptNo=2&finishedFrom=<ISO date-time>&finishedTo=<ISO date-time>&page=0&size=20` trả `data.items`, `page`, `size`, `total`. Size tối đa 100. `attemptNo` là bộ lọc tùy chọn được thêm bởi tính năng lượt thi. Trạng thái thêm `VOIDED` để loại bài khỏi hàng chấm đang hoạt động nhưng giữ lịch sử.
- `GET /queue/summary?examId=<uuid>` trả `data.counts` theo trạng thái và `data.total`.
- `GET /submissions/{id}` trả chi tiết bài, `version`, rubric snapshot, `turns` (câu hỏi/transcript/raw-normalized/STT flags/signals), `aiRuns`, `aiSuggestions`, `aiFeedback`, `aiEvidence`, `lecturerScores`, `lecturerNotes`. Audio URL không có.
- `POST /submissions/{id}/claim` claim trong 10 phút; gọi `PUT /submissions/{id}/lock` để heartbeat định kỳ (ví dụ 2 phút), `DELETE /submissions/{id}/lock` khi rời màn hình.
- `PUT /submissions/{id}/scores` lưu một/một số ô và bắt buộc gửi version mới nhất.
- `POST /submissions/{id}/accept-ai` nhận các gợi ý AI mới nhất cho tất cả tiêu chí; body `{ "version": <version hiện tại> }`.
- `PUT /submissions/{id}/questions/{questionId}/notes` lưu nhận xét theo câu hỏi.
- `POST /submissions/{id}/ai-runs` xếp chạy lại, HTTP 202; poll `GET /submissions/{id}/ai-runs`.
- `POST /submissions/{id}/finalize`, `POST /submissions/{id}/release` (body có `version`), `POST /submissions/{id}/reopen` (body có `reason`, `note`, `version`).
- `GET /submissions/{id}/audit`, `GET /submissions/{id}/revisions`.
- `GET /exams/{examId}/analytics/agreement` trả comparison count, MAE, exact agreement, within-one agreement.
- `GET/PUT /exams/{examId}/settings`. Bulk accept và đưa fairness signals vào chấm bị backend từ chối.

Ví dụ `PUT /submissions/{id}/scores`:

```json
{
  "scores": [
    {
      "questionId": "7c2fd92b-e276-4c01-9e88-b6b76f26df83",
      "criterionId": "33d14564-cc4c-40a7-9c1b-73738711d0e0",
      "score": 1.5,
      "maxScore": 2.5,
      "source": "OVERRIDDEN",
      "overrideReason": "Câu trả lời có ví dụ đúng mà gợi ý chưa tính tới.",
      "comment": ""
    }
  ],
  "version": 4
}
```

`source` nhận `MANUAL` hoặc `OVERRIDDEN` tại route này. Nhận AI bằng route riêng. `maxScore` phải đúng mức tối đa tính từ snapshot backend.

Ví dụ finalize:

```json
{ "confirm": true, "version": 5, "note": "Đã rà soát đủ tiêu chí và bằng chứng." }
```

Ví dụ reopen:

```json
{ "reason": "Phúc khảo", "note": "Kiểm tra lại minh chứng câu 2.", "version": 7 }
```

## Cách tổ chức màn hình

Trang queue cần có trạng thái kết nối từ capabilities; bộ lọc kỳ thi/trạng thái/khoảng ngày; phân trang; số lượng theo trạng thái; nhãn cờ cần rà soát; ngày nộp; phân biệt rõ AI pending/failed với chờ giảng viên. Không render placeholder giống dữ liệu thật.

Trang chi tiết hiển thị kỳ thi, sinh viên, câu hỏi theo thứ tự, transcript cùng raw/normalized rõ ràng, tín hiệu STT như cảnh báo trung tính, bảng rubric và max score. Dùng `aiEvidence` để đánh dấu trích dẫn chính xác trong transcript theo `charStart`/`charEnd`; trích dẫn không xác thực phải có nhãn cảnh báo, không tô như bằng chứng chắc chắn. Hiển thị từng `aiSuggestion` cạnh ô điểm giảng viên, `variance`, `confidence` là tín hiệu nội bộ chưa hiệu chuẩn. Hiển thị `aiFeedback.flags` và `needsAttention` cho giảng viên.

Nút Nhận tất cả gợi ý AI gọi `POST accept-ai` và cần xác nhận. Nút Chốt điểm chỉ bật khi có đủ điểm cho tất cả question/criterion, version còn mới, và người dùng xác nhận. Nhận xét sinh viên mặc định `DRAFT`; chỉ gửi `commentStatus: "APPROVED"` khi giảng viên duyệt rõ ràng. Kết quả gọi settings không mở quyền bulk accept vì backend hiện từ chối bật.

## Đồng bộ và lỗi

- Dùng `version` từ response mới nhất. Sau mỗi lần lưu, thay version bằng version trả về. `409 STALE_VERSION`: tải lại detail, giữ bản nháp local có cảnh báo và cho người dùng đối chiếu, không ghi đè âm thầm.
- `423 LOCKED_BY_OTHER`: chuyển read-only và hiển thị thông báo khóa; `LOCK_EXPIRED`: claim lại.
- `403 ACCESS_DENIED`: báo không được phân công; `401`: dùng luồng đăng nhập hiện hành.
- `503 AI_NOT_READY`: không cho chạy AI, nhưng vẫn cho chấm tay nếu submission `IN_REVIEW`.
- `429 RATE_LIMITED`: tôn trọng header `Retry-After`.
- `422 INCOMPLETE_SCORES` hoặc `REASON_REQUIRED`: hiển thị đúng tiêu chí/lý do cần bổ sung.
- `409 ALREADY_FINALIZED`/`NOT_FINALIZED`/`RUBRIC_MISMATCH`: tải lại trạng thái, không tự retry thao tác ghi.
- Thang điểm là 10 cố định; settings chỉ cho phép thay bước làm tròn trong bản hiện tại.
- Mọi lỗi có `errorCode`; không chỉ hiển thị một thông báo chung.

## TypeScript types đề xuất

```ts
type UUID = string;
type GradingStatus = "WAITING_SESSION" | "AI_PENDING" | "AI_SCORING" | "AI_SCORED" | "AI_FAILED" | "IN_REVIEW" | "FINALIZED" | "RELEASED" | "REOPENED";
type AiRunStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
type LecturerScoreSource = "ACCEPTED_AI" | "OVERRIDDEN" | "MANUAL";
type CommentStatus = "DRAFT" | "APPROVED";

interface GradingCapabilities {
  available: boolean;
  ai: { ready: boolean; provider: string; model: string; reason: string };
  features: { autoScoring: boolean; bulkAccept: boolean; release: boolean };
  version: string;
}
interface GradingQueueItem {
  submission_id: UUID; attempt_id: UUID; exam_id: UUID; student_id: UUID;
  student_name: string; exam_name: string; attempt_no: number; status: GradingStatus; ai_status: string;
  priority_score: number; priority_reasons: string; session_finished_at: string;
  ai_scored_at: string | null; finalized_at: string | null; version: number;
}
interface CriterionScoreInput {
  questionId: UUID; criterionId: UUID; score: number; maxScore: number;
  source: "OVERRIDDEN" | "MANUAL"; overrideReason?: string; comment?: string;
}
interface AiEvidence {
  question_id: UUID; criteria_id: UUID; turn_id: UUID; quote: string;
  char_start: number | null; char_end: number | null; verified: boolean; verification_code: string;
}
```

Some JDBC detail fields currently use snake_case and JSONB values are returned as JSON strings. Normalize only in the API service layer, preserve unknown fields safely, and validate runtime data. The queue/detail use UUIDs even though the old FE evaluation types may declare numeric IDs; update those types.

## Acceptance and edge cases

Test no fake data; disabled provider; AI pending/running/done/failed; manual-only grading; conflict 409 and lock 423; lock expiry; stale versions; duplicate submit; failed AI with manual path; evidence verified/unverified; STT review flags visible without point deductions; finalized/released/reopened revision history; auto release policy; role denied; paging and date filters; route refresh/deep links; empty queue; API timeout; English/Vietnamese i18n and keyboard/screen-reader labels. Do not fabricate audio player until an authorized audio API exists.
