# Attempts Feature Survey

Surveyed repository on `chore/local-inference` after Group 4 commits `b3dff8c`, `96e7876`, and `377ece3`. Frontend files were read-only; no FE files were changed.

## Existing one-attempt assumption

- `student_session_assignment` is the enrollment/slot row and currently has unique `(session_id, student_id)` (`ux_assignment_session_student`). It stores one `scheduled_time`, `slot_order`, enrollment `status`, and `cancelled_reason`.
- `viva_attempt.assignment_id` is `UNIQUE NOT NULL`; one interview session is attached to each enrollment. `viva_attempt.status` is currently `NOT_STARTED | IN_PROGRESS | COMPLETED`.
- `interaction_turn`, `stt_result`, fluency metrics, question plans, session events and audio URLs refer to `viva_attempt.attempt_id`; they are already naturally isolated per interview session.
- `final_grade_report.attempt_id` is unique, so each interview currently has one final report. Group 4 `grading_submissions.attempt_id` is also unique and points to the same `viva_attempt`; criterion drafts, AI runs, revisions and audit events are per submission.
- There is no attempt number, allowance, exam-level score policy, official attempt pointer, or durable attempt audit table. The roster API returns one `StudentSessionAssignmentResponse` per enrollment and labels the `viva_attempt.status` as `attemptStatus`. The FE currently renders that status in the “Lượt thi” column.
- Current schema/migrations: PostgreSQL; forward SQL files are applied with `psql`; Spring runs `schema.sql` on application initialization. V2/V3/V4 are additive/manual forward migrations. Do not run PL/pgSQL dollar-quoted scripts through Spring's SQL initializer.

## Interview start and counting point

`InterviewController.POST /api/interviews/start` calls `InterviewServiceImpl.startInterview`. It locks the owned `student_session_assignment`, checks `ExamEntryEligibility`, creates or resumes the unique `viva_attempt`, changes the enrollment status to `IN_PROGRESS`, and creates the first `interaction_turn` before returning the question response. This transaction is the repository's closest durable signal that the first question has been asked/issued. It can mark the associated `exam_attempts.counted=true` atomically when it inserts the first MAIN turn. Existing disconnect recovery resumes the same `viva_attempt`; it must not allocate another exam attempt.

Device-check uses a separate route/provider and does not call `startInterview`, so it must remain outside attempt counting.

## Scheduling and question reuse

- `ExamSessionController.POST /api/exam-sessions/{id}/schedule/auto` delegates to `ExamSessionServiceImpl.autoSchedule`. It sorts pending/scheduled assignment rows, optionally shuffles using the supplied seed, assigns sequential slots, supports `dryRun`, and does not currently check a specified attempt number.
- Manual scheduling is `PUT /api/exam-sessions/{sessionId}/students/{studentId}/schedule`; it updates the single assignment row and checks student/session overlap.
- Bulk assignment and CSV/XLSX import both create `student_session_assignment` rows. Remove currently rejects rows with IN_PROGRESS or COMPLETED attempts; otherwise it deletes the enrollment row.
- `InterviewServiceImpl.previousStudentQuestionIds` currently searches previous assignments in the same exam/session and their question plans; `QuestionPlanService` also checks adjacent slot plans. Multi-attempt avoidance must additionally exclude questions from earlier counted, non-voided attempts for this same student/exam. Existing Bloom allocation and adjacent-student fairness rules must remain.

## Group 4 and Group 6

- Group 4's `grading_submissions` is unique by `viva_attempt_id`, not by student. That is the correct isolation boundary for multiple attempts. Add a unique `exam_attempt_id` link and `attempt_no` for queue display/filtering, retaining old submission, AI-run, criterion, revision and audit rows.
- `final_grade_report` stays per interview. It is not the official per-student exam result after this change. A new `exam_student_results` projection stores the selected official result separately.
- Group 6 report/statistics/export services currently aggregate final reports through assignments/attempts. They need to filter voided attempts and use the official result projection for exam totals while preserving per-attempt detail.

## “Access denied” finding

The FE detail hook's first request is `GET /api/exam-sessions/{id}`; the red page-level error renders `detail.error` from this request. `ExamSessionServiceImpl.getExamSessionById`, roster, schedule, readiness and mutations call `authorizeOwner`. That method permits AD and LE roles, then rejects every LE whose user ID differs from `exam_session.created_by`. It never checks `subject.assigned_lecturer_ids_json`, even though the project already has `SubjectRepository.isLecturerAssigned(subjectId, lecturerId)` and uses it for lecturer-to-subject authorization elsewhere. Therefore an assigned lecturer who did not create the exam gets 403 `ACCESS_DENIED`. Fix the shared authorization helper to allow creator OR assigned lecturer for the exam topic's subject; continue denying unrelated lecturers/students.

The FE also calls `/api/topics`, `/api/exam-sessions/{id}/students`, `/readiness`, and other detail APIs. Their role matcher allows LE, and the same service authorization explains 403s on exam-scoped endpoints. The primary page error is specifically the detail request above; preserve visible 403s for unrelated topics or invalid sessions.

## Legacy migration mapping

Keep `student_session_assignment` as the one enrollment row and preserve its slot/status. Create attempt number 1 for every existing enrollment. Copy its `scheduled_time` and `slot_order`; link the old `viva_attempt` if present and retain all old turn, audio, score and Group 4 rows. Derive attempt status conservatively: completed interview => COMPLETED/counts; an interview with a persisted first MAIN turn => IN_PROGRESS/counts; scheduled enrollment without a counted start => SCHEDULED/not counted; cancelled/absent legacy enrollment => CANCELLED/EXPIRED and not counted. No old row is deleted or reassigned to a different student/exam.

Current environment data counts were not re-queried during this survey; migration must be verified on a backup/disposable DB with before/after row counts and foreign-key checks before deployment.

## Decisions and open constraints

- Use `exam_attempts` as per-attempt scheduling/state and `student_session_assignment` as enrollment. Keep the old assignment schedule fields as a compatibility mirror of attempt 1 only.
- Keep one active SCHEDULED/IN_PROGRESS attempt per enrollment; lock the enrollment row during eligibility check and attempt allocation. Unique `(exam_id,student_id,attempt_no)` is a second guard.
- Tie each `viva_attempt` to one `exam_attempts` row. Relax uniqueness on its enrollment FK, add a unique `exam_attempt_id`, and keep the old enrollment FK for compatibility.
- Official result is separate from per-attempt final reports. Only a lecturer action may move the official pointer for `LECTURER_CHOOSES`.
- Rollback is forward-only for data: a rollback script may disable new APIs/restore one-attempt policy, but must not delete attempts or links. Structural rollback after real attempt 2 data exists is intentionally unsupported.
