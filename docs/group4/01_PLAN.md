# Group 4 Implementation Plan

## Invariants

1. AI may create suggestions only. Only an authenticated, assigned lecturer (or authorized admin policy) may finalize. Service guard, endpoint RBAC, DB constraints and tests enforce this.
2. AI suggestion fields and lecturer draft/final fields are distinct. No unqualified `score` field/API property is introduced.
3. AI runs, final revisions and audit events are immutable. Re-run/reopen creates new run/revision; never overwrite old AI output or final revision.
4. Signals (STT confidence, review flags, fluency) are visible to lecturers but omitted from grading prompt and scoring formula by default (`INCLUDE_SIGNALS_IN_GRADING=false`).
5. Student identity is omitted from LLM input. Local OpenAI-compatible provider is opt-in; cloud LLM is disabled unless an explicit remote override is set with warning.
6. AI score arithmetic is Java `BigDecimal` with configured scale/rounding; the model returns fractions/evidence only.

## Provider and Job Decisions

- Introduce `GradingLlmProvider` with `disabled`, `mock` (dev/test only), and `openai-compatible` providers. Default is `disabled`. The compatible adapter targets a configured loopback endpoint and validates HTTP/JSON response; it uses no SDK/API key. Remote URLs require explicit `GRADING_ALLOW_REMOTE_LLM=true` and startup warning. Live model quality cannot be claimed until a real local server is configured and run.
- Use PostgreSQL tables as a durable queue. A scheduled reconciler creates submissions for completed attempts not yet indexed. A worker claims pending AI runs using `FOR UPDATE SKIP LOCKED`, leases jobs, retries transient errors with backoff, and recovers stale RUNNING leases after process restart. No Kafka/Rabbit dependency is added.
- Snapshot question text, transcript raw/normalized, rubric criteria/weights/expected points and references at submission creation. Keep audio URLs out of LLM prompt; lecturer audio links are signed/authorized and each issue is audited.
- K independent AI samples per criterion; deterministic code calculates per-question/overall suggested totals from validated fractions and the configured scale. Use median as the displayed suggested fraction, persist all sample scores and population variance. Validate evidence quotes against the selected transcript exactly; invalid evidence raises a flag and `needs_attention`, never fabricates an offset.
- `final_grade_report.final_teacher_score` is written only by finalization, together with lecturer actor/time. AI updates only `total_ai_score` compatibility projection and new AI tables. Add DB check/trigger and immutable revision/audit guards; do not delete historical data.

## Milestones

1. G0 survey/plan completed before code.
2. G1 additive SQL/schema, immutable audit/revision primitives and state machine tests. Implemented in the current worktree; migration is idempotent and loaded after `schema.sql` by Spring SQL init.
3. G2 completed-attempt reconciliation, durable submissions, queue filters/page, ownership/assignment checks, capabilities endpoint.
4. G3 local LLM provider boundary, validated structured AI run, evidence verification, K-sample aggregation and retry/recovery.
5. G4 lecturer claim/lease, draft scores/comments, accept/override/manual operations, finalize/reopen/revision, release policy.
6. G5 audio access audit, agreement analytics and metrics.
7. G6 OpenAPI/API contract, runbook, local mock workflow, compatibility notes for FE numeric-ID stub vs backend UUIDs, and a standalone FE prompt.

Each milestone is tested before its commit. No FE files are edited. Do not stage unrelated worktree changes.

## Initial FE Contract Mapping

The FE mock type has `id`, `interviewId`, and `studentId` as numbers, but backend entities use UUID. The implementation will use string UUIDs in the real API and document a required FE type correction in `FE_PROMPT_GRADING.md`; silently converting UUIDs to numbers is invalid. Legacy `POST /api/evaluations` will remain a compatibility endpoint only if its identifier mapping can be supported from actual schema; otherwise document deprecation and keep it non-authoritative.
