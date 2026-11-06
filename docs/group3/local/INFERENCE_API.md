# Local Inference API

Base URL is loopback-only by default (`http://127.0.0.1:8000`). The normal deployment is the inference process inside the backend container/network namespace; Compose does not publish port 8000. Every route, including health, requires `X-Internal-Token`; there is no token query parameter. The internal token is separate from the student JWT and must not be logged.

OpenAPI contract is in `inference-service/openapi.json`; FastAPI docs UI is disabled. Status/health JSON includes actual model device/readiness/reason and local resource readings. Runtime returns HTTP 429 with `Retry-After` when the inference queue is full; model unavailable returns 503; invalid internal token returns 401.

## STT batch/final

`POST /v1/stt/transcribe` multipart: `audio` (WAV/FLAC/other decodable file), `language` (`vi` or `en`), `hotwords` (JSON string array), `initial_prompt` (optional bounded prompt), `mode=final`. Result includes `text_raw`, `text_normalized`, language, words, segment log probabilities/no-speech/compression ratio, confidence, model, latency, queue wait, warnings and request ID. Raw model text is retained even if a suspicious segment is excluded from the accepted hypothesis.

## STT WebSocket

`/v1/stt/stream?language=vi|en&session_id=...&turn_id=...` requires the internal token header. The first client text event is `{"type":"config","hotwords":[...],"initial_prompt":"...","mode":"streaming_only|final_only|dual"}`. Audio is binary mono signed PCM16 little-endian at exactly 16 kHz in 20-100 ms frames. Client `{"type":"commit"}` requests final decode of the current VAD segment; `{"type":"end"}` flushes VAD remainder and ends the turn. VAD creates segment boundaries; `end` from BE is the authoritative turn boundary. Server sends `ready`, stable-prefix `partial`, `committed`, `warning`, `error`, then `closed`.

`partial` text is an interim hypothesis. `stable_prefix` is the LocalAgreement common prefix. `committed` has segment `start_ms`/`end_ms`, word offsets within the segment, model confidence, warnings, and `needs_human_review`; the Java adapter makes timestamps absolute. Whisper is not a neural streaming decoder; partials are bounded-window repeated decodes with LocalAgreement.

## TTS

`POST /v1/tts/synthesize` JSON: `{text,language,voice,speed,format,normalize}`. Returns requested WAV/MP3/Opus/PCM audio, `X-Duration-Ms`, `X-Request-ID`. `POST /v1/tts/stream` returns NDJSON events `{type:"audio",seq,audio_base64,sample_rate,channels}` and terminal `{type:"done",duration_ms,latency_ms}`; `error` contains no text/audio detail. BE forwards audio chunks through the existing question-speech streaming route.

TTS normalization is performed once inside inference when `normalize=true`. Cache key uses normalized text, voice, speed, model and normalize flag. Voice is selected by language and fixed by runtime config for all exam participants.
