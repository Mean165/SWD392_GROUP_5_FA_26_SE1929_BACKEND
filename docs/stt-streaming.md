# Live Speech-to-Text

## Provider

The existing multipart STT endpoint and the existing interview audio fallback still use the OpenAI file-transcription adapter. Live microphone audio uses the `SpeechToTextProvider` boundary. `STT_PROVIDER=google` selects Google Cloud Speech-to-Text V2 with Chirp 3; `STT_PROVIDER=elevenlabs` selects ElevenLabs Scribe v2 realtime and its documented batch finalizer. `STT_PROVIDER=mock` selects deterministic local fixtures. Google and ElevenLabs return interim results from real streaming connections.

The Google adapter sends audio to the configured Google Cloud project and location. ElevenLabs sends audio server-to-server to the configured regional host. No audio, transcript, API key, or vendor response body is written to server logs.

## Setup

Configure Application Default Credentials for the server identity, for example with `GOOGLE_APPLICATION_CREDENTIALS` in a local environment or workload identity in production. Do not commit a service-account key.

```properties
STT_PROVIDER=google
STT_ALLOWED_ORIGINS=http://localhost:5173
GOOGLE_CLOUD_PROJECT=your-project-id
GOOGLE_CLOUD_SPEECH_LOCATION=eu
GOOGLE_CLOUD_SPEECH_MODEL=chirp_3
STT_MAX_SESSION_DURATION_SECONDS=60
STT_MAX_FRAME_BYTES=24000
STT_FINISH_TIMEOUT_SECONDS=10
DEVICE_CHECK_STT_SESSIONS_PER_MINUTE=5
STT_PROVIDER=elevenlabs
ELEVENLABS_API_KEY=your-secret-key
ELEVENLABS_REGION=global
ELEVENLABS_ENABLE_LOGGING=false
ELEVENLABS_CONNECT_TIMEOUT_SECONDS=10
STT_MODE=realtime_only
STT_BATCH_TIMEOUT_SECONDS=30
```

Put secrets in a local, ignored `.env` file or deployment secret manager. Never commit the API key. Docker Compose defaults to `STT_PROVIDER=elevenlabs` and permits `http://localhost:5173` for local frontend development. Use `STT_PROVIDER=mock` to test without credentials. The health check verifies local provider configuration; it does not guarantee the vendor is reachable at that instant. Set `STT_ALLOWED_ORIGINS` to the exact comma-separated browser origins for the deployment. Google, when explicitly selected, uses Application Default Credentials and needs no ElevenLabs key.

ElevenLabs defaults to global `api.elevenlabs.io`; select `us`, `eu`, `in`, or `sg` with `ELEVENLABS_REGION` to use the documented regional host. `ELEVENLABS_ENABLE_LOGGING=false` requests Zero Retention, which the supplied vendor documentation says requires Enterprise entitlement. Confirm entitlement before sending real student audio. A rejection is surfaced as an error; the backend never retries with vendor logging turned on. Set it to `true` only after the organization's privacy owner approves that processing choice.

## WebSocket Protocol

Open `wss://<host>/ws/stt` with a student login token. Send `Authorization: Bearer <accessToken>` when the client can set headers. Browser WebSocket clients can use `?access_token=<url-encoded accessToken>`. Deploy over TLS and redact `access_token` from reverse-proxy/access logs.

The first frame must be a text message:

```json
{"type":"start","language":"vi-VN","purpose":"DEVICE_CHECK","sampleRate":16000,"encoding":"pcm_s16le","consent":true}
```

For EXAM, include the IDs needed by the existing grading flow:

```json
{"type":"start","language":"en-US","purpose":"EXAM","sampleRate":16000,"encoding":"pcm_s16le","consent":true,"attemptId":"<attempt UUID>","turnId":"<current turn UUID>"}
```

After `start`, send binary frames containing mono, little-endian, signed 16-bit PCM at 16 kHz. Each frame must be non-empty, have an even byte count, and be no larger than `STT_MAX_FRAME_BYTES`. Example outgoing server messages:

```json
{"type":"partial","text":"Xin chào"}
{"type":"final","text":"Xin chào.","confidence":0.0}
{"type":"error","code":"INVALID_AUDIO_FORMAT","message":"Audio frames must be non-empty PCM16 and within the frame size limit."}
{"type":"ended"}
```

Each `final` is one finalized recognition segment. Append final segments to the transcript and replace the current partial text when a later partial arrives. Send `{"type":"stop"}` to finish. EXAM stop submits the aggregated final transcript to `InterviewService.submitAnswer`, which stores it against `attemptId` and `turnId` and runs the existing AI grading flow. DEVICE_CHECK only holds recognized text in session memory and never calls the interview service or any persistence service.

The authenticated health endpoint is `GET /api/student/device-check/stt/health`:

```json
{"ready":true,"provider":"google-cloud-speech-v2","languages":["vi-VN","en-US"],"sampleRate":16000,"encoding":"pcm_s16le","maxDurationSeconds":60}
```

## Errors

