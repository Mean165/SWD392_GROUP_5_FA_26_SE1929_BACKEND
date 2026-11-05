# Attempts API Contract

All routes use the existing `/api` namespace, JWT, `ApiResponse` envelope and repository error formatting. Lecturer/admin exam routes additionally verify exam creator or assigned subject lecturer. Student routes derive student identity from JWT. `examAttemptId` is a UUID; old `assignmentId` input remains accepted to start attempt 1 or resume the currently allocated attempt.

## Lecturer routes

| Method/path | Request/result |
| --- | --- |
| `GET /api/exam-sessions/{examId}/attempt-settings` | Returns defaults or saved policy and `version`. |
| `PUT /api/exam-sessions/{examId}/attempt-settings` | `{maxAttempts,scorePolicy,avoidRepeatQuestions,cooldownMinutes,attemptWindowStart,attemptWindowEnd,allowLecturerExtraAttempts,countAbandoned,countExpired,version}`. Returns `affectedStudents` when a lowered limit is below already-used attempts. |
| `GET /api/exam-sessions/{examId}/students` | Existing `PageResponse` and fields retained; adds `attemptsUsed`, `attemptsAllowed`, `attemptsRemaining`, `extraAttempts`, `currentAttempt`, `attempts[]`, `officialResult`, `removed`. |
| `GET /api/exam-sessions/{examId}/students/{studentId}/attempts` | Lists each attempt with number, status, time, counted flag and per-attempt grades. |
| `POST /api/exam-sessions/{examId}/students/{studentId}/attempts` | `{ "scheduledTime": "2026-11-10T09:00:00+07:00" }`; allocates/schedules next available attempt, does not alter attempt 1 history. |
| `POST /api/exam-sessions/{examId}/students/{studentId}/grant-attempts` | `{ "additional": 1, "reason": "Verified connection failure" }`; additional is a total allowance increase, not an attempt-number overwrite. |
| `POST /api/exam-sessions/{examId}/students/{studentId}/revoke-attempts` | `{ "additional": 1, "reason": "Correction of duplicate grant" }`; cannot revoke already-used extra attempts. |
| `POST /api/exam-sessions/{examId}/schedule/auto` | Existing body plus optional `attemptNo` (default 1); preserves `seed`, `shuffle`, `slotMinutes`, `dryRun`. Does not reschedule counted/completed attempts. |
| `PUT /api/exam-sessions/{examId}/students/{studentId}/schedule` | Existing `scheduledTime` plus optional `attemptNo` (default 1). |
| `PUT /api/exam-attempts/{attemptId}/schedule` | `{ "scheduledTime": "...", "slotOrder": 4 }` for a particular allocated attempt. |
| `POST /api/exam-attempts/{attemptId}/cancel` | `{ "reason": "Schedule removed before start" }`; only unused scheduled attempt. |
| `POST /api/exam-attempts/{attemptId}/void` | `{ "reason": "Confirmed platform outage" }`; preserves record and audits; disallowed after release. |
| `PUT /api/exam-sessions/{examId}/students/{studentId}/official-attempt` | `{ "attemptId": "<examAttemptId>", "confirm": true }`; only `LECTURER_CHOOSES`, only finalized non-void attempt. |
| `GET /api/exam-sessions/{examId}/attempts/audit` | Ordered append-only audit events. |
| `GET /api/exam-sessions/{examId}/official-results` | Lecturer Group 6 source, including official policy, selected attempt/average and each attempt detail. |

`POST /api/exam-sessions/{id}/students/bulk` accepts existing `studentIds`/`studentCodes`; optional `extraAttemptsByIdentifier` maps student code, email or UUID string to a nonnegative integer. Grants use reason `import`. CSV accepts optional `extra_attempts` column; XLSX uses first column identifier and second column extra count. Invalid rows are returned in `skipped`.

The existing roster fields remain: `assignmentId`, `sessionId`, `studentId`, `scheduledTime`, enrollment `status`, `slotOrder`, `attemptId`, `attemptStatus`, `totalAiScore`, `finalTeacherScore`. New `attemptId`/status identify current/most recent attempt for compatibility; new clients should use `attempts[]` and `officialResult`.

## Student routes

- `GET /api/student/exam-assignments` and `/history` keep their existing pagination/envelope and add the attempt summary/list and official result.
- `GET /api/student/exam-assignments/{assignmentId}/eligibility?examAttemptId=<uuid>` returns existing `canEnter/reason/opensAt/closesAt` plus `reasonCode`, allowance counters, `nextAttemptId`, `nextAttemptNo`, `retryAt`.
- `POST /api/interviews/start` prefers `{ "examAttemptId": "..." }`. Existing `{ "assignmentId": "..." }` or `{ "sessionId": "..." }` remains compatible and resolves the currently eligible attempt server-side.
- Start response adds `examAttemptId` and `attemptNo` while preserving the existing `attemptId` (the viva/interview session UUID).
- Explicit interruption recovery: `POST /api/interviews/{attemptId}/abandon`. Reconnect calls the same start attempt and resumes existing viva session; it does not allocate a new attempt.

## Errors

| HTTP | Code | Meaning |
| --- | --- | --- |
| 403 | `NOT_ENROLLED` | Not an active enrollment or attempt belongs to another student. |
| 409 | `ATTEMPTS_EXHAUSTED` | No unused counted allowance remains. |
| 409 | `ATTEMPT_IN_PROGRESS` | Another scheduled/in-progress attempt must be handled first. |
| 409 | `COOLDOWN_ACTIVE` | Includes retry/open time in message and `retryAt` on eligibility response. |
| 409 | `OUTSIDE_WINDOW` | Attempt/global window or slot is not open. |
| 409 | `EXAM_CLOSED` | Exam is completed/cancelled. |
| 409 | `POLICY_NOT_MANUAL` | Lecturer tried to select while a computed score policy is active. |
| 409 | `CANNOT_VOID_RELEASED` | Reopen/appeal the published grade first. |
| 409 | `STALE_VERSION` | Reload attempt settings/results then retry deliberately. |
| 422 | `REASON_REQUIRED` | Grant/revoke/void/cancel reason missing. |

API error envelopes retain the repository's `{success,message,errorCode}` structure.
