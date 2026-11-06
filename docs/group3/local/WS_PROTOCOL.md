# STT WebSocket Protocol

## Connection and Authentication

Connect to same-origin `/ws/stt` (Vite development proxy forwards WebSocket traffic to Spring). Use the logged-in student's JWT as `?access_token=<JWT>` because browser WebSocket APIs cannot set an Authorization header. Never print the token, URL query or complete handshake in logs. Spring validates the JWT at handshake and again for each message; expiration terminates the session.

## Start

Send one JSON text frame before any audio:

```json
{"type":"start","language":"vi-VN","purpose":"DEVICE_CHECK","sampleRate":16000,"encoding":"pcm_s16le","consent":true}
```

`language` is `vi-VN` or `en-US`; `purpose` is `DEVICE_CHECK` or `EXAM`; the sample format is mono PCM16 little-endian at 16,000 samples/second. EXAM also requires string UUID fields `attemptId` and `turnId`.

Consent must be explicitly checked before the browser sends any audio. Audio leaves the browser and is posted from Spring to the local inference-service over the private Docker network; it is not sent to a speech cloud. DEVICE_CHECK stores neither audio nor transcript after the connection. EXAM uses the configured local audio storage policy and existing interview transcript persistence.

## Audio Frames

After `start`, send binary WebSocket frames containing only raw signed 16-bit PCM samples, little endian, mono, 16 kHz. No WAV header or container is included. Each frame must be non-empty, have an even number of bytes and be no larger than `STT_MAX_FRAME_BYTES` (default 24,000 bytes, 750 ms of audio). Smaller regular frames such as 20-100 ms are recommended. Total session duration defaults to 60 seconds. DEVICE_CHECK sessions default to at most five starts per student per minute.

## Stop and Server Events

Send `{"type":"stop"}` to flush the final decode. Server text messages use these shapes:

```json
{"type":"partial","text":"Xin chào"}
{"type":"final","text":"Xin chào, tôi đang kiểm tra micro.","confidence":0.91}
{"type":"error","code":"NO_SPEECH","message":"No speech was recognized."}
{"type":"ended"}
```

Partial is provisional; final is emitted by the provider while decoding. A successful DEVICE_CHECK stop then returns `ended`. Empty DEVICE_CHECK may return `ended` without `final`; the FE must not mark the microphone as passed unless it saw a final event with non-whitespace text and observed microphone signal. Errors are terminal; close/release microphone and audio worklet and let the student retry after presenting the mapped reason.

## Error Codes

`START_REQUIRED`, `START_TIMEOUT`, `INVALID_MESSAGE`, `SESSION_ALREADY_STARTED`, `CONSENT_REQUIRED`, `UNSUPPORTED_LANGUAGE`, `INVALID_PURPOSE`, `INVALID_AUDIO_FORMAT`, `SESSION_TIMEOUT`, `RATE_LIMITED`, `PROVIDER_NOT_CONFIGURED`, `PROVIDER_UNAVAILABLE`, `PROVIDER_TIMEOUT`, `NO_SPEECH`, `EXAM_CONTEXT_REQUIRED`, `EXAM_ATTEMPT_EXPIRED`, `EXAM_ACCESS_DENIED`, `EXAM_SUBMISSION_REJECTED`, `EXAM_SUBMISSION_FAILED`, and `TOKEN_EXPIRED`.

For DEVICE_CHECK, expected recoverable actions: request microphone permission for device errors; verify mono PCM16LE/16 kHz and restart for `INVALID_AUDIO_FORMAT`; wait before retry for `RATE_LIMITED`; reload login for `TOKEN_EXPIRED`; retry shortly for provider availability/timeouts; tell the student to speak clearly and retry for `NO_SPEECH`. Do not retry a terminal socket in-place; open a new connection. `SESSION_TIMEOUT` requires an explicit new attempt.

## Lifecycle

Close the WebSocket and stop all microphone tracks on stop, navigation, device change, permission failure or unmount. The backend handles abrupt disconnect by aborting inference and discarding DEVICE_CHECK buffers. A single connection accepts only one `start`.
