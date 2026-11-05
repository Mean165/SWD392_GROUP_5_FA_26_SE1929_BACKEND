# Attempts Implementation Plan

## Decisions

1. Preserve `student_session_assignment` as one enrollment row. Put repeatable scheduled attempts in `exam_attempts`; this preserves old enrollment identities and prevents duplicated student roster records.
2. Migrate every existing enrollment to attempt 1, preserving its schedule and linking its current `viva_attempt`. Existing sessions, turns, audio, per-attempt grades and Group 4 audits stay in place.
3. Add `exam_attempt_id` to `viva_attempt` and `grading_submissions`; retain existing `attempt_id` relationships for compatibility and old reports. Each attempt has an independent `viva_attempt`, grading submission, final grade report and Group 4 history.
4. A row lock on the enrollment serializes eligibility/allocation/counting. The unique attempt number constraint protects against races across app instances. Existing assignment input remains a compatibility selector for attempt 1; new student starts send `examAttemptId`.
5. Count at the first persisted MAIN question transaction, not at page entry or device check. Existing in-progress sessions resume their existing attempt.
6. Fix lecturer access at the common `authorizeOwner` boundary: exam creator, assigned subject lecturer, or admin. Do not loosen role checks globally.
7. Reuse Group 4 append-only hash-chain patterns for attempt events. Official result is a separate projection; `LECTURER_CHOOSES` has no system/AI fallback.

## Milestones

- A1 survey, access-denied evidence, additive migration/backfill and rollback notes.
- A2 attempt domain, settings, allowance, eligibility service, locking and audit events.
- A3 interview start/resume/count integration and question-repeat filtering.
- A4 additional grant/revoke, attempt scheduling, auto-schedule attemptNo, imports and removal behavior.
- A5 official score policies, Group 4 submission/release integration, Group 6 reporting.
- A6 student endpoints, concurrency/query tests and 500-student query validation.
- A7 design/API/FE prompt/runbook/limitations.

Each milestone should have focused tests and its own commit. No frontend files are edited here. FE work is handed off through `FE_PROMPT_ATTEMPTS.md` after API shapes are implemented.

## Current implementation checkpoint

- A1 survey, exact 403 cause, additive V5, attempt-1 backfill, and non-destructive rollback instructions are drafted. V5 was not applied to a real database.
- A2 domain/policy, attempt settings, allowance, eligibility, row lock, and append-only audit integration are implemented.
- A3 start links `examAttemptId`, persists first MAIN turn and counts in one transaction, resumes same session, and filters previously asked questions.
- A4 grant/revoke/void/cancel, manual/auto schedule by attempt number, import extras and removal marker are implemented.
- A5 score-policy projection is integrated with Group 4 finalize/reopen/release and the Group 6 official-results route.
- A6 student schedule/eligibility is extended and roster queries are batched in the attempt service. No live 500-student PostgreSQL benchmark or concurrent-start DB test was run.
- A7 docs and FE handoff exist.

The full Java suite passed on this machine after the implementation checkpoint. Migration/backfill and concurrent lock behavior remain unverified against PostgreSQL because automatic review blocked the disposable-DB create/apply/drop command.

## Commit grouping

The attempt persistence, eligibility and interview lifecycle touch shared service/entity contracts across A1-A6. To avoid intermediate commits that leave the application uncompilable or count a turn before it has a durable exam-attempt link, implementation commits group the coordinated backend changes and keep the handoff documentation in a separate commit. Focused tests ran before the implementation commit; full-suite test results are recorded in the final handoff.

## Risks to verify

- The repository has no existing interview-session table separate from `viva_attempt`; use `viva_attempt` as the interview-session record and preserve its ID.
- Existing SQL initialization modifies base tables at application startup; the new migration remains a separate `psql` forward migration because V4 uses PostgreSQL PL/pgSQL blocks.
- Reopen/finalize/void must update the per-student official projection transactionally and append audit events without mutating historical revisions.
- Current scheduling overlap checks inspect enrollment rows; they must be changed to inspect non-cancelled attempt slots without counting two attempts as two students for roster totals.
