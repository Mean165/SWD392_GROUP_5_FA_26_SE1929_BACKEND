# ElevenLabs Scribe Vendor Notes

Reviewed local references: `docs/vendor/elevenlabs/realtime_stt.md`, `keyterm_prompting.md`, and `batch_convert.md`.

## Realtime Contract Implemented

- WebSocket path: `/v1/speech-to-text/realtime`. Supported hosts in the supplied doc: global `api.elevenlabs.io`, US `api.us.elevenlabs.io`, EU `api.eu.residency.elevenlabs.io`, IN `api.in.residency.elevenlabs.io`, SG `api.sg.residency.elevenlabs.io`.
- The backend uses `xi-api-key` in the handshake header. The API key is read from `ELEVENLABS_API_KEY`, is never included in the browser protocol, and is not logged. Token query auth exists for client-side sockets but is not used.
- Model `scribe_v2_realtime`, format `pcm_16000`, sample rate 16000, language code `vi`/`en`, timestamps and language detection enabled, 5000 ms keepalive, repeated `keyterms` query fields, and explicit `enable_logging` are used.
- Commit strategy is **manual only**. Each client frame is mono PCM16 little-endian. The adapter holds one frame: older frames are sent as `input_audio_chunk` with `commit=false`, and on client `stop` the final held frame is sent once with `commit=true`. Vendor VAD is not enabled. The adapter waits for a committed transcript before closing.
- `input_audio_chunk` includes `message_type`, Base64 `audio_base_64`, `commit`, and `sample_rate`. `previous_text` is not sent.
- Server events handled: `session_started`, `partial_transcript`, `committed_transcript`, `committed_transcript_with_timestamps`, and typed vendor errors. Partial/final text is forwarded through the existing AiViva WebSocket protocol. Word logprobs are converted to probabilities when present.
- Realtime keyterms are limited to 50 entries, 20 code points each. Batch-only forbidden-character rules are not applied to realtime.
- Vendor docs set `keepalive_interval_ms` to 500-10000. They do not give maximum chunk bytes, maximum connection duration, numeric concurrent-stream/rate quotas, or WebSocket retry-after semantics. Application defaults (24,000 bytes/frame, 60-second DEVICE_CHECK, configured exam duration, 5 DEVICE_CHECK starts/minute) are local caps, not vendor limits.

## Logging and Data Residency

- `enable_logging=false` requests Zero Retention; the supplied docs say Enterprise entitlement is required. Application default is false. A 4xx response is returned as an auth/provider error and is never retried with logging enabled.
- Set `ELEVENLABS_ENABLE_LOGGING=true` only after the organization explicitly approves vendor retention. In either mode, AiViva sends audio PCM, language, model/options, and realtime keyterms when provided. Batch sends audio and documented request options. It does not send user identity, exam identifiers, or transcript context to ElevenLabs.
- The server sends data only to the region selected by `ELEVENLABS_REGION`: `global` (default), `us`, `eu`, `in`, or `sg`. Confirm contractual residency and account entitlement before production.

## Keyterms

- Realtime: maximum 50 terms, 20 characters each; repeated query parameters are documented.
- Batch: maximum 1000 terms, 50 characters each, at most 5 words per term. The docs report a 20% keyterm surcharge and a 20-second minimum billable duration above 100 terms.
- Batch rejects `<`, `>`, `{`, `}`, `[`, `]`, and backslash. The selector applies this character rule only for batch terms, and performs NFC normalization, whitespace collapse, source ordering, and case-insensitive dedupe.
- Batch docs describe `keyterms` as a multipart list but give no raw multipart serialization example. The batch adapter omits that field and records `keytermsUsed=[]`; do not guess whether the vendor expects repeated parts or a JSON-encoded list.

## Batch Contract Implemented

- `POST /v1/speech-to-text`, same region host and `xi-api-key` header.
- Multipart fields sent: `file` (mono PCM16 LE 16 kHz), `model_id=scribe_v2`, `file_format=pcm_s16le_16`, `language_code=vi|en`, `timestamps_granularity=word`, `diarize=false`, `tag_audio_events=false`, `use_multi_channel=false`, and `enable_logging`.
- Response fields consumed: `text`, `language_code`, `words` including optional `start/end/logprob/type/speaker_id`, optional `transcription_id`, optional `audio_duration`. Word logprobs are exposed as estimated word confidence; `language_probability` is not misused as transcript confidence.
- The local copy says upload size `<5 GB`; current product documentation elsewhere has stated a different limit. Local answer-duration caps are much lower; the implementation does not use either vendor size limit to raise them.

## Remaining Questions / Limits

1. Confirm the deployed account's Scribe model entitlement for Vietnamese (`vi`) and English (`en`) with a live contract test. Local docs specify ISO-639-1/-3 semantics but do not list this model's language catalog.
2. Confirm the account is entitled to `enable_logging=false` Zero Retention. Do not silently fall back to logging.
3. Obtain the exact multipart encoding for batch `keyterms` before enabling them in the batch adapter.
4. Vendor max chunk size, max WebSocket lifetime, concurrency/rate limits, and retry-after semantics are not specified in the supplied docs.
5. The production `/ws/stt` handler currently uses the realtime provider and existing exam submission flow directly. `STTTurnSession` dual/batch reconciliation, new `stt_result` persistence, keyterm context selection, and fluency writes are not yet wired into that handler.
6. No original exam audio retention was added. If exam recordings are later required, define a separate consent/retention policy and reuse the existing storage configuration only after its current behavior is verified.