| Code | Meaning |
| --- | --- |
| `CONSENT_REQUIRED` | `start` must explicitly contain `"consent":true` before any audio can be forwarded. |
| `STT_NOT_READY` | The selected provider is not configured; check health and provider credentials. |
| `PROVIDER_NOT_CONFIGURED` | ElevenLabs was selected but `ELEVENLABS_API_KEY` is empty. |
| `TOKEN_EXPIRED` | Login token became invalid during an active connection. A token invalid at upgrade gets HTTP 401. |
| `UNSUPPORTED_LANGUAGE` | Language is not `vi-VN` or `en-US`. |
| `INVALID_PURPOSE` | Purpose is not `DEVICE_CHECK` or `EXAM`. |
| `EXAM_CONTEXT_REQUIRED` | EXAM start omitted `attemptId` or `turnId`. |
| `INVALID_AUDIO_FORMAT` | Sample rate, encoding, frame size, or PCM sample boundary is invalid. |
| `RATE_LIMITED` | Student exceeded the device-check start quota or already has a live device-check session. |
| `START_REQUIRED` | A session did not start within 10 seconds or audio arrived before `start`. |
| `SESSION_TIMEOUT` | Session wall time or audio sample duration exceeded its limit. |
| `STT_NOT_READY` | Google project, credentials, or local provider setup is unavailable. |
| `PROVIDER_TIMEOUT` | Provider did not finish within the configured drain timeout. |
| `PROVIDER_UNAVAILABLE` | Provider stream failed or rejected audio. |
| `PROVIDER_AUTH_ERROR` | Vendor rejected the backend credential or logging entitlement. |
| `PROVIDER_QUOTA_EXCEEDED` | Vendor account has no remaining transcription quota. |
| `PROVIDER_RATE_LIMITED` | Vendor throttled the request; no retry policy is configured yet. |
| `PROVIDER_PROTOCOL_ERROR` | Vendor returned an unsupported or invalid response. |
| `PROVIDER_TRANSCRIPTION_ERROR` | ElevenLabs transcriber failed while processing the audio. |
| `PROVIDER_ERROR` | ElevenLabs returned a generic realtime service error. |
| `PROVIDER_TERMS_NOT_ACCEPTED` | ElevenLabs account has not accepted required service terms. |
| `INPUT_TOO_LARGE` | Batch upload exceeded the vendor size limit. |
| `NO_SPEECH` | EXAM ended without a recognized transcript. |
| `EXAM_SUBMISSION_FAILED` | Transcript could not enter the existing interview grading flow. |
| `EXAM_ATTEMPT_EXPIRED` | Exam attempt is no longer active. |
| `EXAM_ACCESS_DENIED` | Attempt or turn does not belong to the logged-in student. |
| `EXAM_SUBMISSION_REJECTED` | Existing interview rules rejected the transcript. |

## Limits and Data Handling

DEVICE_CHECK is limited to 60 seconds by default, 5 starts per student per minute, and one concurrent device-check session per student. A session must start within 10 seconds. EXAM uses `INTERVIEW_ANSWER_TIME_LIMIT_SECONDS` (180 seconds by default), subject to Google's five-minute stream limit. WebSocket payloads are capped at 8 KiB for text and 32 KiB for binary at the container layer; the STT frame limit defaults to 24,000 bytes.

The selected provider receives PCM audio and the chosen language. It does not record streaming audio or log audio/transcript content. DEVICE_CHECK transcript exists only in process memory until the connection closes. EXAM final transcript is persisted by the existing interview grading flow. Streaming EXAM does not save original audio; the current interview audio-storage path is used only when a request includes an audio segment. Retaining original EXAM audio should remain a separate explicit policy decision.

With ElevenLabs, the provider connection uses `commit_strategy=manual`: it buffers one PCM frame so only the last frame is sent with `commit=true` after client `stop`; all preceding frames use `commit=false`. It does not enable vendor VAD on that connection. `STTTurnSession` supports bounded in-memory batch replay and reconciliation, but the current `/ws/stt` handler still uses realtime-only flow; `STT_MODE=dual` is not wired into the live handler yet.

The batch adapter sends `model_id=scribe_v2`, `file_format=pcm_s16le_16`, `language_code`, `timestamps_granularity=word`, `diarize=false`, `tag_audio_events=false`, `use_multi_channel=false`, and `enable_logging`. Batch `keyterms` are omitted because the supplied docs do not define multipart list serialization.

The current device-check page is not part of this backend repository. Its microphone step should use this WebSocket and its speaker step should continue to use the existing TTS endpoint; the previous one-shot "speak test" UI needs removal in the frontend repository.

## Latency Smoke Test

The benchmark uses existing Python `websockets` dependency from `scripts/requirements-tts-smoke.txt` and a raw mono PCM16 little-endian 16 kHz fixture. It opens a non-persisting `DEVICE_CHECK` session, streams the sample at real-time pacing, and reports first partial/final latency without printing transcript content:

```powershell
python -m pip install -r scripts/requirements-tts-smoke.txt
$env:STT_SMOKE_ACCESS_TOKEN = "<student access token>"
$env:STT_SMOKE_WS_URL = "ws://localhost:8080/ws/stt"
python scripts/stt_smoke_test.py --language vi-VN --sample "sample-1=path/to/your-sample.pcm"
```

No API key is needed when the backend runs with `STT_PROVIDER=mock`. For a live vendor test, set `STT_PROVIDER=elevenlabs` and the backend's `ELEVENLABS_API_KEY`; the browser/script still receives only the student login token. Each smoke run is subject to the normal DEVICE_CHECK duration and rate limits.
