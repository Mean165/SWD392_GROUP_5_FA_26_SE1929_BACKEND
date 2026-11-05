# Known Limitations

- PostgreSQL V5 backfill and locking were not exercised on a scratch database in this run. Automatic approval review rejected the attempted create/apply/drop script; do not apply V5 to the shared `aiviva_db` without a backup and a dedicated database.
- No multi-connection concurrency integration test or 500-student P95 benchmark has been run. Enforcement is coded with the locked enrollment row plus unique attempt-number constraint.
- Count event is the first persisted MAIN question returned by the interview start transaction. The server cannot attest that the browser actually played the audio; a TTS/playback failure after the response may still consume the attempt.
- Existing Group 6 generic `ReportServiceImpl`, `ScoreStatisticsService` and `GradeExportService` remain stubs. This change adds `GET /api/reports/lecturers/exams/{examId}/official-results` and enriches student schedule/history; other school export templates need their own schema/requirements.
- Extra attempts cannot be granted after an exam session is COMPLETED/CANCELLED; reopen the exam session flow is not implemented. A cancelled exam remains closed.
- The roster compatibility `attemptId` is now the current/most recent interview session; clients should use `attempts[].examAttemptId` for attempt operations.
- Student-visible score data is withheld until `officialResult.released=true`; unapproved AI suggestions are never returned as student scores.
- CSV recognizes `studentCode` or `email` and optional `extra_attempts`; XLSX reads identifier in column A and optional extra count in column B. No richer column-mapping preview was added.
- Removing a student uses `removed_at` and cancels unused slots; completed attempts/grades remain queryable. Re-enrollment of a previously removed student is not exposed as a separate workflow.
