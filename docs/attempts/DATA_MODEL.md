# Attempts Data Model

Migration: `src/main/resources/db/migration/V5__exam_attempts.sql` (PostgreSQL, apply after V4 using `psql`). It is additive, idempotent, and preserves the existing enrollment/interview/grade IDs.

| Table/column | Purpose |
| --- | --- |
| `exam_attempt_settings` | One policy row per exam: max attempts, official score policy, repeat avoidance, cooldown/window, start counting point, extra-attempt permission and abandoned/expired counting. Existing/new exams default to one attempt and `LECTURER_CHOOSES`. |
| `student_attempt_allowance` | One row per exam/student with total extra allowance, last reason/actor/time and optimistic version. Every grant/revoke is also preserved as an audit event. |
| `exam_attempts` | One scheduled/interview attempt with attempt number, slot/time, counted flag, state, timestamps, void metadata and version. Unique `(exam_id,student_id,attempt_no)` and unique `interview_session_id`. |
| `viva_attempt.exam_attempt_id` | Unique link from the existing interview session record to its exam attempt. `viva_attempt.assignment_id` is no longer unique, allowing multiple interviews for one enrollment. |
| `grading_submissions.exam_attempt_id`, `attempt_no` | Unique Group 4 grading submission link and queue filter. Existing submission ID and attempt ID remain intact. |
| `exam_student_results` | One official result pointer or average total per exam/student, policy, explicit chooser metadata and release flag. |
| `attempt_audit_events` | Append-only event rows, sequenced and SHA-256 chained per exam/student. Stores no transcript/audio content. |
| `student_session_assignment.removed_at` | Soft removal marker; existing completed attempts/enrollment remain available for reports and audit. |
| `attempt_question_plan.selection_flags_json` | Stores `QUESTION_REPEATED_DUE_TO_BANK_SIZE` when repeat avoidance had to relax. |

Every old `student_session_assignment` becomes attempt 1. Its `slot_order` and `scheduled_time` are copied exactly. Its existing `viva_attempt.attempt_id` is reused as `exam_attempts.attempt_id` and as `interview_session_id`; Group 4 and final-grade foreign keys are preserved. Existing final lecturer grades with actor/time seed `exam_student_results` as the current official selection and released legacy result. Old rows lacking explicit actor/time remain in the original report table, not automatically published as a verified official result.
