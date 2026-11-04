# STT Repository Survey

## Stack and current implementation

- Java 21, Spring Boot 3.3.4, Maven, Spring MVC/WebSocket, Spring Security/JWT, JPA, PostgreSQL. Tests use JUnit 5, Mockito, and Spring Boot Test.
- SQL schema is initialized by `src/main/resources/schema.sql`; numbered SQL files exist in `src/main/resources/db/migration` but Flyway/Liquibase is not configured as an automatic runner. Existing convention is manual SQL migration plus idempotent additions in `schema.sql` for new databases.
- `SpeechToTextProvider` is the live stream boundary. `GoogleCloudSpeechToTextProvider` currently provides gRPC streaming. The older `SpeechToTextProviderAdapter`/`OpenAiWhisperSttProviderAdapter` handles file transcription.
- `/ws/stt` authenticates student JWT and supports DEVICE_CHECK/EXAM; EXAM stop submits transcript through `InterviewService.submitAnswer`.
- Existing persisted transcript is `interaction_turn.transcript_text`; existing fluency table is `acoustic_fluency_metric`. No `stt_result` or `session_event` table existed before this S1 work.
- No existing ring buffer, batch finalizer, keyterm selector, normalization/reconciliation pipeline, confidence review policy, or cost tracker existed before this S1 work.

## Vendor source review

The three requested local files exist and were read. Their supported fields and remaining undocumented limits/serialization gaps are listed in [`00_VENDOR_NOTES.md`](00_VENDOR_NOTES.md). The realtime endpoint schema is sufficient for manual commit. Batch keyterm multipart serialization and some operational limits remain unresolved; implementation must not guess those fields/limits.

## Migration convention

V2 is a manually applied PostgreSQL SQL migration and is not executed by a migration framework. S1 adds an idempotent V3 script and matching idempotent `schema.sql` definitions for fresh databases. Existing databases must be upgraded by explicitly running V3.
