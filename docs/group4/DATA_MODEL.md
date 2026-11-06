# Group 4 Data Model

The additive migration is `src/main/resources/db/migration/V4__group4_grading_workflow.sql`; apply it with PostgreSQL `psql` after the existing `schema.sql` base tables exist. Existing AI assessments, evidence and final grade rows are retained.

| Table | Purpose |
| --- | --- |
| `grading_submissions` | Queue row for a completed `viva_attempt`, assignment to exam/student, status, lease, version and current revision. |
| `grading_rubric_snapshots` | Immutable JSON snapshot of turns, transcript variants, active rubric criteria/weights and hash. |
| `ai_grading_runs` | Durable AI run queue, provider/model/prompt version, idempotency hash, retries, lease and latency. |
| `ai_rubric_assessment` | Existing AI criterion suggestions; new rows link to `run_id` and carry K samples, variance, confidence and evidence verification. Existing rows keep `run_id=NULL`. |
| `ai_criterion_evidence` | New run evidence with exact quote and character offsets into the selected stored transcript. |
| `ai_question_feedback` | Strengths, weaknesses, missing points, flags and review state per run/question. |
| `lecturer_criterion_scores` | Lecturer-only score cells; separate from AI suggestion rows. |
| `lecturer_question_notes` | Lecturer note and separately approved student-visible comment. |
| `grade_revisions` | Append-only official score snapshots. |
| `grading_audit_events` | Per-submission append-only SHA-256 chain. |
| `grading_exam_settings` | K, rounding, release and review settings; fairness-signal scoring remains disabled. |
| `grading_exam_audit_events` | Append-only audit chain for exam-level settings changes. |

`exam_id` references the existing `exam_session.session_id`; `attempt_id` references `viva_attempt.attempt_id`. Questions and turns reuse `interaction_turn.main_question_id`, STT results and the active subject rubric. No old rows are rewritten. Attempts with an already populated official final score are excluded from new reconciliation so the migration does not replace legacy final history.
