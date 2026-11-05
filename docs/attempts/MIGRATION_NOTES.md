# Migration and Rollback

## Apply

Take a PostgreSQL backup first. V5 depends on V2/V3/V4 objects and is not run by Spring SQL initialization because this repository's statement splitter cannot handle PL/pgSQL `$$` blocks.

```powershell
psql -h <db-host> -p <db-port> -U <db-user> -d <db-name> -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/V5__exam_attempts.sql
```

Before rollout record counts for `student_session_assignment`, `viva_attempt`, `final_grade_report`, and `grading_submissions`. After applying, verify:

```sql
select count(*) from student_session_assignment;
select count(*) from exam_attempts where attempt_no = 1;
select count(*) from viva_attempt where exam_attempt_id is null;
select count(*) from grading_submissions where exam_attempt_id is null;
select count(*) from final_grade_report where final_teacher_score is not null;
```

The first two counts should match for non-removed legacy enrollments; null link counts should be zero. Compare final-grade rows/counts and representative report IDs with the pre-migration record. The migration uses `ON CONFLICT DO NOTHING`, nullable additions followed by backfill/NOT NULL, and guards foreign keys/triggers/indexes so rerunning the file does not duplicate attempts.

## Rollback

Do not delete `exam_attempts`, audit events, V4 grading rows, interview sessions, turns, audio references, or final grades. Before any attempt 2 is used, rollback application behavior by deploying the prior backend and leaving the additive V5 tables/columns in place; old code continues to read enrollment attempt 1 and its preserved IDs.

After a second attempt or a new official result exists, a destructive schema rollback is unsupported. Restore a pre-migration database backup only as a coordinated database recovery operation; do not run `DROP TABLE`, reset, or rewrite histories as routine rollback. New routes can be feature-disabled while keeping all attempt rows for audit and later re-enable.

## Migration test status

JUnit unit/contract tests run locally. A disposable PostgreSQL migration/backfill test was not run because automatic review rejected a local DB create/drop script whose password and cleanup target could not be guaranteed safe. Do not apply V5 to `aiviva_db` until a dedicated disposable database and backup are confirmed.
