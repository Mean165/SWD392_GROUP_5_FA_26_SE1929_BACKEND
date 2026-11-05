# Prompt Giao AI Frontend: Multi-Attempt Exams

Bạn đang làm React/Vite frontend cho AiViva. Hãy khảo sát code FE hiện có rồi triển khai UI số lượt thi cho trang kỳ thi `/lecturer/exam-sessions/{id}`, lịch thi sinh viên, trang bắt đầu phòng phỏng vấn, hàng đợi/chấm điểm và báo cáo Group 6. Tái sử dụng router/API client/component/i18n convention hiện tại. Không sửa backend và không dùng dữ liệu giả.

Backend đã có contract thật dưới đây. Tất cả ID là UUID string. API trả envelope `{success,message,data,timestamp,errorCode}`; dùng đúng `data`. Tất cả thao tác giới hạn lượt phải gọi BE; FE không tự quyết định cho phép bắt đầu.

## Bối cảnh và luật nghiệp vụ

- Một sinh viên có một enrollment `assignmentId` trong mỗi kỳ thi và nhiều `examAttemptId`. `attemptId` hiện có trong API interview là `viva_attempt`/interview-session ID, không phải ID kỳ thi-lượt.
- Mỗi lượt có lịch, trạng thái, câu hỏi, bài chấm, điểm AI và điểm giảng viên độc lập. Không ghi đè lượt cũ.
- Mặc định một lượt; điểm chính thức mặc định `LECTURER_CHOOSES`. Các policy: `LATEST`, `HIGHEST`, `FIRST`, `AVERAGE`, `LECTURER_CHOOSES`.
- Lượt chỉ bị tính khi server đã lưu câu MAIN đầu tiên. Device-check, vào trang, phòng chờ và sự cố trước câu đầu không tính. Tiếp tục phiên khi reconnect phải gửi lại cùng `examAttemptId`.
- “Hoàn lượt” chỉ với lý do qua hành động của giảng viên/admin. Lượt COMPLETED/voided/audit vẫn giữ trong lịch sử. Không dùng AI suggestion thay điểm chính thức.
- Điểm chính thức chỉ hiển thị cho sinh viên khi `officialResult.released=true`. Không gửi/hiển thị điểm gợi ý AI cho sinh viên.

## Endpoint và payload thực tế

### Giảng viên, kỳ thi

- `GET /api/exam-sessions/{examId}/attempt-settings` trả:

```json
{
  "maxAttempts": 1,
  "scorePolicy": "LECTURER_CHOOSES",
  "avoidRepeatQuestions": true,
  "cooldownMinutes": 0,
  "attemptWindowStart": null,
  "attemptWindowEnd": null,
  "countOnStartEvent": "FIRST_QUESTION_ASKED",
  "allowLecturerExtraAttempts": true,
  "countAbandoned": true,
  "countExpired": false,
  "affectedStudents": [],
  "version": 0
}
```

- `PUT /api/exam-sessions/{examId}/attempt-settings` gửi tất cả cấu hình cùng version:

```json
{
  "maxAttempts": 2,
  "scorePolicy": "HIGHEST",
  "avoidRepeatQuestions": true,
  "cooldownMinutes": 60,
  "attemptWindowStart": null,
  "attemptWindowEnd": null,
  "allowLecturerExtraAttempts": true,
  "countAbandoned": true,
  "countExpired": false,
  "version": 0
}
```

Response có `affectedStudents` khi giảm giới hạn thấp hơn số đã dùng. Hiển thị cảnh báo và danh sách bị ảnh hưởng trước khi người dùng lưu; server vẫn giữ attempt đã sử dụng.

- `GET /api/exam-sessions/{examId}/students?page=0&size=20` giữ roster cũ và thêm:

