# Group 4 Backend Survey

Survey date: 2026-11-05. Backend branch at start: `chore/local-inference`. Frontend was read-only; its worktree contains unrelated uncommitted changes that must be preserved.

## Runtime and Persistence

- Backend: Java 21, Spring Boot 3.3.4, Spring MVC, Spring Data JPA, Spring Security/JWT, PostgreSQL.
- Migration convention: numbered SQL files `V2__...sql`, `V3__...sql` are manual forward migrations; no Flyway or Liquibase dependency/runner is configured. `schema.sql` is run for fresh DB initialization; it is not a migration runner for existing volumes. Group 4 must provide a new forward SQL migration and update the idempotent fresh-install schema.
- JSON support: Jackson; PostgreSQL JSONB exists and is already used for session events, STT corrections, subject lecturer assignments and question metadata.
- Transactions use `@Transactional`; persistence is JPA with some existing `JdbcTemplate` use for JSONB and queue workflows. Existing tests use JUnit 5 and Mockito. No Testcontainers dependency, Resilience4j, Micrometer metrics registry, or general durable job queue has been found.
- Spring Security is stateless JWT. Role codes are `AD`, `LE`, `ST`, and proctor role is present elsewhere. Most lecturer APIs use `hasAnyRole('AD','LE')`; evaluation currently has only a POST controller and inherits generic authenticated policy, so it needs explicit lecturer/admin authorization.
- Lecturer-to-subject assignments are stored in `subject.assigned_lecturer_ids_json` JSONB and queried through native SQL/JdbcTemplate. `ExamSession.created_by` plus topic->subject provide ownership/assignment context. `Subject` JPA entity does not map the JSONB field.

## Existing Group 1/2/3 Data

- Questions: `question` keyed by UUID, linked to `subject_topic`; contains text, Bloom level, source and approval status.
- Rubric: subject rubric in `rubric` and criteria in `rubric_criteria`; each criterion has `criteria_name`, `expected_knowledge_points`, and `weight_ratio`. `RubricResolver.resolveForQuestion` resolves the active rubric for the question's subject and validates weights sum to 1.
- Exam/session: `exam_session`, assignments in `student_session_assignment`, attempts in `viva_attempt`. Completed attempts are the grading source. Current session->student assignment and attempt links use UUID.
- Interview: `interaction_turn` stores MAIN/FOLLOW_UP question text, transcript, audio reference and turn order; `stt_result` stores raw/normalized transcript, corrections, confidence/review flags; `acoustic_fluency_metric` stores fluency/speech signals; `session_event` is an append-style event table.
- Existing AI grading artifacts: `ai_rubric_assessment` (450 rows at survey, aggregate count only) stores AI suggested points/compliance/reasoning per turn+criterion; `ai_claim_evidence` (450 rows) stores quotes and offsets. Neither table has an AI run/revision key. Keep all old rows; add nullable linkage for new runs rather than rewriting legacy rows.
- Existing aggregate grade table: `final_grade_report` has `total_ai_score NOT NULL`, `final_teacher_score`, `finalized_by_lecturer_id`, `finalized_at`, and teacher adjustment reason. 30 rows existed; 13 completed attempts, 3 completed attempts without a report. Aggregate consistency query found zero existing rows where final score/actor/timestamp nullability disagreed. Existing table mixes legacy AI and lecturer projections; Group 4 will use separately named draft/final/revision structures and only update the legacy final projection on lecturer finalization.
- No row-level student data or transcripts were read during the survey.

## Existing Evaluation and LLM

- `evaluation` package is a stub: `POST /api/evaluations`, `EvaluationServiceImpl` returns an ID-less `AI_SUGGESTED` response; `ScoreCalculationService`, `AIScoringService`, and `AIEvaluationService` are empty/underspecified. No evaluation package tests were found.
- FE `/lecturer/evaluations` and child detail/review routes currently render `WorkflowPage`; `evaluationService.ts` returns `backendUnavailable`. FE types use optional numeric IDs and vague single `finalScore`/`aiSuggestedScore`; backend source identifiers are UUID. The FE page explicitly requires live data, distinct AI suggestion vs lecturer final score, and no fabricated results. Do not modify FE in this backend task.
- The existing interview `LlmProviderAdapter` has an OpenAI-specific HTTP adapter. `LLM_PROVIDER` config is used by question generation; interview follow-up currently injects the OpenAI adapter. Reusing that adapter for grading would transmit transcript content to the configured endpoint, potentially cloud. Group 4 must use a separate provider boundary with cloud disabled by default and local OpenAI-compatible endpoint support; never send student identity.
- No local grading LLM server or grading API key was probed/used. Production grading must report AI unavailable until a real local-compatible model is configured. Mock is test/dev only.

## Observability and Gaps

- Existing response envelope is `ApiResponse`; pageable response utility is `PageResponse`. Existing security actor can be read from Spring Security context/JWT.
- There is no durable grading queue, submission claim/lease, score revision history, immutable grading audit table/hash chain, release workflow, agreement analytics or grading metrics. The question-generation subsystem has a DB-backed worker pattern and `@Async`; inspect and reuse only where safe.
- `schema.sql` defines current AI assessment/evidence/final report tables without append-only triggers or DB rules requiring lecturer actor for official final score. Group 4 needs additive DDL and tests.
- FE's desired contract and exact page interactions live in `src/pages/lecturer/evaluations`; no FE files were edited.

## Decisions for Implementation

- Keep the AI suggestion model separate from lecturer score/revision tables and the existing `final_grade_report` official-score projection.
- A submission can be created and manually reviewed without an LLM. Automatic AI scoring is unavailable unless the configured local-compatible provider is ready.
- A completed attempt's rubric/question/turn context is snapshotted; each grading run is immutable. Lecturer drafts are mutable with optimistic version checks; finalized revisions and audit events are append-only.
- Existing identity remains in BE/DB only. The grading provider receives temporary aliases for question/criterion/turn IDs, transcript, rubric and evidence context, no student name/code/email or attempt/session IDs.
