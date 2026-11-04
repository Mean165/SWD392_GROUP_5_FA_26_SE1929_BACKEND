# STT Plan and Decisions

## Commit strategy

Choose **manual commit per answer turn**. Send realtime audio chunks without commit until existing endpointing decides the turn ended, then mark the held final chunk `commit=true`. Do not configure vendor VAD commits on this connection; running two endpointing authorities can split/commit a turn twice and make reconciliation/replay ambiguous.

## Milestones

- S1: Java records/DTOs; mode and cost configuration; consent-gated `STTTurnSession`; mock realtime/batch provider; deterministic keyterms, normalizer, reconciler, fluency extraction, bounded PCM replay; persistence entities and V3 SQL migration. Independent of vendor credentials.
- S2: ElevenLabs realtime adapter using documented query, header auth, audio chunks and manual commit. Fake HTTP/WebSocket contract tests, consent/logging setting and endpoint/cost instrumentation.
- S3: ElevenLabs batch REST finalizer using `file_format=pcm_s16le_16`; reconcile result with streaming; keyterm multipart field remains gated until its wire serialization is documented.
- S4: retry/circuit-breaker, reconnect/replay integration, confidence/repeat decisions, and Micrometer metrics if a registry/exporter is configured.
- S5: benchmark `--dry-run`, opt-in live tests, runbook, API/WS contract and known limitations.

## Current status

S1 implementation is complete in Java/Spring: DTO/domain types, deterministic mock realtime/batch provider, keyterm selection, normalization, reconciliation, fluency extraction, bounded PCM ring buffer, cost estimates, persistence entities and V3 SQL migration are in place. No vendor key is required for S1.

S2/S3 adapters are implemented with the supplied ElevenLabs contracts and fake WebSocket/HTTP tests. Realtime uses manual commit, regional hosts, server-side key auth, and no transcript/audio logging. Batch keyterm multipart encoding remains intentionally unsent rather than guessed. The production `/ws/stt` handler still needs to be wired to `STTTurnSession` before `STT_MODE=dual` or batch reconciliation affects live exam sessions.

## Privacy decisions

- Vendor requests are server-to-server. Only PCM audio, selected language, model/options, and (when enabled) selected keyterms are sent. `ELEVENLABS_API_KEY` stays in the backend environment and is never sent to the browser or logged.
- `enable_logging=false` is the privacy-preserving default requested by deployment configuration. Vendor docs say Zero Retention requires Enterprise entitlement. A rejection must surface as a provider error; the server must never retry with logging enabled. Deployment must confirm entitlement and user notice/consent before real audio is sent.
- DEVICE_CHECK is transient and must not persist transcript or audio. EXAM transcript persistence uses the existing `interaction_turn.transcript_text` and the new `stt_result` metadata; raw audio is not added to persistence.