```json
{
  "assignmentId": "<uuid>",
  "studentId": "<uuid>",
  "attemptsUsed": 1,
  "attemptsAllowed": 2,
  "attemptsRemaining": 1,
  "extraAttempts": 0,
  "currentAttempt": { "examAttemptId": "<uuid>", "attemptNo": 2, "status": "SCHEDULED" },
  "attempts": [
    { "examAttemptId": "<uuid>", "attemptNo": 1, "status": "COMPLETED", "scheduledTime": "...", "interviewSessionId": "<uuid>", "aiSuggestedTotal": 7.5, "finalTotal": 8.0, "counted": true, "voided": false },
    { "examAttemptId": "<uuid>", "attemptNo": 2, "status": "SCHEDULED", "scheduledTime": null, "interviewSessionId": null, "aiSuggestedTotal": null, "finalTotal": null, "counted": false, "voided": false }
  ],
  "officialResult": { "policy": "HIGHEST", "officialAttemptId": "<uuid>", "total": 8.0, "released": true },
  "removed": false
}
```

For lecturer UI, retain all AI and final attempt values. `officialResult.total` is null before publication or before a final grade exists.

- `GET /api/exam-sessions/{examId}/students/{studentId}/attempts` returns the attempt list and counters.
- `POST /api/exam-sessions/{examId}/students/{studentId}/grant-attempts`:

```json
{ "additional": 1, "reason": "Đã xác minh sự cố mất kết nối" }
```

- `POST /api/exam-sessions/{examId}/students/{studentId}/revoke-attempts` uses the same body. Cannot revoke extra attempts already consumed.
- `POST /api/exam-sessions/{examId}/students/{studentId}/attempts` allocates the next available attempt and schedules it:

```json
{ "scheduledTime": "2026-11-10T09:00:00+07:00" }
```

This is the schedule for a new attempt, not an enrollment duplicate.
- Existing `PUT /api/exam-sessions/{examId}/students/{studentId}/schedule` accepts `{ "scheduledTime": "...", "attemptNo": 2 }`; attemptNo defaults to 1.
- Existing `POST /api/exam-sessions/{examId}/schedule/auto` accepts existing `startTime`, `slotMinutes`, `seed`, `shuffle`, `dryRun` plus `attemptNo` (default 1). Preview is deterministic for a seed and never reschedules counted/completed attempts.
- `POST /api/exam-attempts/{examAttemptId}/cancel` and `/void` use `{ "reason": "..." }`. Cancel only an unused scheduled attempt. Void retains the row and excludes it from count/policy; released attempts return `CANNOT_VOID_RELEASED`.
- `PUT /api/exam-sessions/{examId}/students/{studentId}/official-attempt`:

```json
{ "attemptId": "<examAttemptId>", "confirm": true }
```

Only for `LECTURER_CHOOSES`, only finalized and non-void attempts. Show an explicit confirmation dialog.
- `GET /api/exam-sessions/{examId}/attempts/audit` and `GET /api/exam-sessions/{examId}/official-results` support the audit and Group 6 views.
- Bulk add `POST /api/exam-sessions/{examId}/students/bulk` accepts existing `studentIds`/`studentCodes`, plus optional `extraAttemptsByIdentifier` map keyed by UUID/code/email. Import CSV supports optional `extra_attempts` column; XLSX first column is student identifier, second is extra attempt count.

### Student and interview

- `GET /api/student/exam-assignments` and `/history` keep current response shape and add attempt counters/list and `officialResult`. Hide official ID/total and all final attempt totals unless `released=true`; hide `aiSuggestedTotal` always.
- `GET /api/student/exam-assignments/{assignmentId}/eligibility?examAttemptId=<uuid>` returns existing `canEnter`, `reason`, `opensAt`, `closesAt` plus `reasonCode`, `attemptsUsed`, `attemptsAllowed`, `attemptsRemaining`, `nextAttemptId`, `nextAttemptNo`, `retryAt`.
- Start via `POST /api/interviews/start` with `{ "examAttemptId": "<uuid>" }`. For compatibility, backend still accepts `assignmentId` or `sessionId`; use `examAttemptId` whenever available. Start response retains `attemptId` (interview session) and adds `examAttemptId`, `attemptNo`.
- Reconnect sends the same `examAttemptId`; it resumes the same interview and does not allocate a new attempt.
- Explicit student stop/abandon: `POST /api/interviews/{interviewAttemptId}/abandon`.
- Device-check APIs do not consume attempts.
- `GET /api/reports/lecturers/exams/{examId}/official-results` returns the same lecturer roster/result model. Existing generic report stubs remain incomplete; use this real endpoint for this feature.

