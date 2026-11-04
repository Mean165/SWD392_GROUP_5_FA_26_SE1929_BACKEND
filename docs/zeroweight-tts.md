# ZeroWeight TTS Provider

## Select and configure

OpenAI remains the default. Select the new provider with `TTS_PROVIDER=zeroweight`; `TTS_PROVIDER=mock` selects a deterministic WAV tone for development/tests.

```properties
TTS_PROVIDER=zeroweight
ZEROWEIGHT_API_KEY=your-server-side-api-key
ZEROWEIGHT_API_URL=https://api.zeroweight.ai
ZEROWEIGHT_VOICE_ID=ngocanh
ZEROWEIGHT_SPEED=1.0
ZEROWEIGHT_NORMALIZE_TEXT=true
TTS_INTERNAL_NORMALIZATION_ENABLED=false
TTS_CACHE_MAX_ENTRIES=512
ZEROWEIGHT_TIMEOUT_MILLIS=20000
ZEROWEIGHT_MAX_AUDIO_BYTES=20971520
ZEROWEIGHT_CREDIT_BUDGET=0
ZEROWEIGHT_CREDIT_WARNING_REMAINING=1000
```

The API key is read by the backend only and is sent only in `X-ZW-Api-Key` on the session-creation request. The returned one-use URL is opened immediately; the API key is never added to that URL or sent to the frontend. Set credentials in a secret manager or an ignored local `.env`; never commit a real key. ZeroWeight requires a paid API plan and charges by socket-open duration.

ZeroWeight's session URL resolves the voice, PCM format and sample rate for the connection. Speed is sent in a `config` frame before the utterance. Each connection uses the configured voice and speed consistently. The backend requests mono PCM16 at 24 kHz and packages completed synthesis as WAV for the existing audio endpoints; the WAV content type is detected and returned as `audio/wav`.

## Flow and cache

The adapter posts `voice_id`, `format`, `sample_rate`, `normalize_text` and sampling settings to `/api/v1/realtime/stream/session`. It immediately opens the returned `wss://` URL, sends `config`, `synthesize`, then `end`, strips and validates the provider's 12-byte frame header, collects PCM, and closes the socket after `context_done`.

The existing service has a bounded in-memory LRU cache keyed by SHA-256 over text, language, requested format, provider, voice, speed and normalization mode. Concurrent requests for the same key share one synthesis. Main interview questions are prewarmed asynchronously when saved; dynamic follow-ups can use the live PCM endpoint. Device-check samples are cached on first request. Cache hits avoid opening another billed socket.

Provider text normalization defaults on. This repository does not currently have the internal TTS normalization step described in the broader architecture notes. If that step is later enabled, set `TTS_INTERNAL_NORMALIZATION_ENABLED=true`; the ZeroWeight adapter disables provider normalization and currently fails closed until an internal normalizer is actually wired in. It never runs both normalization paths.

## Credits and fallback

The documented billing unit is one credit per second of open WebSocket time, rounded up, with a one-credit minimum per session. The provider does not document a balance/usage API in this integration, so the backend logs an estimated per-session usage based on socket lifetime. If `ZEROWEIGHT_CREDIT_BUDGET` is set, it warns once when the process-local estimate reaches `ZEROWEIGHT_CREDIT_WARNING_REMAINING`. This estimate resets when the backend restarts and is not the account's authoritative balance. No provider-credit API query or automatic paid-plan fallback is configured.

A cache hit continues to work when the provider is down. A cache miss that fails returns a sanitized service error; interview question text remains available to the existing frontend for text-only continuation. HTTP `401`, `402`, `422`, `429`, `503` and WebSocket close codes are mapped to provider error codes. A short-lived token error is retried once with a fresh session; rate limits use bounded backoff. Other errors are not retried indefinitely.

## Existing-app limitations

The current interview code has no explicit `LISTENING` state event or persistent TTS conversation object. The implementation prewarms a main question after its turn is saved and opens/closes one ZeroWeight connection per uncached utterance; it does not hold a billed idle socket open. The authenticated `/api/interviews/turns/{turnId}/speech/stream` route streams raw PCM for `FOLLOW_UP` turns with encoding/sample-rate/channel headers. Existing `/speech` REST routes return a completed MP3 or WAV file for playback/cache. A cached-audio hit avoids opening ZeroWeight; on a cache miss with provider failure the interview can continue as text, but no alternate-provider fallback is configured.

The current catalogue voice `ngocanh` is used as configured. The service has no language field in the ZeroWeight session request; validate English pronunciation with the account's chosen voice before enabling it for English exam questions.

## Smoke test

The script opens one socket for several utterances and reports time to first binary audio frame plus an approximate socket credit count. It does not write the key, URL, text, or audio to disk.

```powershell
python -m pip install -r scripts/requirements-tts-smoke.txt
$env:ZEROWEIGHT_API_KEY = "<set locally>"
python scripts/tts_smoke_test.py
```
