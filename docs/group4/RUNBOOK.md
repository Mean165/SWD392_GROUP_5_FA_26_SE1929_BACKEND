# Group 4 Runbook

## Start with AI disabled

On a backup/disposable environment first, apply the migration after existing `schema.sql` has created the base tables:

```powershell
psql -h localhost -p 5433 -U postgres -d aiviva_db -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/V4__group4_grading_workflow.sql
```

Set `SPRING_DATASOURCE_URL` to the same PostgreSQL database used by the backend, without printing credentials in a shared terminal/log. Spring SQL init is not used for V4 because its statement splitter does not safely process PL/pgSQL `$$` blocks. Confirm a database backup before applying to a shared DB. The migration is additive and keeps old assessment/final-grade rows.

With the default `GRADING_AI_PROVIDER=disabled`, completed attempts are reconciled into `IN_REVIEW`; lecturers can claim, enter criteria manually and finalize. AI capability is correctly reported not ready.

## Enable a local compatible model

Start a local OpenAI-compatible chat-completions server on loopback, then set in the backend `.env` (do not commit secrets):

```dotenv
GRADING_AI_PROVIDER=openai-compatible
GRADING_AI_BASE_URL=http://127.0.0.1:11434/v1
GRADING_AI_MODEL=<model-id-returned-by-local-server>
GRADING_ALLOW_REMOTE_LLM=false
INCLUDE_SIGNALS_IN_GRADING=false
```

Restart backend. `GET /api/v1/grading/capabilities` must show `ai.ready=true`; it probes `/models`. The periodic reconciler queues auto-scoring for completed attempts when exam settings enable it. Poll `/submissions/{id}/ai-runs`; terminal errors are visible by safe code.

## Recovery and checks

Pending work is in `ai_grading_runs`; claims use `FOR UPDATE SKIP LOCKED` and expire after five minutes. Transient provider failures retry up to three claims with bounded backoff; invalid model JSON is terminal. Inspect status/error codes without querying transcript or audio columns. Audit chain rows are protected against update/delete by triggers. Never print `.env`, token headers, transcript, or audio URLs to logs.

Run focused tests: `mvnw.cmd -Dtest=GradingStatusTest,GradingScoreCalculatorTest,AuditHashChainTest,PiiRedactorTest test` (or `mvn ...` when Maven is installed). Run the complete suite with `mvnw.cmd test` where the repository wrapper is available; this repo currently has no wrapper checked in.