## Lecturer screen requirements

On `/lecturer/exam-sessions/{id}` add an “Số lượt thi” settings section: max attempts, score policy, avoid repeat toggle, cooldown, window, allow extra toggle. Persist with settings `version`; on 409 reload and resolve conflict. When lowering max show exactly which students already exceeded the new limit; never imply completed attempts were deleted.

Change the roster “Lượt thi” cell from raw interview status to `attemptsUsed / attemptsAllowed` and next state. Expand a row to show each attempt number, schedule/slot, interview state, counted/voided marker, AI suggestion clearly labeled, lecturer-final score and whether it contributes to official result. Add actions: grant/revoke with required reason, schedule next attempt, schedule a selected attempt, cancel unused slot, void with required reason and warning that audit/history remains, and select official attempt only in manual policy. Keep old AI and lecturer score labels distinct.

The existing 403 was caused by backend `authorizeOwner` rejecting assigned lecturers who were not exam creators. Backend now permits exam creator OR lecturer assigned to the exam subject, while still denying unrelated lecturers. Keep the visible error for genuine 403s; do not hide it.

## Student screen requirements

Show `attemptsUsed/attemptsAllowed`, remaining count, next allocated attempt/schedule and server-provided eligibility. Map reason codes to vi/en:

- `ATTEMPTS_EXHAUSTED`: đã hết lượt thi / no attempts remain.
- `ATTEMPT_IN_PROGRESS`: cần tiếp tục hoặc xử lý lượt đang mở / another attempt is still active.
- `COOLDOWN_ACTIVE`: thời gian có thể thi lại / retry time, with `retryAt`.
- `OUTSIDE_WINDOW`: ngoài khung giờ / outside the attempt window.
- `NOT_ENROLLED`: không có quyền trong kỳ thi / not enrolled.
- `EXAM_CLOSED`: kỳ thi đã đóng / exam is closed.

Enable Start only from `canEnter=true`; send the returned `nextAttemptId`. Never compute limits locally. After published, show selected attempt and official total/policy; before publication, show “chưa công bố” and no final/AI score. Device-check is separate and must never decrement counters.

## Errors, i18n and accessibility

Handle `409 ATTEMPTS_EXHAUSTED`, `ATTEMPT_IN_PROGRESS`, `COOLDOWN_ACTIVE`, `OUTSIDE_WINDOW`, `EXAM_CLOSED`, `POLICY_NOT_MANUAL`, `CANNOT_VOID_RELEASED`, `STALE_VERSION`, `SCHEDULE_CONFLICT`; `403 NOT_ENROLLED`/`ACCESS_DENIED`; `422 REASON_REQUIRED` and validation errors. Preserve the normal login flow for 401. Do not retry non-idempotent grants automatically. Require typed non-empty reason before grant, revoke, cancel or void. Use accessible labels, focus-managed confirmations, busy/empty/error states, vi/en i18n, and keyboard-operable expanded attempt lists.

## Required FE tests

Test single-attempt legacy display; multiple attempts and grant; manual schedule/auto preview per attempt; counted only after first question; retry same attempt on reconnect; exhausted/cooldown/outside-window errors; void retains row and decreases counted use; settings reduction warning; all five official policies; hide AI and unpublished scores for students; only show official result after release; `LECTURER_CHOOSES` confirmation; CSV/XLSX extras; assigned lecturer sees screen while unrelated lecturer sees 403; page refresh and error/loading/empty states.
